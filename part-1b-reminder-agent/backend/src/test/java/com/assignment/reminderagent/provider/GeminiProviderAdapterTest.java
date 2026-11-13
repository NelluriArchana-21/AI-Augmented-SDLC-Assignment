package com.assignment.reminderagent.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.config.ReminderAgentProperties;
import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.assignment.reminderagent.provider.ProviderResult.OperationalFailure;
import com.assignment.reminderagent.provider.ProviderResult.Success;
import com.assignment.reminderagent.support.FakeProviderTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiProviderAdapterTest {

  private static final String API_KEY = "local-test-secret";
  private static final String COMPLETION =
      """
      {"intent":"CREATE","task":"Call Maya","date":"2026-01-16","time":"09:00","needs_clarification":false,"clarification_question":null}
      """.strip();

  @Test
  void sendsGenerateContentRequestWithSeparateTrustedAndUntrustedContent() {
    var transport =
        new FakeProviderTransport(
            List.of(
                new FakeProviderTransport.Response(
                    200,
                    """
                    {"candidates":[{"content":{"parts":[{"text":%s}]}}]}
                    """.formatted(new ObjectMapper().valueToTree(COMPLETION).toString()))));
    var adapter = adapter(properties(API_KEY, "configured-model"), transport);

    ProviderResult result = adapter.extract("Trusted contract and time context", "untrusted request");

    assertEquals(new Success(COMPLETION), result);
    assertEquals(1, transport.requests().size());
    var request = transport.requests().getFirst();
    assertTrue(request.uri().getPath().endsWith("/models/configured-model:generateContent"));
    assertEquals(null, request.uri().getRawQuery());
    assertEquals(API_KEY, request.headers().get("x-goog-api-key"));
    assertTrue(request.body().contains("\"systemInstruction\""));
    assertTrue(request.body().contains("Trusted contract and time context"));
    assertTrue(request.body().contains("untrusted request"));
    assertTrue(request.body().contains("\"responseMimeType\":\"application/json\""));
    assertTrue(request.body().contains("\"responseSchema\""));
    assertFalse(request.body().contains(API_KEY));
    assertTrue(request.toString().contains("headerNames"));
    assertFalse(request.toString().contains(API_KEY));
    assertFalse(new GeminiTransport.Response(401, API_KEY).toString().contains(API_KEY));
    assertEquals(Duration.ofSeconds(20), request.timeout());
  }

  @Test
  void returnsAuthenticationFailureWithoutRetryingOrExposingSecrets() {
    var transport =
        new FakeProviderTransport(List.of(new FakeProviderTransport.Response(401, API_KEY)));
    var props = properties(API_KEY, "configured-model");
    ProviderResult result = adapter(props, transport).extract("trusted", "untrusted");

    assertEquals(new OperationalFailure(FailureCategory.AUTHENTICATION), result);
    assertEquals(1, transport.requests().size());
    assertFalse(result.toString().contains(API_KEY));
    assertFalse(props.toString().contains(API_KEY));
  }

  @Test
  void mapsPermanentClientErrorsToConfigurationFailureWithoutRetrying() {
    var transport =
        new FakeProviderTransport(
            List.of(
                new FakeProviderTransport.Response(400, "redacted"),
                new FakeProviderTransport.Response(200, "{}")));

    ProviderResult result = adapter(properties(API_KEY, "configured-model"), transport)
        .extract("trusted", "untrusted");

    assertEquals(new OperationalFailure(FailureCategory.CONFIGURATION), result);
    assertEquals(1, transport.requests().size());
  }

  @Test
  void malformedCompletionIsReturnedUnchangedWithoutRetry() {
    String malformed = "{";
    var transport = successfulTransport(malformed);

    ProviderResult result = adapter(properties(API_KEY, "configured-model"), transport)
        .extract("trusted", "untrusted");

    assertEquals(new Success(malformed), result);
    assertEquals(1, transport.requests().size());
  }

  @Test
  void missingCredentialsFailBeforeTransportInvocation() {
    var transport = new FakeProviderTransport(List.of());
    ProviderResult result =
        adapter(properties("  ", "configured-model"), transport).extract("trusted", "untrusted");

    assertInstanceOf(OperationalFailure.class, result);
    assertEquals(FailureCategory.CONFIGURATION, ((OperationalFailure) result).category());
    assertTrue(transport.requests().isEmpty());
  }

  @Test
  void missingModelConfigurationFailsBeforeTransportInvocation() {
    var transport = new FakeProviderTransport(List.of());
    ProviderResult result =
        adapter(properties(API_KEY, " "), transport).extract("trusted", "untrusted");

    assertEquals(new OperationalFailure(FailureCategory.CONFIGURATION), result);
    assertTrue(transport.requests().isEmpty());
  }

  @Test
  void mapsRateLimitStatusAndRetriesIt() {
    var transport =
        new FakeProviderTransport(
            List.of(
                new FakeProviderTransport.Response(429, "redacted"),
                new FakeProviderTransport.Response(401, "redacted")));
    var sleeps = new java.util.ArrayList<Duration>();
    var policy = new ProviderRetryPolicy(sleeps::add, () -> 0L);

    ProviderResult result =
        adapter(properties(API_KEY, "configured-model"), transport, policy)
            .extract("trusted", "untrusted");

    assertEquals(new OperationalFailure(FailureCategory.AUTHENTICATION), result);
    assertEquals(2, transport.requests().size());
    assertEquals(List.of(Duration.ofSeconds(1)), sleeps);
  }

  @Test
  void retriesNetworkFailureThroughTheFakeTransport() {
    String responseBody =
        """
        {"candidates":[{"content":{"parts":[{"text":%s}]}}]}
        """
            .formatted(new ObjectMapper().valueToTree(COMPLETION).toString());
    var transport =
        new FakeProviderTransport(
            List.of(
                new FakeProviderTransport.NetworkFailure(),
                new FakeProviderTransport.Response(200, responseBody)));
    var sleeps = new java.util.ArrayList<Duration>();
    var policy = new ProviderRetryPolicy(sleeps::add, () -> 0L);

    ProviderResult result =
        adapter(properties(API_KEY, "configured-model"), transport, policy)
            .extract("trusted", "untrusted");

    assertEquals(new Success(COMPLETION), result);
    assertEquals(2, transport.requests().size());
    assertEquals(List.of(Duration.ofSeconds(1)), sleeps);
    assertTrue(transport.requests().stream().allMatch(request -> request.timeout().equals(Duration.ofSeconds(20))));
  }

  @Test
  void transientServerFailuresRetryUsingTheBoundedPolicy() {
    var transport =
        new FakeProviderTransport(
            List.of(
                new FakeProviderTransport.Response(503, "redacted"),
                new FakeProviderTransport.Response(503, "redacted"),
                new FakeProviderTransport.Response(503, "redacted")));
    var sleeps = new java.util.ArrayList<Duration>();
    var policy = new ProviderRetryPolicy(sleeps::add, () -> 0L);

    ProviderResult result =
        adapter(properties(API_KEY, "configured-model"), transport, policy)
            .extract("trusted", "untrusted");

    assertEquals(new OperationalFailure(FailureCategory.SERVER), result);
    assertEquals(3, transport.requests().size());
    assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps);
  }

  private FakeProviderTransport successfulTransport(String completion) {
    String responseBody =
        """
        {"candidates":[{"content":{"parts":[{"text":%s}]}}]}
        """
            .formatted(new ObjectMapper().valueToTree(completion).toString());
    return new FakeProviderTransport(List.of(new FakeProviderTransport.Response(200, responseBody)));
  }

  private GeminiProviderAdapter adapter(
      ReminderAgentProperties properties, FakeProviderTransport transport) {
    return adapter(properties, transport, new ProviderRetryPolicy());
  }

  private GeminiProviderAdapter adapter(
      ReminderAgentProperties properties,
      FakeProviderTransport transport,
      ProviderRetryPolicy retryPolicy) {
    return new GeminiProviderAdapter(properties, new ObjectMapper(), transport, retryPolicy);
  }

  private ReminderAgentProperties properties(String apiKey, String modelId) {
    var properties = new ReminderAgentProperties();
    properties.setApiKey(apiKey);
    properties.setModelId(modelId);
    return properties;
  }
}
