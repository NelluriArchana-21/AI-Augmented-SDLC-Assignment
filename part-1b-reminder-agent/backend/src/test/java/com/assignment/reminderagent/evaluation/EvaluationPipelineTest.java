package com.assignment.reminderagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.api.ReminderProposalService.ProposalExecution;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.parsing.MalformedModelOutputException;
import com.assignment.reminderagent.provider.ProviderOperationalException;
import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.assignment.reminderagent.validation.ProposalValidationException;
import com.assignment.reminderagent.validation.ProposalValidationException.Layer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationPipelineTest {

  private static final List<ScoreDimension> DIMENSIONS =
      List.of(
          ScoreDimension.I,
          ScoreDimension.T,
          ScoreDimension.D,
          ScoreDimension.C,
          ScoreDimension.S);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final EvaluationDatasetLoader datasetLoader =
      new EvaluationDatasetLoader(objectMapper);
  private final OperationalProbeRunner probeRunner =
      new OperationalProbeRunner(
          objectMapper,
          new com.assignment.reminderagent.parsing.ModelOutputParser(objectMapper),
          new com.assignment.reminderagent.validation.ReminderProposalSchemaValidator(objectMapper));
  private final SemanticQualityEvaluator semanticEvaluator = new SemanticQualityEvaluator();
  private final ScoreCalculator scoreCalculator = new ScoreCalculator();

  @Test
  void createsOneResultPerCaseWithSeparateValidationLayersAndAllHardGates() {
    EvaluationPipeline pipeline = pipeline(input -> successfulExecution());
    Map<String, EvaluationPipeline.ReviewInput> reviews = reviews(20, 5);

    var run = pipeline.evaluate("pipeline-test-90-boundary", reviews);

    assertEquals(29, run.caseResults().size());
    assertEquals(25, run.caseResults().stream()
        .filter(result -> result.evaluationCase().kind()
            == com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind.MODEL_QUALITY)
        .count());
    assertEquals(4, run.caseResults().stream()
        .filter(result -> result.evaluationCase().kind()
            == com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind.ISOLATED_PROBE)
        .count());
    assertEquals(new java.math.BigDecimal("90.0"), run.semanticAggregate());
    assertEquals(new java.math.BigDecimal("100.0"), run.structuralCompliancePercent());
    assertEquals(0, run.invariantFailureCount());
    assertEquals(0, run.unresolvedOperationalFailureCount());
    assertTrue(run.complete());
    assertTrue(run.accepted());

    var modelResult = run.caseResults().getFirst();
    assertEquals(Status.PASS, modelResult.structural().status());
    assertEquals(Status.PASS, modelResult.invariant().status());
    assertEquals(Status.PASS, modelResult.semantic().status());
    assertEquals("synthetic-reviewer", modelResult.semanticReview().evaluatorIdentity());
  }

  @Test
  void providerFailureBlocksAcceptanceAndNeverCreatesClarifyOrSemanticScore() {
    EvaluationPipeline pipeline =
        pipeline(
            input -> {
              throw new ProviderOperationalException(FailureCategory.NETWORK);
            });

    var run = pipeline.evaluate("pipeline-provider-failure", Map.of());
    var first = run.caseResults().getFirst();

    assertEquals(29, run.caseResults().size());
    assertEquals("NETWORK", first.operationalFailureCategory());
    assertNull(first.actualOutput());
    assertNull(first.score());
    assertEquals(Status.NOT_RUN, first.semantic().status());
    assertEquals(1, run.unresolvedOperationalFailureCount());
    assertFalse(run.complete());
    assertFalse(run.accepted());
  }

  @Test
  void structuralAndInvariantFailuresRemainDistinctAndBlockTheRun() {
    EvaluationPipeline structuralFailure =
        pipeline(input -> {
          throw new MalformedModelOutputException();
        });
    var structuralRun = structuralFailure.evaluate("pipeline-structural-failure", Map.of());
    assertEquals(Status.FAIL, structuralRun.caseResults().getFirst().structural().status());
    assertEquals(Status.NOT_RUN, structuralRun.caseResults().getFirst().invariant().status());
    assertFalse(structuralRun.accepted());

    EvaluationPipeline invariantFailure =
        pipeline(input -> {
          throw new ProposalValidationException(Layer.INVARIANT);
        });
    var invariantRun = invariantFailure.evaluate("pipeline-invariant-failure", Map.of());
    assertEquals(Status.PASS, invariantRun.caseResults().getFirst().structural().status());
    assertEquals(Status.FAIL, invariantRun.caseResults().getFirst().invariant().status());
    assertEquals(1, invariantRun.invariantFailureCount());
    assertFalse(invariantRun.accepted());
  }

  private EvaluationPipeline pipeline(EvaluationPipeline.ModelQualityExecutor executor) {
    return new EvaluationPipeline(
        datasetLoader,
        executor,
        probeRunner,
        semanticEvaluator,
        scoreCalculator,
        objectMapper,
        Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
  }

  private ProposalExecution successfulExecution() {
    return new ProposalExecution(
        new ActionProposal(
            ReminderIntent.CREATE,
            "Known task",
            "2026-01-20",
            "09:00",
            false,
            null),
        List.of());
  }

  private Map<String, EvaluationPipeline.ReviewInput> reviews(int fullScoreCount, int partialCount) {
    var allTwo = Map.of(
        ScoreDimension.I, 2,
        ScoreDimension.T, 2,
        ScoreDimension.D, 2,
        ScoreDimension.C, 2,
        ScoreDimension.S, 2);
    var allOne = Map.of(
        ScoreDimension.I, 1,
        ScoreDimension.T, 1,
        ScoreDimension.D, 1,
        ScoreDimension.C, 1,
        ScoreDimension.S, 1);
    var rationales = Map.of(
        ScoreDimension.I, "Reviewed intent.",
        ScoreDimension.T, "Reviewed task/target.",
        ScoreDimension.D, "Reviewed date/time.",
        ScoreDimension.C, "Reviewed clarification.",
        ScoreDimension.S, "Reviewed non-invention/safety.");
    Map<String, EvaluationPipeline.ReviewInput> result = new java.util.HashMap<>();
    for (int i = 1; i <= fullScoreCount; i++) {
      result.put("E%02d".formatted(i), new EvaluationPipeline.ReviewInput("synthetic-reviewer", allTwo, rationales));
    }
    for (int i = fullScoreCount + 1; i <= fullScoreCount + partialCount; i++) {
      result.put("E%02d".formatted(i), new EvaluationPipeline.ReviewInput("synthetic-reviewer", allOne, rationales));
    }
    return result;
  }
}
