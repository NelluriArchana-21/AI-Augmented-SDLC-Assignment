package com.assignment.reminderagent.provider;

import java.util.List;

public interface ReminderExtractionProvider {

  ProviderResult extract(String trustedInstructions, String untrustedRequest);

  default Invocation extractWithAttempts(String trustedInstructions, String untrustedRequest) {
    return new Invocation(extract(trustedInstructions, untrustedRequest), List.of());
  }

  record Invocation(ProviderResult result, List<ProviderAttemptTrace> attempts) {
    public Invocation {
      attempts = List.copyOf(attempts);
    }
  }
}
