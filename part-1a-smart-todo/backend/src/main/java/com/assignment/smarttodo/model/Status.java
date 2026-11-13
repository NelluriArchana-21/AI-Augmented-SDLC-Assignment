package com.assignment.smarttodo.model;

public enum Status {
  TODO,
  IN_PROGRESS,
  COMPLETED;

  public static Status fromString(String value) {
    if (value == null) {
      return null;
    }
    return Status.valueOf(value.trim().toUpperCase().replace(' ', '_'));
  }
}
