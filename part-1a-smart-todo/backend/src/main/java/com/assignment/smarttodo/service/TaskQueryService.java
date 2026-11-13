package com.assignment.smarttodo.service;

import com.assignment.smarttodo.model.Priority;
import com.assignment.smarttodo.model.Status;
import com.assignment.smarttodo.model.Task;
import com.assignment.smarttodo.validation.TagNormalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class TaskQueryService {

  public List<Task> filterTasks(
      List<Task> tasks, String statusFilter, String priorityFilter, String tagFilter) {
    Status status = parseStatus(statusFilter);
    Priority priority = parsePriority(priorityFilter);
    String normalizedTag = TagNormalizer.normalizeValue(tagFilter);

    Stream<Task> stream = tasks.stream();
    if (status != null) {
      stream = stream.filter(task -> task.getStatus() == status);
    }
    if (priority != null) {
      stream = stream.filter(task -> task.getPriority() == priority);
    }
    if (normalizedTag != null) {
      stream =
          stream.filter(
              task ->
                  task.getTags() != null
                      && task.getTags().stream()
                          .anyMatch(tag -> Objects.equals(tag, normalizedTag)));
    }

    return stream
        .sorted(Comparator.comparingInt(task -> priorityOrder(task.getPriority())))
        .toList();
  }

  private int priorityOrder(Priority priority) {
    return switch (priority) {
      case HIGH -> 0;
      case MEDIUM -> 1;
      case LOW -> 2;
      default -> 3;
    };
  }

  private Status parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Status.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid status filter value: " + value, ex);
    }
  }

  private Priority parsePriority(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Priority.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid priority filter value: " + value, ex);
    }
  }
}
