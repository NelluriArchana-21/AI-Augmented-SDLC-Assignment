package com.assignment.reminderagent.provider;

import java.util.Objects;

public sealed interface ProviderResult
    permits ProviderResult.Success, ProviderResult.OperationalFailure {

  record Success(String rawContent) implements ProviderResult {
    public Success {
      Objects.requireNonNull(rawContent, "rawContent");
    }

    @Override
    public String toString() {
      return "Success[rawContent=REDACTED]";
    }
  }

  record OperationalFailure(FailureCategory category) implements ProviderResult {
    public OperationalFailure {
      Objects.requireNonNull(category, "category");
    }
  }

  enum FailureCategory {
    CONFIGURATION,
    AUTHENTICATION,
    RATE_LIMIT,
    SERVER,
    NETWORK,
    TIMEOUT
  }
}
