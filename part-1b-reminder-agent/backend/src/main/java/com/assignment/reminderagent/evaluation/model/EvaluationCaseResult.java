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
    @JsonProperty("case_id") String caseId,
    @JsonProperty("actual_output") JsonNode actualOutput,
    @JsonProperty("operational_failure_category") String operationalFailureCategory,
    @JsonProperty("structural") LayerResult structural,
    @JsonProperty("invariant") LayerResult invariant,
    @JsonProperty("semantic") LayerResult semantic,
    @JsonProperty("applicable_dimensions") List<ScoreDimension> applicableDimensions,
    @JsonProperty("ratings") Map<ScoreDimension, Integer> ratings,
    @JsonProperty("score") BigDecimal score,
    @JsonProperty("recorded_at") Instant recordedAt,
    @JsonProperty("provider_attempts") List<ProviderAttemptRecord> providerAttempts,
    @JsonProperty("repair_links") List<String> repairLinks) {

  public EvaluationCaseResult {
    Objects.requireNonNull(caseId, "caseId");
    Objects.requireNonNull(structural, "structural");
    Objects.requireNonNull(invariant, "invariant");
    Objects.requireNonNull(semantic, "semantic");
    applicableDimensions = List.copyOf(applicableDimensions);
    ratings = Map.copyOf(ratings);
    Objects.requireNonNull(recordedAt, "recordedAt");
    providerAttempts = List.copyOf(providerAttempts);
    repairLinks = List.copyOf(repairLinks);
  }

  public record LayerResult(
      @JsonProperty("status") Status status, @JsonProperty("findings") List<String> findings) {

    public LayerResult {
      Objects.requireNonNull(status, "status");
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
