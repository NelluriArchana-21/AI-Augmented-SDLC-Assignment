package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.LayerResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.evaluation.model.EvaluationRunRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class RepairFeedbackReporter {

  public List<RepairFeedback> report(EvaluationRunRecord run) {
    if (run == null) {
      throw new IllegalArgumentException("Evaluation run is required");
    }
    List<RepairFeedback> feedback = new ArrayList<>();
    for (EvaluationCaseResult result : run.caseResults()) {
      if (result.operationalFailureCategory() != null) {
        feedback.add(
            item(
                result,
                FailureLayer.OPERATIONAL,
                "Provider/network failure category: " + result.operationalFailureCategory(),
                List.of("Resolve the operational failure before rerunning model-quality cases."),
                true,
                false));
        continue;
      }
      if (result.probeOutcome() != null && !result.probeOutcome().passed()) {
        feedback.add(
            item(
                result,
                FailureLayer.PROBE,
                String.join(" ", result.probeOutcome().findings()),
                List.of("Correct the isolated safety/validator behavior and rerun the probe."),
                true,
                false));
        continue;
      }
      addLayerFeedback(feedback, result, result.structural(), FailureLayer.STRUCTURAL);
      addLayerFeedback(feedback, result, result.invariant(), FailureLayer.INVARIANT);
      addLayerFeedback(feedback, result, result.semantic(), FailureLayer.SEMANTIC);

      if (result.evaluationCase().kind()
              == com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind.MODEL_QUALITY
          && result.semantic().status() == Status.NOT_RUN
          && result.structural().status() == Status.PASS
          && result.invariant().status() == Status.PASS) {
        feedback.add(
            item(
                result,
                FailureLayer.HUMAN_REVIEW,
                "No semantic review was recorded for this model-quality case.",
                List.of("A qualified reviewer must rate each applicable dimension with rationale."),
                false,
                true));
      }
    }
    return List.copyOf(feedback);
  }

  private void addLayerFeedback(
      List<RepairFeedback> feedback,
      EvaluationCaseResult result,
      LayerResult layer,
      FailureLayer failureLayer) {
    if (layer.status() == Status.FAIL) {
      feedback.add(
          item(
              result,
              failureLayer,
              actualBehavior(result),
              layer.findings(),
              true,
              false));
    }
  }

  private RepairFeedback item(
      EvaluationCaseResult result,
      FailureLayer failureLayer,
      String actualBehavior,
      List<String> findings,
      boolean rerunRequired,
      boolean humanReviewRequired) {
    List<String> unmetRequirements = new ArrayList<>();
    unmetRequirements.addAll(result.evaluationCase().requirementReferences());
    unmetRequirements.addAll(result.evaluationCase().successCriteriaReferences());
    return new RepairFeedback(
        result.caseId(),
        unmetRequirements,
        failureLayer,
        result.evaluationCase().expectedBehavior(),
        actualBehavior,
        findings,
        rerunRequired,
        humanReviewRequired);
  }

  private String actualBehavior(EvaluationCaseResult result) {
    return result.actualOutput() == null
        ? "No usable agent response was recorded; see case-layer findings."
        : "See the recorded actual_output for this case.";
  }

  public record RepairFeedback(
      String caseId,
      List<String> unmetRequirements,
      FailureLayer failureLayer,
      String expectedBehavior,
      String actualBehavior,
      List<String> findings,
      boolean rerunRequired,
      boolean humanReviewRequired) {

    public RepairFeedback {
      unmetRequirements = List.copyOf(unmetRequirements);
      findings = List.copyOf(findings);
    }
  }

  public enum FailureLayer {
    OPERATIONAL,
    PROBE,
    STRUCTURAL,
    INVARIANT,
    SEMANTIC,
    HUMAN_REVIEW
  }
}
