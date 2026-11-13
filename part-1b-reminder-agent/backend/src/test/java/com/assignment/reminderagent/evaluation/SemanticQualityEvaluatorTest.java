package com.assignment.reminderagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SemanticQualityEvaluatorTest {

  private static final List<ScoreDimension> DIMENSIONS =
      List.of(
          ScoreDimension.I,
          ScoreDimension.T,
          ScoreDimension.D,
          ScoreDimension.C,
          ScoreDimension.S);

  private final SemanticQualityEvaluator evaluator = new SemanticQualityEvaluator();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void retainsHumanIdentityAndRationaleForEachApplicableQualityDimension() {
    var review =
        evaluator
            .evaluate(
                modelQualityCase(),
                objectMapper.createObjectNode().put("intent", "CREATE"),
                "reviewer@example.test",
                ratings(),
                rationales())
            .orElseThrow();

    assertEquals("reviewer@example.test", review.evaluatorIdentity());
    assertEquals(ratings(), review.ratings());
    assertEquals(rationales(), review.rationales());
    assertEquals(
        SetHolder.ALL_RATINGS,
        evaluator.rubric().keySet());
  }

  @Test
  void rejectsMissingIdentityRatingsRationalesAndOutOfRangeScores() {
    var output = objectMapper.createObjectNode().put("intent", "CREATE");
    assertThrows(
        IllegalArgumentException.class,
        () -> evaluator.evaluate(modelQualityCase(), output, " ", ratings(), rationales()));
    assertThrows(
        IllegalArgumentException.class,
        () -> evaluator.evaluate(modelQualityCase(), output, "reviewer", Map.of(), rationales()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            evaluator.evaluate(
                modelQualityCase(),
                output,
                "reviewer",
                Map.of(
                    ScoreDimension.I, 3,
                    ScoreDimension.T, 2,
                    ScoreDimension.D, 2,
                    ScoreDimension.C, 2,
                    ScoreDimension.S, 2),
                rationales()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            evaluator.evaluate(
                modelQualityCase(),
                output,
                "reviewer",
                ratings(),
                Map.of(
                    ScoreDimension.I, " ",
                    ScoreDimension.T, "target",
                    ScoreDimension.D, "date",
                    ScoreDimension.C, "clarification",
                    ScoreDimension.S, "safety")));
  }

  @Test
  void doesNotScoreIsolatedProbeCases() {
    EvaluationCase probe =
        new EvaluationCase(
            "E26",
            "Malformed provider JSON",
            Kind.ISOLATED_PROBE,
            objectMapper.createObjectNode(),
            "Malformed output must be rejected.",
            List.of(ScoreDimension.NOT_APPLICABLE),
            List.of("FR-017"),
            List.of("SC-002"));

    assertTrue(
        evaluator
            .evaluate(
                probe,
                objectMapper.createObjectNode(),
                "",
                Map.of(),
                Map.of())
            .isEmpty());
  }

  private EvaluationCase modelQualityCase() {
    return new EvaluationCase(
        "E01",
        "Normal CREATE",
        Kind.MODEL_QUALITY,
        objectMapper.createObjectNode(),
        "Expect a complete CREATE proposal.",
        DIMENSIONS,
        List.of("FR-008"),
        List.of("SC-001"));
  }

  private Map<ScoreDimension, Integer> ratings() {
    return Map.of(
        ScoreDimension.I, 2,
        ScoreDimension.T, 2,
        ScoreDimension.D, 1,
        ScoreDimension.C, 2,
        ScoreDimension.S, 2);
  }

  private Map<ScoreDimension, String> rationales() {
    return Map.of(
        ScoreDimension.I, "Intent matches.",
        ScoreDimension.T, "Task matches.",
        ScoreDimension.D, "Date/time partially matches.",
        ScoreDimension.C, "Clarification behavior matches.",
        ScoreDimension.S, "No unsupported values were added.");
  }

  private static final class SetHolder {
    private static final java.util.Set<Integer> ALL_RATINGS = java.util.Set.of(0, 1, 2);
  }
}
