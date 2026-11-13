package com.assignment.smarttodo.repository;

import com.assignment.smarttodo.model.Task;
import java.util.List;

public interface TaskRepository {
  List<Task> findAll();

  Task findById(String id);

  Task save(Task task);

  void deleteById(String id);

  void saveAll(List<Task> tasks);
}
