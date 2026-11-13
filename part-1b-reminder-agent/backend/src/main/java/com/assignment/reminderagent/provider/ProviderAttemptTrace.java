package com.assignment.reminderagent.provider;

import java.time.Instant;
import java.util.Objects;

public record ProviderAttemptTrace(
    int attemptNumber, String category, Instant attemptedAt, Disposition disposition) {

  public ProviderAttemptTrace {
    if (attemptNumber < 1) {
      throw new IllegalArgumentException("attemptNumber must be positive");
    }
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(attemptedAt, "attemptedAt");
    Objects.requireNonNull(disposition, "disposition");
  }

  public enum Disposition {
    SUCCEEDED,
    RETRY_SCHEDULED,
    TERMINAL_FAILURE
  }
}
