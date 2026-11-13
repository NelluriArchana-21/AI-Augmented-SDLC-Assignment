package com.assignment.reminderagent.evaluation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EvaluationRunRecord(
    @JsonProperty("run_id") String runId,
    @JsonProperty("started_at") Instant startedAt,
    @JsonProperty("completed_at") Instant completedAt,
    @JsonProperty("case_results") List<EvaluationCaseResult> caseResults,
    @JsonProperty("semantic_aggregate") BigDecimal semanticAggregate,
    @JsonProperty("semantic_threshold") BigDecimal semanticThreshold,
    @JsonProperty("structural_compliance_percent") BigDecimal structuralCompliancePercent,
    @JsonProperty("invariant_failure_count") int invariantFailureCount,
    @JsonProperty("unresolved_operational_failure_count") int unresolvedOperationalFailureCount,
    @JsonProperty("complete") boolean complete,
    @JsonProperty("accepted") boolean accepted,
    @JsonProperty("reevaluation_of_run_id") String reevaluationOfRunId,
    @JsonProperty("repair_links") List<String> repairLinks) {

  public EvaluationRunRecord {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(startedAt, "startedAt");
    caseResults = List.copyOf(caseResults);
    Objects.requireNonNull(semanticThreshold, "semanticThreshold");
    repairLinks = List.copyOf(repairLinks);
  }
}
