package com.assignment.reminderagent.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.api.ApiExceptionHandler;
import com.assignment.reminderagent.config.ReminderAgentProperties;
import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.model.ReminderRequest;
import com.assignment.reminderagent.provider.ProviderOperationalException;
import com.assignment.reminderagent.provider.ProviderResult;
import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReminderFoundationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void actionProposalSerializesExactSnakeCaseFieldsIncludingNulls() throws Exception {
    var proposal = new ActionProposal(ReminderIntent.CLARIFY, null, null, null, true, "What time?");

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(proposal));

    assertEquals(
        java.util.Set.of(
            "intent",
            "task",
            "date",
            "time",
            "needs_clarification",
            "clarification_question"),
        java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(json.fieldNames(), 0), false)
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals("CLARIFY", json.get("intent").asText());
    assertTrue(json.get("needs_clarification").asBoolean());
    assertTrue(json.get("task").isNull());
  }

  @Test
  void reminderRequestConstrainsTextAndMapsInputFieldNames() throws Exception {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      var validRequest =
          new ReminderRequest("remind me", "2026-01-15T10:00:00-05:00", "America/New_York");
      var blankRequest =
          new ReminderRequest("   ", "2026-01-15T10:00:00-05:00", "America/New_York");
      var overlongRequest =
          new ReminderRequest(
              "x".repeat(ReminderRequest.MAX_NATURAL_LANGUAGE_REQUEST_LENGTH + 1),
              "2026-01-15T10:00:00-05:00",
              "America/New_York");

      assertTrue(validator.validate(validRequest).isEmpty());
      assertFalse(validator.validate(blankRequest).isEmpty());
      assertFalse(validator.validate(overlongRequest).isEmpty());
    }

    var decoded =
        objectMapper.readValue(
            """
            {
              "natural_language_request": "remind me",
              "reference_datetime": "2026-01-15T10:00:00-05:00",
              "timezone": "America/New_York"
            }
            """,
            ReminderRequest.class);
    assertEquals("remind me", decoded.naturalLanguageRequest());
    assertEquals("2026-01-15T10:00:00-05:00", decoded.referenceDatetime());
  }

  @Test
  void propertiesNeverExposeConfiguredApiKeyInToString() {
    var properties = new ReminderAgentProperties();
    properties.setApiKey("test-secret");
    properties.setModelId("configured-model");

    assertEquals("x-goog-api-key", ReminderAgentProperties.API_KEY_HEADER);
    assertFalse(properties.toString().contains("test-secret"));
    assertFalse(properties.toString().contains("configured-model"));
    assertFalse(new ProviderResult.Success("provider-secret").toString().contains("provider-secret"));
  }

  @Test
  void providerFailuresMapToRedactedOperationalResponse() {
    var exception = new ProviderOperationalException(FailureCategory.AUTHENTICATION);
    var response = new ApiExceptionHandler().handleProviderOperationalFailure(exception);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals("OPERATIONAL_FAILURE", response.getBody().code());
    assertFalse(response.getBody().message().contains("AUTHENTICATION"));
    assertFalse(response.getBody().message().contains(exception.getMessage()));
  }
}
