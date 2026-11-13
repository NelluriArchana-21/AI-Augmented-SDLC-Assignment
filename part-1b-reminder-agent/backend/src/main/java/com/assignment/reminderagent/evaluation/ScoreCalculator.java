package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ScoreCalculator {

  public static final BigDecimal ACCEPTANCE_THRESHOLD = new BigDecimal("90.0");
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final BigDecimal TWO = new BigDecimal("2");

  public Optional<BigDecimal> scoreCase(
      List<ScoreDimension> applicableDimensions, Map<ScoreDimension, Integer> ratings) {
    if (applicableDimensions == null || ratings == null || applicableDimensions.isEmpty()) {
      throw new IllegalArgumentException("Applicable dimensions and ratings are required");
    }

    Set<ScoreDimension> dimensions = Set.copyOf(applicableDimensions);
    if (dimensions.size() != applicableDimensions.size()) {
      throw new IllegalArgumentException("Applicable dimensions must be unique");
    }
    if (dimensions.contains(ScoreDimension.NOT_APPLICABLE)) {
      if (dimensions.size() != 1 || !ratings.isEmpty()) {
        throw new IllegalArgumentException("N/A must be the only dimension and have no ratings");
      }
      return Optional.empty();
    }
    if (!ratings.keySet().equals(dimensions)) {
      throw new IllegalArgumentException("Ratings must exactly match applicable dimensions");
    }

    int total = ratings.values().stream().mapToInt(this::validateRating).sum();
    BigDecimal denominator = TWO.multiply(BigDecimal.valueOf(dimensions.size()));
    return Optional.of(
        ONE_HUNDRED
            .multiply(BigDecimal.valueOf(total))
            .divide(denominator, 1, RoundingMode.HALF_UP));
  }

  public Optional<BigDecimal> aggregate(Collection<EvaluationCaseResult> results) {
    if (results == null) {
      throw new IllegalArgumentException("Evaluation results are required");
    }
    List<BigDecimal> scores =
        results.stream()
            .filter(result -> result.evaluationCase().kind()
                == com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind.MODEL_QUALITY)
            .map(EvaluationCaseResult::score)
            .filter(score -> score != null)
            .toList();
    if (scores.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return Optional.of(
        sum.divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP));
  }

  public boolean meetsAcceptanceThreshold(BigDecimal aggregate) {
    return aggregate != null && aggregate.compareTo(ACCEPTANCE_THRESHOLD) >= 0;
  }

  private int validateRating(Integer rating) {
    if (rating == null || rating < 0 || rating > 2) {
      throw new IllegalArgumentException("Each applicable rating must be 0, 1, or 2");
    }
    return rating;
  }
}
