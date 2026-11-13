package com.assignment.smarttodo.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.assignment.smarttodo.model.Priority;
import com.assignment.smarttodo.model.Status;
import com.assignment.smarttodo.model.Task;
import com.assignment.smarttodo.service.TaskQueryService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskQueryServiceTest {

  private final TaskQueryService queryService = new TaskQueryService();

  @Test
  void filtersByStatusIndividually() {
    List<Task> tasks = tasks();

    List<Task> result = queryService.filterTasks(tasks, "TODO", null, null);

    assertEquals(List.of("first-high", "second-high", "medium-todo"), titles(result));
  }

  @Test
  void filtersByPriorityIndividually() {
    List<Task> tasks = tasks();

    List<Task> result = queryService.filterTasks(tasks, null, "HIGH", null);

    assertEquals(List.of("first-high", "second-high", "high-progress"), titles(result));
  }

  @Test
  void filtersByTagIndividuallyUsingTrimmedCaseInsensitiveMatch() {
    List<Task> tasks = tasks();

    List<Task> result = queryService.filterTasks(tasks, null, null, "  WoRk  ");

    assertEquals(List.of("first-high", "second-high", "medium-todo"), titles(result));
  }

  @Test
  void taskTagsAreTrimmedLowercasedAndDeduplicatedInFirstSeenOrder() {
    Task task =
        new Task(
            "task",
            "Tags",
            null,
            Priority.HIGH,
            Status.TODO,
            List.of(" Work ", "work", " URGENT", "urgent "));

    assertEquals(List.of("work", "urgent"), task.getTags());
  }

  @Test
  void combinesStatusPriorityAndTagFiltersWithAndSemantics() {
    List<Task> tasks = tasks();

    List<Task> result = queryService.filterTasks(tasks, "TODO", "HIGH", "work");

    assertEquals(List.of("first-high", "second-high"), titles(result));
  }

  @Test
  void emptyFiltersReturnAllTasksSortedByPriorityAndPreserveEqualPriorityOrder() {
    List<Task> tasks = tasks();

    List<Task> result = queryService.filterTasks(tasks, null, "", "  ");

    assertEquals(
        List.of("first-high", "second-high", "high-progress", "medium-todo", "low-progress"),
        titles(result));
  }

  @Test
  void sortingUsesHighThenMediumThenLowAndKeepsEqualPriorityInsertionOrder() {
    List<Task> tasks = tasks();

    List<Task> result = queryService.filterTasks(tasks, null, null, null);

    assertEquals(
        List.of(Priority.HIGH, Priority.HIGH, Priority.HIGH, Priority.MEDIUM, Priority.LOW),
        result.stream().map(Task::getPriority).toList());
    assertEquals(
        List.of("first-high", "second-high", "high-progress", "medium-todo", "low-progress"),
        titles(result));
  }

  @Test
  void filteringAndSortingDoNotMutateInputTasksOrCollection() {
    List<Task> tasks = tasks();
    List<Task> before = tasks.stream().map(Task::copy).toList();
    List<String> inputOrderBefore = titles(tasks);

    queryService.filterTasks(tasks, "TODO", "HIGH", "work");

    assertEquals(inputOrderBefore, titles(tasks));
    for (int i = 0; i < tasks.size(); i++) {
      assertTaskEquals(before.get(i), tasks.get(i));
    }
  }

  private List<Task> tasks() {
    List<Task> tasks = new ArrayList<>();
    tasks.add(task("first-high", Priority.HIGH, Status.TODO, List.of("work", "urgent")));
    tasks.add(task("second-high", Priority.HIGH, Status.TODO, List.of("work")));
    tasks.add(task("medium-todo", Priority.MEDIUM, Status.TODO, List.of("work")));
    tasks.add(task("low-progress", Priority.LOW, Status.IN_PROGRESS, List.of("ops")));
    tasks.add(task("high-progress", Priority.HIGH, Status.IN_PROGRESS, List.of("ops")));
    return tasks;
  }

  private Task task(String title, Priority priority, Status status, List<String> tags) {
    return new Task(title, title, null, priority, status, tags);
  }

  private List<String> titles(List<Task> tasks) {
    return tasks.stream().map(Task::getTitle).toList();
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
