package com.assignment.reminderagent.evaluation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

public record EvaluationCase(
    @JsonProperty("id") String id,
    @JsonProperty("category") String category,
    @JsonProperty("kind") Kind kind,
    @JsonProperty("input") JsonNode input,
    @JsonProperty("expected_behavior") String expectedBehavior,
    @JsonProperty("applicable_dimensions") List<ScoreDimension> applicableDimensions,
    @JsonProperty("requirement_references") List<String> requirementReferences,
    @JsonProperty("success_criteria_references") List<String> successCriteriaReferences) {

  public EvaluationCase {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(expectedBehavior, "expectedBehavior");
    applicableDimensions = List.copyOf(applicableDimensions);
    requirementReferences = List.copyOf(requirementReferences);
    successCriteriaReferences = List.copyOf(successCriteriaReferences);
  }

  public enum Kind {
    @JsonProperty("model_quality")
    MODEL_QUALITY,
    @JsonProperty("isolated_probe")
    ISOLATED_PROBE
  }

  public enum ScoreDimension {
    I,
    T,
    D,
    C,
    S,
    @JsonProperty("N/A")
    NOT_APPLICABLE
  }
}
