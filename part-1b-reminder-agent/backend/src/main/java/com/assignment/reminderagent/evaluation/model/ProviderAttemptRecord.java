package com.assignment.reminderagent.evaluation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

public record ProviderAttemptRecord(
    @JsonProperty("attempt_number") int attemptNumber,
    @JsonProperty("category") String category,
    @JsonProperty("attempted_at") Instant attemptedAt,
    @JsonProperty("disposition") Disposition disposition) {

  public ProviderAttemptRecord {
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
