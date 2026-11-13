package com.assignment.reminderagent.evaluation.model;

import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EvaluationCaseResult(
    @JsonProperty("case") EvaluationCase evaluationCase,
    @JsonProperty("actual_output") JsonNode actualOutput,
    @JsonProperty("operational_failure_category") String operationalFailureCategory,
    @JsonProperty("probe_outcome") ProbeOutcome probeOutcome,
    @JsonProperty("structural") LayerResult structural,
    @JsonProperty("invariant") LayerResult invariant,
    @JsonProperty("semantic") LayerResult semantic,
    @JsonProperty("semantic_review") SemanticReview semanticReview,
    @JsonProperty("score") BigDecimal score,
    @JsonProperty("recorded_at") Instant recordedAt,
    @JsonProperty("provider_attempts") List<ProviderAttemptRecord> providerAttempts,
    @JsonProperty("repair_links") List<String> repairLinks) {

  public EvaluationCaseResult {
    Objects.requireNonNull(evaluationCase, "evaluationCase");
    Objects.requireNonNull(structural, "structural");
    Objects.requireNonNull(invariant, "invariant");
    Objects.requireNonNull(semantic, "semantic");
    Objects.requireNonNull(recordedAt, "recordedAt");
    providerAttempts = List.copyOf(providerAttempts);
    repairLinks = List.copyOf(repairLinks);
    if (operationalFailureCategory != null && actualOutput != null) {
      throw new IllegalArgumentException("Operational failures cannot contain agent output");
    }
    if (evaluationCase.kind() == EvaluationCase.Kind.ISOLATED_PROBE
        && (actualOutput != null || score != null || semanticReview != null)) {
      throw new IllegalArgumentException("Isolated probes cannot be agent responses or scored");
    }
  }

  @JsonProperty("case_id")
  public String caseId() {
    return evaluationCase.id();
  }

  public record LayerResult(
      @JsonProperty("status") Status status, @JsonProperty("findings") List<String> findings) {

    public LayerResult {
      Objects.requireNonNull(status, "status");
      findings = List.copyOf(findings);
    }
  }

  public record SemanticReview(
      @JsonProperty("evaluator_identity") String evaluatorIdentity,
      @JsonProperty("ratings") Map<ScoreDimension, Integer> ratings,
      @JsonProperty("rationales") Map<ScoreDimension, String> rationales) {

    public SemanticReview {
      Objects.requireNonNull(evaluatorIdentity, "evaluatorIdentity");
      ratings = Map.copyOf(ratings);
      rationales = Map.copyOf(rationales);
    }
  }

  public record ProbeOutcome(
      @JsonProperty("passed") boolean passed, @JsonProperty("findings") List<String> findings) {

    public ProbeOutcome {
      findings = List.copyOf(findings);
    }
  }

  public enum Status {
    PASS,
    FAIL,
    NOT_RUN,
    NOT_APPLICABLE
  }
}
