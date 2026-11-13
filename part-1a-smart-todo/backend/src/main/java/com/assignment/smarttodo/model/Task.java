package com.assignment.smarttodo.model;

import com.assignment.smarttodo.validation.TagNormalizer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Task {

  private String id;
  private String title;
  private String description;
  private Priority priority;
  private Status status;
  private List<String> tags = new ArrayList<>();

  public Task() {}

  public Task(
      String id,
      String title,
      String description,
      Priority priority,
      Status status,
      List<String> tags) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.priority = priority;
    this.status = status;
    this.tags = TagNormalizer.normalize(tags);
  }

  public Task copy() {
    return new Task(this.id, this.title, this.description, this.priority, this.status, this.tags);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Priority getPriority() {
    return priority;
  }

  public void setPriority(Priority priority) {
    this.priority = priority;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public List<String> getTags() {
    return List.copyOf(tags);
  }

  public void setTags(List<String> tags) {
    this.tags = TagNormalizer.normalize(tags);
  }
}
