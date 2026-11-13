package com.assignment.reminderagent.provider;

import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import java.util.List;
import java.util.Objects;

public final class ProviderOperationalException extends RuntimeException {

  private final FailureCategory category;
  private final List<ProviderAttemptTrace> attempts;

  public ProviderOperationalException(FailureCategory category) {
    this(category, List.of());
  }

  public ProviderOperationalException(
      FailureCategory category, List<ProviderAttemptTrace> attempts) {
    super("Provider operation failed");
    this.category = Objects.requireNonNull(category, "category");
    this.attempts = List.copyOf(attempts);
  }

  public FailureCategory getCategory() {
    return category;
  }

  public List<ProviderAttemptTrace> getAttempts() {
    return attempts;
  }
}
