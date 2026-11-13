package com.assignment.smarttodo.validation;

import com.assignment.smarttodo.model.Priority;
import com.assignment.smarttodo.model.Status;
import com.assignment.smarttodo.model.Task;
import java.util.Objects;

public final class TaskValidator {

  private TaskValidator() {}

  public static void validateForCreate(Task task) {
    validateCommon(task);
    if (task.getId() != null && !task.getId().isBlank()) {
      throw new IllegalArgumentException("Task id must not be supplied during creation");
    }
  }

  public static void validateForUpdate(String pathId, Task task) {
    validateCommon(task);
    if (task.getId() != null && !task.getId().equals(pathId)) {
      throw new IllegalArgumentException("Task id in request body does not match the path id");
    }
  }

  public static void validateStatusTransition(Status currentStatus, Status requestedStatus) {
    if (Objects.equals(currentStatus, requestedStatus)) {
      return;
    }

    boolean allowed =
        (currentStatus == Status.TODO && requestedStatus == Status.IN_PROGRESS)
            || (currentStatus == Status.IN_PROGRESS && requestedStatus == Status.COMPLETED)
            || (currentStatus == Status.COMPLETED && requestedStatus == Status.TODO);

    if (!allowed) {
      throw new IllegalArgumentException(
          "Invalid status transition from " + currentStatus + " to " + requestedStatus);
    }
  }

  private static void validateCommon(Task task) {
    if (task == null) {
      throw new IllegalArgumentException("Task payload is required");
    }
    if (task.getTitle() == null || task.getTitle().isBlank()) {
      throw new IllegalArgumentException("Task title is required");
    }
    if (task.getTitle().length() > 120) {
      throw new IllegalArgumentException("Task title must be at most 120 characters");
    }
    if (task.getPriority() == null) {
      throw new IllegalArgumentException("Task priority is required");
    }
    if (task.getStatus() == null) {
      throw new IllegalArgumentException("Task status is required");
    }
    if (task.getDescription() != null && task.getDescription().isBlank()) {
      task.setDescription(null);
    }
    if (task.getTags() == null) {
      task.setTags(new java.util.ArrayList<>());
    }
    if (task.getPriority() != Priority.LOW
        && task.getPriority() != Priority.MEDIUM
        && task.getPriority() != Priority.HIGH) {
      throw new IllegalArgumentException("Task priority must be LOW, MEDIUM, or HIGH");
    }
    if (task.getStatus() != Status.TODO
        && task.getStatus() != Status.IN_PROGRESS
        && task.getStatus() != Status.COMPLETED) {
      throw new IllegalArgumentException("Task status must be TODO, IN_PROGRESS, or COMPLETED");
    }
  }
}
