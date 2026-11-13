package com.assignment.reminderagent.provider;

import java.util.Objects;
import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;

public final class ProviderOperationalException extends RuntimeException {

  private final FailureCategory category;

  public ProviderOperationalException(FailureCategory category) {
    super("Provider operation failed");
    this.category = Objects.requireNonNull(category, "category");
  }

  public FailureCategory getCategory() {
    return category;
  }
}
