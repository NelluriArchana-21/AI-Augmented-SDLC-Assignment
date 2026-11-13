package com.assignment.smarttodo.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {

  @TempDir static Path tempDir;

  @DynamicPropertySource
  static void registerStorage(DynamicPropertyRegistry registry) {
    registry.add("task.storage.path", () -> storagePath().toString());
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void clearStoredTasks() throws IOException {
    Files.deleteIfExists(storagePath());
  }

  @Test
  void postGetByIdPutAndDeleteReturnContractStatusCodes() throws Exception {
    String id = createTask(validTask("Plan sprint", "Prepare backlog", "TODO"));

    mockMvc
        .perform(get("/api/tasks/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.title").value("Plan sprint"));

    mockMvc
        .perform(
            put("/api/tasks/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskWithId(id, "Plan sprint", "Updated", "IN_PROGRESS")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.description").value("Updated"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    mockMvc.perform(delete("/api/tasks/" + id)).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/tasks/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void rejectsClientSuppliedIdDuringCreation() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\":\""
                        + UUID.randomUUID()
                        + "\",\"title\":\"Bad\",\"priority\":\"HIGH\",\"status\":\"TODO\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCannotChangeTheTaskId() throws Exception {
    String id = createTask(validTask("Alpha", "Original", "TODO"));
    byte[] persistedBefore = Files.readAllBytes(storagePath());

    mockMvc
        .perform(
            put("/api/tasks/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    taskWithId(UUID.randomUUID().toString(), "Changed", "Changed", "IN_PROGRESS")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/tasks/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    org.junit.jupiter.api.Assertions.assertArrayEquals(
        persistedBefore, Files.readAllBytes(storagePath()));
  }

  @Test
  void sameStatusPutReturnsUnchangedCurrentRepresentationAndDoesNotPersist() throws Exception {
    String id = createTask(validTask("Original title", "Original description", "TODO"));
    JsonNode original =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/tasks/" + id))
                .andReturn()
                .getResponse()
                .getContentAsString());
    byte[] persistedBefore = Files.readAllBytes(storagePath());

    MvcResult updateResult =
        mockMvc
            .perform(
                put("/api/tasks/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(taskWithId(id, "Changed title", "Changed description", "TODO")))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode unchanged = objectMapper.readTree(updateResult.getResponse().getContentAsString());
    org.junit.jupiter.api.Assertions.assertEquals(original, unchanged);
    org.junit.jupiter.api.Assertions.assertArrayEquals(
        persistedBefore, Files.readAllBytes(storagePath()));
  }

  @Test
  void missingTaskGetPutAndDeleteReturnNotFound() throws Exception {
    String missingId = UUID.randomUUID().toString();

    mockMvc.perform(get("/api/tasks/" + missingId)).andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/api/tasks/" + missingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskWithId(missingId, "Missing", null, "TODO")))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/tasks/" + missingId)).andExpect(status().isNotFound());
  }

  @Test
  void rejectedTransitionReturnsBadRequestAndLeavesStoredStateUnchanged() throws Exception {
    String id = createTask(validTask("Original", "Description", "TODO"));
    byte[] persistedBefore = Files.readAllBytes(storagePath());

    mockMvc
        .perform(
            put("/api/tasks/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskWithId(id, "Altered", "Altered", "COMPLETED")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/tasks/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Original"))
        .andExpect(jsonPath("$.description").value("Description"))
        .andExpect(jsonPath("$.status").value("TODO"));
    org.junit.jupiter.api.Assertions.assertArrayEquals(
        persistedBefore, Files.readAllBytes(storagePath()));
  }

  private String createTask(String requestBody) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String validTask(String title, String description, String taskStatus) {
    String descriptionJson = description == null ? "" : ",\"description\":\"" + description + "\"";
    return "{\"title\":\""
        + title
        + "\""
        + descriptionJson
        + ",\"priority\":\"HIGH\",\"status\":\""
        + taskStatus
        + "\",\"tags\":[\"work\"]}";
  }

  private String taskWithId(String id, String title, String description, String taskStatus) {
    String descriptionJson = description == null ? "" : ",\"description\":\"" + description + "\"";
    return "{\"id\":\""
        + id
        + "\",\"title\":\""
        + title
        + "\""
        + descriptionJson
        + ",\"priority\":\"HIGH\",\"status\":\""
        + taskStatus
        + "\",\"tags\":[\"work\"]}";
  }

  private static Path storagePath() {
    return tempDir.resolve("tasks.json");
  }
}
