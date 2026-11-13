package com.assignment.reminderagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.LayerResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.ProbeOutcome;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.evaluation.model.EvaluationRunRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepairFeedbackReporterTest {

  @Test
  void linksFailuresToCasesRequirementsAndRequiredNextSteps() {
    ObjectMapper objectMapper = new ObjectMapper();
    List<EvaluationCase> cases = new EvaluationDatasetLoader(objectMapper).loadDefault();
    List<EvaluationCaseResult> results = new ArrayList<>();
    for (EvaluationCase evaluationCase : cases) {
      LayerResult status = layer(Status.NOT_RUN, "Not evaluated.");
      LayerResult invariant = status;
      LayerResult semantic = status;
      String operationalFailure = null;
      ProbeOutcome probe =
          evaluationCase.kind() == Kind.ISOLATED_PROBE
              ? new ProbeOutcome(
                  !evaluationCase.id().equals("E26"),
                  evaluationCase.id().equals("E26")
                      ? List.of("Injected probe did not fail safely.")
                      : List.of("Synthetic probe passed."))
              : null;
      if (evaluationCase.id().equals("E01")) {
        invariant = layer(Status.FAIL, "Actionable proposal lacked a required target.");
      } else if (evaluationCase.id().equals("E02")) {
        operationalFailure = "NETWORK";
      } else if (evaluationCase.id().equals("E03")) {
        status = layer(Status.PASS);
        invariant = layer(Status.PASS);
      }
      results.add(
          new EvaluationCaseResult(
              evaluationCase,
              null,
              operationalFailure,
              probe,
              evaluationCase.kind() == Kind.ISOLATED_PROBE ? layer(Status.NOT_APPLICABLE) : status,
              evaluationCase.kind() == Kind.ISOLATED_PROBE
                  ? layer(Status.NOT_APPLICABLE)
                  : invariant,
              evaluationCase.kind() == Kind.ISOLATED_PROBE
                  ? layer(Status.NOT_APPLICABLE)
                  : semantic,
              null,
              null,
              Instant.parse("2026-11-13T10:00:00Z"),
              List.of(),
              List.of()));
    }
    EvaluationRunRecord run =
        new EvaluationRunRecord(
            "feedback-run",
            Instant.parse("2026-11-13T10:00:00Z"),
            Instant.parse("2026-11-13T10:00:01Z"),
            results,
            null,
            ScoreCalculator.ACCEPTANCE_THRESHOLD,
            BigDecimal.ZERO.setScale(1),
            1,
            1,
            false,
            false,
            null,
            List.of());

    List<RepairFeedbackReporter.RepairFeedback> feedback =
        new RepairFeedbackReporter().report(run);

    assertEquals(
        List.of("E01", "E02", "E03", "E26"),
        feedback.stream().map(RepairFeedbackReporter.RepairFeedback::caseId).toList());
    var invariantFeedback = feedback.getFirst();
    assertEquals(
        RepairFeedbackReporter.FailureLayer.INVARIANT, invariantFeedback.failureLayer());
    assertTrue(invariantFeedback.unmetRequirements().containsAll(cases.getFirst().requirementReferences()));
    assertTrue(invariantFeedback.rerunRequired());
    assertFalse(invariantFeedback.actualBehavior().isBlank());
    var operationalFeedback = feedback.get(1);
    assertEquals(
        RepairFeedbackReporter.FailureLayer.OPERATIONAL, operationalFeedback.failureLayer());
    assertTrue(operationalFeedback.actualBehavior().contains("NETWORK"));
    assertTrue(feedback.get(2).humanReviewRequired());
    assertFalse(feedback.get(2).rerunRequired());
    assertEquals(RepairFeedbackReporter.FailureLayer.PROBE, feedback.get(3).failureLayer());
  }

  private LayerResult layer(Status status, String... findings) {
    return new LayerResult(status, List.of(findings));
  }
}
