package com.assignment.smarttodo.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
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

@SpringBootTest
@AutoConfigureMockMvc
class TaskFilteringIntegrationTest {

  @TempDir static Path tempDir;

  @DynamicPropertySource
  static void registerStorage(DynamicPropertyRegistry registry) {
    registry.add("task.storage.path", () -> storagePath().toString());
  }

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void clearStoredTasks() throws Exception {
    Files.deleteIfExists(storagePath());
  }

  @Test
  void filtersIndividuallyAndCombinesAllFiltersWithAndSemantics() throws Exception {
    createTasks();
    byte[] persistedBeforeQueries = Files.readAllBytes(storagePath());

    mockMvc
        .perform(get("/api/tasks").param("status", "TODO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].title").value("first-high"))
        .andExpect(jsonPath("$[1].title").value("second-high"))
        .andExpect(jsonPath("$[2].title").value("medium-todo"));

    mockMvc
        .perform(get("/api/tasks").param("priority", "HIGH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].title").value("first-high"))
        .andExpect(jsonPath("$[1].title").value("second-high"))
        .andExpect(jsonPath("$[2].title").value("high-progress"));

    mockMvc
        .perform(get("/api/tasks").param("tag", "work"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].title").value("first-high"))
        .andExpect(jsonPath("$[1].title").value("second-high"))
        .andExpect(jsonPath("$[2].title").value("medium-todo"));

    mockMvc
        .perform(
            get("/api/tasks")
                .param("status", "TODO")
                .param("priority", "HIGH")
                .param("tag", "work"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].title").value("first-high"))
        .andExpect(jsonPath("$[1].title").value("second-high"));

    assertArrayEquals(persistedBeforeQueries, Files.readAllBytes(storagePath()));
  }

  @Test
  void normalizesTrimsAndDeduplicatesTagsAndMatchesNormalizedTagFilter() throws Exception {
    createTask("first-high", "HIGH", "TODO", "[\" Work \",\"work\",\" URGENT \",\"urgent\"]");
    createTask("second-high", "HIGH", "TODO", "[\"work\"]");
    createTask("medium-todo", "MEDIUM", "TODO", "[\"work\"]");
    createTask("low-progress", "LOW", "IN_PROGRESS", "[\"ops\"]");
    createTask("high-progress", "HIGH", "IN_PROGRESS", "[\"ops\"]");

    byte[] persistedBeforeQueries = Files.readAllBytes(storagePath());

    mockMvc
        .perform(get("/api/tasks").param("tag", "  WoRk  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].title").value("first-high"));

    mockMvc
        .perform(get("/api/tasks").param("tag", "urgent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].tags").isArray())
        .andExpect(jsonPath("$[0].tags.length()").value(2))
        .andExpect(jsonPath("$[0].tags[0]").value("work"))
        .andExpect(jsonPath("$[0].tags[1]").value("urgent"));

    assertArrayEquals(persistedBeforeQueries, Files.readAllBytes(storagePath()));
  }

  @Test
  void sortsHighMediumLowAndPreservesInsertionOrderForPriorityTies() throws Exception {
    createTasks();
    byte[] persistedBeforeQueries = Files.readAllBytes(storagePath());

    mockMvc
        .perform(get("/api/tasks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].title").value("first-high"))
        .andExpect(jsonPath("$[1].title").value("second-high"))
        .andExpect(jsonPath("$[2].title").value("high-progress"))
        .andExpect(jsonPath("$[3].title").value("medium-todo"))
        .andExpect(jsonPath("$[4].title").value("low-progress"));

    assertArrayEquals(persistedBeforeQueries, Files.readAllBytes(storagePath()));
  }

  @Test
  void returnsEmptyResultsAndInvalidFiltersWithoutChangingPersistedState() throws Exception {
    createTasks();
    byte[] persistedBeforeQueries = Files.readAllBytes(storagePath());

    mockMvc
        .perform(
            get("/api/tasks")
                .param("status", "TODO")
                .param("priority", "LOW")
                .param("tag", "missing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(get("/api/tasks").param("status", "INVALID"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/tasks").param("priority", "INVALID"))
        .andExpect(status().isBadRequest());

    assertArrayEquals(persistedBeforeQueries, Files.readAllBytes(storagePath()));
  }

  private void createTasks() throws Exception {
    createTask("first-high", "HIGH", "TODO", "[\"work\",\"urgent\"]");
    createTask("second-high", "HIGH", "TODO", "[\"work\"]");
    createTask("medium-todo", "MEDIUM", "TODO", "[\"work\"]");
    createTask("low-progress", "LOW", "IN_PROGRESS", "[\"ops\"]");
    createTask("high-progress", "HIGH", "IN_PROGRESS", "[\"ops\"]");
  }

  private void createTask(String title, String priority, String taskStatus, String tags)
      throws Exception {
    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\""
                        + title
                        + "\",\"priority\":\""
                        + priority
                        + "\",\"status\":\""
                        + taskStatus
                        + "\",\"tags\":"
                        + tags
                        + "}"))
        .andExpect(status().isCreated());
  }

  private static Path storagePath() {
    return tempDir.resolve("tasks.json");
  }
}
