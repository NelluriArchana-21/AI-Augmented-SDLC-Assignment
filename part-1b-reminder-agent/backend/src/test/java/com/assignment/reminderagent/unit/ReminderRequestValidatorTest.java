package com.assignment.reminderagent.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.validation.ReminderRequestValidator;
import com.assignment.reminderagent.validation.ReminderRequestValidator.Clarification;
import com.assignment.reminderagent.validation.ReminderRequestValidator.Valid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ReminderRequestValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ReminderRequestValidator validator = new ReminderRequestValidator();

  @Test
  void acceptsValidRequestAtTheMaximumLength() throws Exception {
    String request = "x".repeat(4_000);

    var result =
        validator.validate(
            objectMapper.readTree(requestJson(request, "2026-01-15T10:00:00-05:00", "America/New_York")));

    Valid valid = assertInstanceOf(Valid.class, result);
    assertEquals(request, valid.request().naturalLanguageRequest());
    assertEquals("2026-01-15T10:00:00-05:00", valid.request().referenceDatetime());
    assertEquals("America/New_York", valid.request().timezone());
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void clarifiesMissingBlankWrongTypedOrOverlongRequestText(String json) throws Exception {
    assertClarification(validator.validate(objectMapper.readTree(json)));
  }

  static Stream<String> invalidRequests() {
    return Stream.of(
        "{\"reference_datetime\":\"2026-01-15T10:00:00-05:00\",\"timezone\":\"America/New_York\"}",
        requestJson("  ", "2026-01-15T10:00:00-05:00", "America/New_York"),
        requestJson("x".repeat(4_001), "2026-01-15T10:00:00-05:00", "America/New_York"),
        "{\"natural_language_request\":9,\"reference_datetime\":\"2026-01-15T10:00:00-05:00\",\"timezone\":\"America/New_York\"}");
  }

  @ParameterizedTest
  @MethodSource("invalidTimeContexts")
  void clarifiesMissingMistypedMalformedOrInvalidTimeContext(String json) throws Exception {
    assertClarification(validator.validate(objectMapper.readTree(json)));
  }

  static Stream<String> invalidTimeContexts() {
    return Stream.of(
        "{\"natural_language_request\":\"remind me\",\"timezone\":\"America/New_York\"}",
        requestJson("remind me", "2026-01-15 10:00:00-05:00", "America/New_York"),
        requestJson("remind me", "2026-01-15T10:00:00-05:00", "Mars/Olympus_Mons"),
        requestJson("remind me", "2026-01-15T10:00:00-05:00", "+05:00"),
        "{\"natural_language_request\":\"remind me\",\"reference_datetime\":9,\"timezone\":\"America/New_York\"}",
        requestJson("remind me", "2026-01-15T10:00:00Z", "America/New_York"),
        "{\"natural_language_request\":\"remind me\",\"reference_datetime\":\"2026-01-15T10:00:00-05:00\",\"timezone\":9}");
  }

  @Test
  void invalidRootProducesAContractValidClarification() {
    assertClarification(validator.validate(null));
  }

  private void assertClarification(ReminderRequestValidator.Result result) {
    Clarification clarification = assertInstanceOf(Clarification.class, result);
    ActionProposal proposal = clarification.proposal();

    assertEquals(ReminderIntent.CLARIFY, proposal.intent());
    assertTrue(proposal.needsClarification());
    assertNotNull(proposal.clarificationQuestion());
    assertTrue(!proposal.clarificationQuestion().isBlank());
    assertNull(proposal.task());
    assertNull(proposal.date());
    assertNull(proposal.time());
  }

  private static String requestJson(String request, String referenceDatetime, String timezone) {
    return """
        {"natural_language_request":%s,"reference_datetime":%s,"timezone":%s}
        """
        .formatted(
            quote(request),
            quote(referenceDatetime),
            quote(timezone));
  }

  private static String quote(String value) {
    try {
      return new ObjectMapper().writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encode test JSON", exception);
    }
  }
}
