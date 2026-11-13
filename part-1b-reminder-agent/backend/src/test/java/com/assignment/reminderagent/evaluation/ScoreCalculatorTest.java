package com.assignment.reminderagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.LayerResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.evaluation.model.ProviderAttemptRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

  private static final List<ScoreDimension> FIVE_DIMENSIONS =
      List.of(
          ScoreDimension.I,
          ScoreDimension.T,
          ScoreDimension.D,
          ScoreDimension.C,
          ScoreDimension.S);

  private final ScoreCalculator calculator = new ScoreCalculator();

  @Test
  void calculatesAllFiveDimensionsOnTheZeroToTwoScale() {
    assertEquals(
        new BigDecimal("0.0"),
        calculator.scoreCase(FIVE_DIMENSIONS, ratings(0, 0, 0, 0, 0)).orElseThrow());
    assertEquals(
        new BigDecimal("50.0"),
        calculator.scoreCase(FIVE_DIMENSIONS, ratings(2, 1, 0, 2, 0)).orElseThrow());
    assertEquals(
        new BigDecimal("100.0"),
        calculator.scoreCase(FIVE_DIMENSIONS, ratings(2, 2, 2, 2, 2)).orElseThrow());
  }

  @Test
  void excludesNotApplicableDimensionsAndRejectsInvalidOrIncompleteRatings() {
    assertEquals(
        new BigDecimal("50.0"),
        calculator
            .scoreCase(
                List.of(ScoreDimension.I, ScoreDimension.T),
                Map.of(ScoreDimension.I, 2, ScoreDimension.T, 0))
            .orElseThrow());
    assertTrue(
        calculator
            .scoreCase(List.of(ScoreDimension.NOT_APPLICABLE), Map.of())
            .isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            calculator.scoreCase(
                List.of(ScoreDimension.I, ScoreDimension.NOT_APPLICABLE),
                Map.of(ScoreDimension.I, 2)));
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.scoreCase(List.of(ScoreDimension.I), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.scoreCase(List.of(ScoreDimension.I), Map.of(ScoreDimension.I, 3)));
  }

  @Test
  void averagesScoredModelCasesUnweightedAndRoundsToOneDecimal() {
    EvaluationCaseResult scoreZero = result("E01", Kind.MODEL_QUALITY, "0.0");
    EvaluationCaseResult scoreZeroAgain = result("E02", Kind.MODEL_QUALITY, "0.0");
    EvaluationCaseResult scoreTen = result("E03", Kind.MODEL_QUALITY, "10.0");
    EvaluationCaseResult probe = result("E26", Kind.ISOLATED_PROBE, null);

    assertEquals(
        new BigDecimal("3.3"),
        calculator.aggregate(List.of(scoreZero, scoreZeroAgain, scoreTen, probe)).orElseThrow());
    assertTrue(calculator.meetsAcceptanceThreshold(new BigDecimal("90.0")));
    assertTrue(calculator.meetsAcceptanceThreshold(new BigDecimal("90.1")));
    assertFalse(calculator.meetsAcceptanceThreshold(new BigDecimal("89.9")));
    assertFalse(calculator.meetsAcceptanceThreshold(null));
    assertTrue(calculator.aggregate(List.of(probe)).isEmpty());
  }

  private Map<ScoreDimension, Integer> ratings(
      int intent, int target, int dateTime, int clarification, int safety) {
    return Map.of(
        ScoreDimension.I, intent,
        ScoreDimension.T, target,
        ScoreDimension.D, dateTime,
        ScoreDimension.C, clarification,
        ScoreDimension.S, safety);
  }

  private EvaluationCaseResult result( String id, Kind kind, String scoreText) {
    List<ScoreDimension> dimensions =
        kind == Kind.MODEL_QUALITY ? FIVE_DIMENSIONS : List.of(ScoreDimension.NOT_APPLICABLE);
    EvaluationCase evaluationCase =
        new EvaluationCase(
            id,
            "score-test",
            kind,
            new ObjectMapper().createObjectNode(),
            "Synthetic expected behavior",
            dimensions,
            List.of("FR-019"),
            List.of("SC-005"));
    var notRun = new LayerResult(Status.NOT_RUN, List.of());
    return new EvaluationCaseResult(
        evaluationCase,
        null,
        null,
        kind == Kind.ISOLATED_PROBE
            ? new EvaluationCaseResult.ProbeOutcome(true, List.of())
            : null,
        notRun,
        notRun,
        notRun,
        null,
        scoreText == null ? null : new BigDecimal(scoreText),
        Instant.parse("2026-11-13T00:00:00Z"),
        List.<ProviderAttemptRecord>of(),
        List.of());
  }
}
