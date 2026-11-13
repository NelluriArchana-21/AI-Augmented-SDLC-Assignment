package com.assignment.smarttodo.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.smarttodo.SmartTodoApplication;
import com.assignment.smarttodo.model.Priority;
import com.assignment.smarttodo.model.Status;
import com.assignment.smarttodo.model.Task;
import com.assignment.smarttodo.repository.TaskRepository;
import com.assignment.smarttodo.service.TaskService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class TaskPersistenceIntegrationTest {

  @TempDir Path tempDir;

  @Test
  void recoversTaskDataAndOrderingAfterApplicationContextRecreation() throws Exception {
    Path storage = tempDir.resolve("tasks.json");
    Task firstHigh = task("First high", Priority.HIGH, Status.TODO);
    Task low = task("Low", Priority.LOW, Status.IN_PROGRESS);
    Task secondHigh = task("Second high", Priority.HIGH, Status.COMPLETED);
    Task createdFirst;
    Task createdLow;
    Task createdSecondHigh;

    try (ConfigurableApplicationContext context = startApplication(storage)) {
      TaskService taskService = context.getBean(TaskService.class);
      createdFirst = taskService.create(firstHigh);
      createdLow = taskService.create(low);
      createdSecondHigh = taskService.create(secondHigh);

      assertTrue(Files.exists(storage));
    }

    try (ConfigurableApplicationContext context = startApplication(storage)) {
      List<Task> persistedOrder = context.getBean(TaskRepository.class).findAll();

      assertEquals(
          List.of(createdFirst.getId(), createdLow.getId(), createdSecondHigh.getId()),
          persistedOrder.stream().map(Task::getId).toList());
      assertTaskEquals(createdFirst, persistedOrder.get(0));
      assertTaskEquals(createdLow, persistedOrder.get(1));
      assertTaskEquals(createdSecondHigh, persistedOrder.get(2));
      assertDoesNotThrow(() -> UUID.fromString(persistedOrder.get(0).getId()));

      List<Task> sorted = context.getBean(TaskService.class).list(null, null, null);
      assertEquals(
          List.of(createdFirst.getId(), createdSecondHigh.getId(), createdLow.getId()),
          sorted.stream().map(Task::getId).toList());
    }
  }

  @Test
  void missingStorageStartsAsAnEmptyCollection() {
    Path storage = tempDir.resolve("missing.json");

    try (ConfigurableApplicationContext context = startApplication(storage)) {
      assertTrue(context.getBean(TaskService.class).list(null, null, null).isEmpty());
      assertFalse(Files.exists(storage));
    }
  }

  @Test
  void malformedStorageIsSurfacedAndPreservedAfterApplicationContextCreation() throws Exception {
    Path storage = tempDir.resolve("malformed.json");
    byte[] invalidContent = "{ not a task collection".getBytes();
    Files.write(storage, invalidContent);

    try (ConfigurableApplicationContext context = startApplication(storage)) {
      assertThrows(
          IllegalStateException.class,
          () -> context.getBean(TaskService.class).list(null, null, null));
      assertArrayEquals(invalidContent, Files.readAllBytes(storage));
    }
  }

  private ConfigurableApplicationContext startApplication(Path storage) {
    return new SpringApplicationBuilder(SmartTodoApplication.class)
        .web(WebApplicationType.NONE)
        .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
        .run("--task.storage.path=" + storage);
  }

  private Task task(String title, Priority priority, Status status) {
    return new Task(null, title, "Description for " + title, priority, status, List.of("work"));
  }

  private void assertTaskEquals(Task expected, Task actual) {
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getTitle(), actual.getTitle());
    assertEquals(expected.getDescription(), actual.getDescription());
    assertEquals(expected.getPriority(), actual.getPriority());
    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(expected.getTags(), actual.getTags());
  }
}
