package com.assignment.reminderagent.parsing;

import com.assignment.reminderagent.provider.ProviderAttemptTrace;
import java.util.List;

public final class MalformedModelOutputException extends RuntimeException {

  private final List<ProviderAttemptTrace> attempts;

  public MalformedModelOutputException() {
    this(List.of());
  }

  public MalformedModelOutputException(List<ProviderAttemptTrace> attempts) {
    super("Provider output is not one valid reminder proposal JSON object");
    this.attempts = List.copyOf(attempts);
  }

  public List<ProviderAttemptTrace> getAttempts() {
    return attempts;
  }
}
