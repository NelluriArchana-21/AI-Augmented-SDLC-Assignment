package com.assignment.smarttodo.repository;

import com.assignment.smarttodo.model.Task;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class JsonTaskRepository implements TaskRepository {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Path storagePath;

  public JsonTaskRepository(@Value("${task.storage.path}") String storagePath) {
    this.storagePath = Paths.get(storagePath);
  }

  public List<Task> load() {
    return loadTasks();
  }

  @Override
  public List<Task> findAll() {
    return loadTasks();
  }

  @Override
  public Task findById(String id) {
    return loadTasks().stream()
        .filter(task -> task.getId() != null && task.getId().equals(id))
        .findFirst()
        .orElse(null);
  }

  @Override
  public Task save(Task task) {
    List<Task> tasks = loadTasks();
    tasks.add(task);
    persistTasks(tasks);
    return task;
  }

  @Override
  public void deleteById(String id) {
    List<Task> tasks = loadTasks();
    boolean removed = tasks.removeIf(task -> task.getId() != null && task.getId().equals(id));
    if (!removed) {
      throw new IllegalArgumentException("Task not found: " + id);
    }
    persistTasks(tasks);
  }

  @Override
  public void saveAll(List<Task> tasks) {
    persistTasks(tasks);
  }

  private List<Task> loadTasks() {
    try {
      Files.readAttributes(storagePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (NoSuchFileException e) {
      validateExistingStorageParents();
      return new ArrayList<>();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read tasks from storage: " + storagePath, e);
    }

    try {
      String content = Files.readString(storagePath).trim();
      List<Task> tasks = OBJECT_MAPPER.readValue(content, new TypeReference<List<Task>>() {});
      if (tasks == null) {
        throw new IOException("Task collection must be a JSON array");
      }
      return tasks;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read tasks from storage: " + storagePath, e);
    }
  }

  private void validateExistingStorageParents() {
    Path parent = storagePath.getParent();
    while (parent != null) {
      try {
        BasicFileAttributes attributes = Files.readAttributes(parent, BasicFileAttributes.class);
        if (!attributes.isDirectory()) {
          throw new IllegalStateException(
              "Unable to read tasks from storage: " + storagePath,
              new NotDirectoryException(parent.toString()));
        }
      } catch (NoSuchFileException e) {
        parent = parent.getParent();
        continue;
      } catch (IOException e) {
        throw new IllegalStateException("Unable to read tasks from storage: " + storagePath, e);
      }
      parent = parent.getParent();
    }
  }

  private void persistTasks(List<Task> tasks) {
    try {
      Path parent = storagePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Path tempFile =
          Files.createTempFile(parent == null ? Path.of(".") : parent, "tasks-", ".tmp");
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), tasks);
      try {
        Files.move(
            tempFile,
            storagePath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException atomicMoveFailed) {
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to persist tasks to storage: " + storagePath, e);
    }
  }
}
