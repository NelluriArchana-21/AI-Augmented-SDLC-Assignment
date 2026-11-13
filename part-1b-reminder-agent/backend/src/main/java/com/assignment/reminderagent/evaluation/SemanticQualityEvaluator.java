package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.SemanticReview;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SemanticQualityEvaluator {

  private static final Map<Integer, String> RATING_RUBRIC =
      Map.of(
          0, "Incorrect, absent where required, or unsafe; materially conflicts with expected behavior.",
          1, "Partially correct, ambiguous, or incomplete without a decisive safety violation.",
          2, "Fully matches the case expectation and is safe, grounded, and appropriately clarified.");

  public Optional<SemanticReview> evaluate(
      EvaluationCase evaluationCase,
      JsonNode actualOutput,
      String evaluatorIdentity,
      Map<ScoreDimension, Integer> ratings,
      Map<ScoreDimension, String> rationales) {
    if (evaluationCase == null || actualOutput == null || !actualOutput.isObject()) {
      throw new IllegalArgumentException("A case and actual agent response are required");
    }
    if (ratings == null || rationales == null) {
      throw new IllegalArgumentException("Ratings and rationales are required");
    }

    List<ScoreDimension> applicable = evaluationCase.applicableDimensions();
    if (applicable.equals(List.of(ScoreDimension.NOT_APPLICABLE))) {
      if (!ratings.isEmpty() || !rationales.isEmpty()) {
        throw new IllegalArgumentException("N/A cases cannot receive semantic ratings");
      }
      return Optional.empty();
    }
    if (evaluatorIdentity == null || evaluatorIdentity.isBlank()) {
      throw new IllegalArgumentException("Evaluator identity is required");
    }
    Set<ScoreDimension> dimensions = Set.copyOf(applicable);
    if (!ratings.keySet().equals(dimensions) || !rationales.keySet().equals(dimensions)) {
      throw new IllegalArgumentException("Every applicable dimension needs a rating and rationale");
    }
    for (ScoreDimension dimension : dimensions) {
      Integer rating = ratings.get(dimension);
      String rationale = rationales.get(dimension);
      if (rating == null || rating < 0 || rating > 2) {
        throw new IllegalArgumentException("Ratings must be 0, 1, or 2");
      }
      if (rationale == null || rationale.isBlank()) {
        throw new IllegalArgumentException("Every rating needs a nonblank rationale");
      }
    }
    return Optional.of(new SemanticReview(evaluatorIdentity, ratings, rationales));
  }

  public Map<Integer, String> rubric() {
    return RATING_RUBRIC;
  }
}
