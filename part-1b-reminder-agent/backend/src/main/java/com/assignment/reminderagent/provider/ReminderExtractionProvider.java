package com.assignment.reminderagent.provider;

public interface ReminderExtractionProvider {

  ProviderResult extract(String trustedInstructions, String untrustedRequest);
}
