package com.assignment.smarttodo.unit;

import static org.junit.jupiter.api.Assertions.*;

import com.assignment.smarttodo.model.Priority;
import com.assignment.smarttodo.model.Status;
import com.assignment.smarttodo.model.Task;
import com.assignment.smarttodo.repository.JsonTaskRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonTaskRepositoryTest {

  @TempDir Path tempDir;

  @Test
  void missingStorageFileLoadsAnEmptyCollection() {
    Path storage = tempDir.resolve("missing.json");
    JsonTaskRepository repository = repository(storage);

    assertTrue(repository.load().isEmpty());
    assertFalse(Files.exists(storage));
  }

  @Test
  void persistsAndRecoversTasksInInsertionOrderAfterRepositoryRecreation() {
    Path storage = tempDir.resolve("tasks.json");
    JsonTaskRepository writer = repository(storage);
    Task first = task("1", "Alpha", Priority.HIGH, Status.TODO);
    Task second = task("2", "Beta", Priority.LOW, Status.IN_PROGRESS);

    writer.save(first);
    writer.save(second);

    List<Task> recovered = repository(storage).load();

    assertEquals(List.of("1", "2"), recovered.stream().map(Task::getId).toList());
    assertTaskEquals(first, recovered.get(0));
    assertTaskEquals(second, recovered.get(1));
  }

  @Test
  void malformedJsonFailsExplicitlyAndIsNotReplacedByAnEmptyStore() throws IOException {
    Path storage = tempDir.resolve("malformed.json");
    byte[] invalidContent = "{ definitely not valid JSON".getBytes();
    Files.write(storage, invalidContent);
    JsonTaskRepository repository = repository(storage);

    IllegalStateException error = assertThrows(IllegalStateException.class, repository::load);
    assertStorageReadError(error, storage);
    assertArrayEquals(invalidContent, Files.readAllBytes(storage));

    assertThrows(
        IllegalStateException.class,
        () -> repository.save(task("new", "New task", Priority.HIGH, Status.TODO)));
    assertArrayEquals(invalidContent, Files.readAllBytes(storage));
  }

  @Test
  void emptyExistingFileIsRejectedAsInvalidJsonAndPreserved() throws IOException {
    Path storage = tempDir.resolve("empty.json");
    byte[] emptyContent = new byte[0];
    Files.write(storage, emptyContent);
    JsonTaskRepository repository = repository(storage);

    IllegalStateException error = assertThrows(IllegalStateException.class, repository::load);

    assertStorageReadError(error, storage);
    assertArrayEquals(emptyContent, Files.readAllBytes(storage));
    assertThrows(
        IllegalStateException.class,
        () -> repository.save(task("new", "New task", Priority.HIGH, Status.TODO)));
    assertArrayEquals(emptyContent, Files.readAllBytes(storage));
  }

  @Test
  void nullJsonValueIsRejectedAsAnInvalidTaskCollectionAndPreserved() throws IOException {
    Path storage = tempDir.resolve("null-collection.json");
    byte[] invalidCollection = "null".getBytes();
    Files.write(storage, invalidCollection);
    JsonTaskRepository repository = repository(storage);

    IllegalStateException error = assertThrows(IllegalStateException.class, repository::load);

    assertStorageReadError(error, storage);
    assertTrue(error.getCause().getMessage().contains("must be a JSON array"));
    assertArrayEquals(invalidCollection, Files.readAllBytes(storage));
  }

  @Test
  void inaccessibleDirectoryAtStoragePathFailsWithoutReplacingItsContents() throws IOException {
    Path storage = tempDir.resolve("not-a-file.json");
    JsonTaskRepository repository = repository(storage);
    Files.createDirectory(storage);
    Path marker = storage.resolve("preserve.txt");
    byte[] markerContent = "preserve this entry".getBytes();
    Files.write(marker, markerContent);

    IllegalStateException error = assertThrows(IllegalStateException.class, repository::load);

    assertStorageReadError(error, storage);
    assertTrue(Files.isDirectory(storage));
    assertArrayEquals(markerContent, Files.readAllBytes(marker));
    assertThrows(
        IllegalStateException.class,
        () -> repository.save(task("new", "New task", Priority.HIGH, Status.TODO)));
    assertArrayEquals(markerContent, Files.readAllBytes(marker));
  }

  @Test
  void danglingStorageSymlinkIsNotTreatedAsAMissingStore() throws IOException {
    Path storage = tempDir.resolve("dangling.json");
    Path missingTarget = tempDir.resolve("missing-target.json");
    Files.createSymbolicLink(storage, missingTarget);
    JsonTaskRepository repository = repository(storage);

    IllegalStateException error = assertThrows(IllegalStateException.class, repository::load);

    assertStorageReadError(error, storage);
    assertTrue(Files.isSymbolicLink(storage));
    assertFalse(Files.exists(missingTarget));
  }

  @Test
  void writeFailurePropagatesAndPreservesPreviouslyPersistedData() throws IOException {
    Path storage = tempDir.resolve("tasks.json");
    JsonTaskRepository repository = repository(storage);
    Task existing = task("existing", "Existing task", Priority.MEDIUM, Status.TODO);
    repository.save(existing);
    byte[] previouslyPersistedData = Files.readAllBytes(storage);

    Task serializationFailure =
        new Task(
            "new", "New task", "New description", Priority.HIGH, Status.TODO, List.of("work")) {
          @Override
          public String getTitle() {
            throw new IllegalStateException("simulated serialization failure");
          }
        };

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> repository.saveAll(List.of(serializationFailure)));

    assertTrue(error.getMessage().contains("Unable to persist tasks to storage"));
    assertInstanceOf(IOException.class, error.getCause());
    assertArrayEquals(previouslyPersistedData, Files.readAllBytes(storage));
    assertTaskEquals(existing, repository.load().get(0));
  }

  @Test
  void inaccessibleStorageParentIsNotTreatedAsAMissingStore() throws IOException {
    Path storageDirectory = tempDir.resolve("storage");
    Path storage = storageDirectory.resolve("tasks.json");
    JsonTaskRepository repository = repository(storage);
    Task existing = task("existing", "Existing task", Priority.MEDIUM, Status.TODO);
    repository.save(existing);
    byte[] previouslyPersistedData = Files.readAllBytes(storage);

    Path preservedDirectory = tempDir.resolve("preserved-storage");
    Files.move(storageDirectory, preservedDirectory);
    Files.writeString(storageDirectory, "blocks storage directory access");

    IllegalStateException error = assertThrows(IllegalStateException.class, repository::load);

    assertStorageReadError(error, storage);
    assertArrayEquals(
        previouslyPersistedData, Files.readAllBytes(preservedDirectory.resolve("tasks.json")));
  }

  @Test
  void successfulReplacementLeavesACompleteCollectionAndNoTemporaryFiles() throws IOException {
    Path storage = tempDir.resolve("tasks.json");
    JsonTaskRepository repository = repository(storage);
    Task original = task("original", "Original task", Priority.LOW, Status.TODO);
    Task replacement = task("replacement", "Replacement task", Priority.HIGH, Status.IN_PROGRESS);
    repository.save(original);

    repository.saveAll(List.of(replacement));

    List<Task> recovered = repository(storage).load();
    assertEquals(1, recovered.size());
    assertTaskEquals(replacement, recovered.get(0));
    try (var files = Files.list(tempDir)) {
      assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("tasks-")));
    }
  }

  private JsonTaskRepository repository(Path storage) {
    return new JsonTaskRepository(storage.toString());
  }

  private Task task(String id, String title, Priority priority, Status status) {
    return new Task(id, title, "Description for " + title, priority, status, List.of("work"));
  }

  private void assertStorageReadError(IllegalStateException error, Path storage) {
    assertTrue(error.getMessage().contains("Unable to read tasks from storage"));
    assertTrue(error.getMessage().contains(storage.toString()));
    assertNotNull(error.getCause());
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
