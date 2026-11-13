package com.assignment.smarttodo.model;

public enum Priority {
  LOW,
  MEDIUM,
  HIGH;

  public static Priority fromString(String value) {
    if (value == null) {
      return null;
    }
    return Priority.valueOf(value.trim().toUpperCase());
  }
}
