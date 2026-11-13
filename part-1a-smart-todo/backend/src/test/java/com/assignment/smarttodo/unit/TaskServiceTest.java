package com.assignment.smarttodo.unit;

import static org.junit.jupiter.api.Assertions.*;

import com.assignment.smarttodo.model.Priority;
import com.assignment.smarttodo.model.Status;
import com.assignment.smarttodo.model.Task;
import com.assignment.smarttodo.repository.JsonTaskRepository;
import com.assignment.smarttodo.service.TaskService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskServiceTest {

  @TempDir Path tempDir;

  @Test
  void rejectsBlankAndOverlongTitles() {
    TaskService service = service("title-validation.json");

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(task(" \t ", null, Priority.HIGH, Status.TODO, null, List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                task("x".repeat(121), null, Priority.HIGH, Status.TODO, null, List.of())));
    assertEquals(
        "x".repeat(120),
        service
            .create(task("x".repeat(120), null, Priority.HIGH, Status.TODO, null, List.of()))
            .getTitle());
  }

  @Test
  void acceptsOmittedEmptyAndValidDescriptions() {
    TaskService service = service("descriptions.json");

    Task omitted =
        service.create(task("Omitted", null, Priority.HIGH, Status.TODO, null, List.of()));
    Task empty = service.create(task("Empty", "", Priority.MEDIUM, Status.TODO, null, List.of()));
    Task provided =
        service.create(
            task("Provided", "A useful description", Priority.LOW, Status.TODO, null, List.of()));

    assertNull(omitted.getDescription());
    assertTrue(empty.getDescription() == null || empty.getDescription().isEmpty());
    assertEquals("A useful description", provided.getDescription());
  }

  @Test
  void acceptsEveryPriorityAndStatusEnumValue() {
    TaskService service = service("enum-values.json");

    for (Priority priority : Priority.values()) {
      for (Status status : Status.values()) {
        Task created =
            service.create(task(priority + "-" + status, null, priority, status, null, List.of()));
        assertEquals(priority, created.getPriority());
        assertEquals(status, created.getStatus());
      }
    }
  }

  @Test
  void returnedTagsCannotMutateTaskState() {
    Task task = task("Tags", null, Priority.HIGH, Status.TODO, null, List.of("work"));

    assertThrows(UnsupportedOperationException.class, () -> task.getTags().add("urgent"));
    assertEquals(List.of("work"), task.getTags());
  }

  @Test
  void createsUuidAndKeepsItImmutableAcrossUpdates() throws Exception {
    TaskService service = service("immutable-id.json");
    Task created =
        service.create(
            task("Alpha", "Original", Priority.MEDIUM, Status.TODO, null, List.of("home")));

    assertDoesNotThrow(() -> UUID.fromString(created.getId()));

    Task advanced =
        service.update(
            created.getId(),
            task(
                "Alpha",
                "Original",
                Priority.MEDIUM,
                Status.IN_PROGRESS,
                created.getId(),
                List.of("home")));
    assertEquals(created.getId(), advanced.getId());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.update(
                created.getId(),
                task(
                    "Alpha",
                    "Original",
                    Priority.MEDIUM,
                    Status.COMPLETED,
                    UUID.randomUUID().toString(),
                    List.of("home"))));
    assertTaskEquals(advanced, service.getById(created.getId()));
  }

  @Test
  void allowsEachSpecifiedStatusTransition() {
    TaskService service = service("valid-transitions.json");
    Task current =
        service.create(task("Transitions", null, Priority.HIGH, Status.TODO, null, List.of()));

    for (Status next : List.of(Status.IN_PROGRESS, Status.COMPLETED, Status.TODO)) {
      current =
          service.update(
              current.getId(),
              task(
                  current.getTitle(),
                  current.getDescription(),
                  current.getPriority(),
                  next,
                  current.getId(),
                  current.getTags()));
      assertEquals(next, current.getStatus());
      assertEquals(current.getId(), service.getById(current.getId()).getId());
    }
  }

  @Test
  void sameStatusUpdateReturnsCurrentTaskAndDoesNotPersistChanges() throws Exception {
    Path storage = tempDir.resolve("same-status.json");
    TaskService service = new TaskService(new JsonTaskRepository(storage.toString()));
    Task created =
        service.create(
            task(
                "Current title",
                "Current description",
                Priority.MEDIUM,
                Status.TODO,
                null,
                List.of("current")));
    byte[] persistedBefore = Files.readAllBytes(storage);

    Task result =
        service.update(
            created.getId(),
            task(
                "Changed title",
                "Changed description",
                Priority.HIGH,
                Status.TODO,
                created.getId(),
                List.of("changed")));

    assertTaskEquals(created, result);
    assertTaskEquals(created, service.getById(created.getId()));
    assertArrayEquals(persistedBefore, Files.readAllBytes(storage));
  }

  @Test
  void rejectsInvalidTransitionWithoutChangingStoredTask() throws Exception {
    Path storage = tempDir.resolve("invalid-transition.json");
    TaskService service = new TaskService(new JsonTaskRepository(storage.toString()));
    Task created =
        service.create(task("Alpha", "Original", Priority.MEDIUM, Status.TODO, null, List.of()));
    byte[] persistedBefore = Files.readAllBytes(storage);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.update(
                created.getId(),
                task(
                    "Altered",
                    "Altered",
                    Priority.HIGH,
                    Status.COMPLETED,
                    created.getId(),
                    List.of("changed"))));

    assertTaskEquals(created, service.getById(created.getId()));
    assertArrayEquals(persistedBefore, Files.readAllBytes(storage));
  }

  private TaskService service(String filename) {
    return new TaskService(new JsonTaskRepository(tempDir.resolve(filename).toString()));
  }

  private Task task(
      String title,
      String description,
      Priority priority,
      Status status,
      String id,
      List<String> tags) {
    return new Task(id, title, description, priority, status, tags);
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
