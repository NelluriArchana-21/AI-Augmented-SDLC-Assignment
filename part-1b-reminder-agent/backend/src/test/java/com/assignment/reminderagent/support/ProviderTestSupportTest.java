package com.assignment.reminderagent.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.assignment.reminderagent.model.ReminderRequest;
import com.assignment.reminderagent.provider.ProviderResult;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderTestSupportTest {

  @Test
  void fakeProviderReturnsConfiguredResultsAndRecordsRequests() {
    var request = new ReminderRequest("remind me", "2026-01-15T10:00:00-05:00", "America/New_York");
    var expected = new ProviderResult.Success("{\"intent\":\"CLARIFY\"}");
    var provider = new FakeReminderExtractionProvider(List.of(expected));

    assertEquals(expected, provider.extract(request));
    assertEquals(List.of(request), provider.requests());
    assertThrows(IllegalStateException.class, () -> provider.extract(request));
  }

  @Test
  void fakeTransportReturnsScriptedResponsesWithoutPrintingHeaders() {
    var request =
        new FakeProviderTransport.Request(
            URI.create("https://provider.invalid"), Map.of("x-goog-api-key", "test-secret"), "{}");
    var response = new FakeProviderTransport.Response(429, "{}");
    var transport =
        new FakeProviderTransport(List.of(response, new FakeProviderTransport.NetworkFailure()));

    assertEquals(response, transport.exchange(request));
    assertEquals(new FakeProviderTransport.NetworkFailure(), transport.exchange(request));
    assertEquals(List.of(request, request), transport.requests());
    org.junit.jupiter.api.Assertions.assertFalse(request.toString().contains("test-secret"));
  }
}
