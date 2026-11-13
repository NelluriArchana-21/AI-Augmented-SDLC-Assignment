package com.assignment.smarttodo.controller;

import com.assignment.smarttodo.model.Task;
import com.assignment.smarttodo.service.TaskService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TaskController {

  private final TaskService taskService;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentional Spring-injected service collaborator; it is not exposed as task data.")
  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @PostMapping("/tasks")
  public ResponseEntity<Task> createTask(@RequestBody Task task) {
    Task created = taskService.create(task);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/tasks")
  public List<Task> listTasks(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String tag) {
    return taskService.list(status, priority, tag);
  }

  @GetMapping("/tasks/{id}")
  public Task getTask(@PathVariable String id) {
    return taskService.getById(id);
  }

  @PutMapping("/tasks/{id}")
  public ResponseEntity<Task> updateTask(@PathVariable String id, @RequestBody Task task) {
    Task updated = taskService.update(id, task);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/tasks/{id}")
  public ResponseEntity<Void> deleteTask(@PathVariable String id) {
    taskService.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
