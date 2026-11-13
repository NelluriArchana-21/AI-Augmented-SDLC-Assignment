package com.assignment.reminderagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.parsing.ModelOutputParser;
import com.assignment.reminderagent.validation.ReminderProposalSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationalProbeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OperationalProbeRunner runner =
      new OperationalProbeRunner(
          objectMapper,
          new ModelOutputParser(objectMapper),
          new ReminderProposalSchemaValidator(objectMapper));

  @Test
  void runsMalformedOutputProbeWithoutCreatingAnAgentResponse() {
    var result = runner.run(probe("E26", "Malformed provider JSON", objectMapper.createObjectNode()
        .put("provider_completion", "{")));

    assertTrue(result.probeOutcome().passed());
    assertNull(result.actualOutput());
    assertEquals(Status.NOT_APPLICABLE, result.structural().status());
  }

  @Test
  void recordsProviderFailureSeparatelyWithoutScoringOrFabricatingOutput() {
    var result = runner.run(probe("E27", "Provider/network failure", objectMapper.createObjectNode()
        .put("failure_category", "network_unreachable")));

    assertTrue(result.probeOutcome().passed());
    assertNull(result.actualOutput());
    assertNull(result.score());
    assertEquals("NETWORK", result.operationalFailureCategory());
    assertEquals(3, result.providerAttempts().size());
  }

  @Test
  void rejectsImpossibleDateAsAValidatorProbe() {
    JsonNode candidate =
        objectMapper
            .createObjectNode()
            .put("intent", "CREATE")
            .put("task", "Known task")
            .put("date", "2026-02-30")
            .put("time", "09:00")
            .put("needs_clarification", false)
            .putNull("clarification_question");
    var result =
        runner.run(
            probe(
                "E28",
                "Invalid calendar-date output",
                objectMapper.createObjectNode().set("candidate_proposal", candidate)));

    assertTrue(result.probeOutcome().passed());
    assertNull(result.actualOutput());
    assertTrue(result.probeOutcome().findings().getFirst().contains("rejected"));
  }

  @Test
  void verifiesMissingCredentialFailureBeforeAnyTransportInvocation() {
    var result = runner.run(probe("E29", "Missing provider credential", objectMapper.createObjectNode()));

    assertTrue(result.probeOutcome().passed());
    assertEquals("CONFIGURATION", result.operationalFailureCategory());
    assertTrue(result.providerAttempts().isEmpty());
    assertFalse(result.probeOutcome().findings().isEmpty());
  }

  private EvaluationCase probe(String id, String category, JsonNode input) {
    return new EvaluationCase(
        id,
        category,
        Kind.ISOLATED_PROBE,
        input,
        "Expected isolated failure behavior.",
        List.of(ScoreDimension.NOT_APPLICABLE),
        List.of("FR-020"),
        List.of("SC-006"));
  }
}
