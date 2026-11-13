package com.assignment.smarttodo.service;

import com.assignment.smarttodo.exception.TaskNotFoundException;
import com.assignment.smarttodo.model.Task;
import com.assignment.smarttodo.repository.TaskRepository;
import com.assignment.smarttodo.validation.TaskValidator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

  private final TaskRepository repository;
  private final TaskQueryService queryService = new TaskQueryService();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentional shared repository collaborator for task persistence; it is not returned to callers.")
  public TaskService(TaskRepository repository) {
    this.repository = repository;
  }

  public Task create(Task task) {
    TaskValidator.validateForCreate(task);
    Task normalized = task.copy();
    normalized.setId(UUID.randomUUID().toString());
    repository.save(normalized);
    return normalized.copy();
  }

  public List<Task> list(String statusFilter, String priorityFilter, String tagFilter) {
    return queryService.filterTasks(repository.findAll(), statusFilter, priorityFilter, tagFilter);
  }

  public Task getById(String id) {
    Task task = repository.findById(id);
    if (task == null) {
      throw new TaskNotFoundException("Task not found: " + id);
    }
    return task.copy();
  }

  public Task update(String id, Task task) {
    Task existing = repository.findById(id);
    if (existing == null) {
      throw new TaskNotFoundException("Task not found: " + id);
    }

    String requestId = task == null ? null : task.getId();
    if (requestId != null && !requestId.equals(id)) {
      throw new IllegalArgumentException("Task id in request body does not match the path id");
    }

    TaskValidator.validateForUpdate(id, task);

    if (task.getStatus() == existing.getStatus()) {
      return existing.copy();
    }

    TaskValidator.validateStatusTransition(existing.getStatus(), task.getStatus());

    Task updated = existing.copy();
    updated.setTitle(task.getTitle());
    updated.setDescription(task.getDescription());
    updated.setPriority(task.getPriority());
    updated.setStatus(task.getStatus());
    updated.setTags(task.getTags());
    updated.setId(id);

    List<Task> tasks = repository.findAll();
    for (int i = 0; i < tasks.size(); i++) {
      if (tasks.get(i).getId().equals(id)) {
        tasks.set(i, updated);
        repository.saveAll(tasks);
        return updated.copy();
      }
    }

    throw new TaskNotFoundException("Task not found: " + id);
  }

  public void deleteById(String id) {
    if (repository.findById(id) == null) {
      throw new TaskNotFoundException("Task not found: " + id);
    }
    repository.deleteById(id);
  }
}
