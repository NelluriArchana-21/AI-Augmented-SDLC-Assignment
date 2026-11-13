package com.assignment.reminderagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.LayerResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.ProbeOutcome;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.evaluation.model.EvaluationRunRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceReporterTest {

  private static final Instant RECORDED_AT = Instant.parse("2026-11-13T10:00:00Z");
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void writesOneJsonlRecordPerCaseAndKeepsInitialAndRerunSeparate(@TempDir Path tempDir)
      throws Exception {
    EvidenceReporter reporter = new EvidenceReporter(objectMapper);
    EvaluationRunRecord initial = run("initial-run", null, false);
    EvaluationRunRecord rerun = run("rerun-1", "initial-run", true);

    Path initialDirectory = reporter.write(tempDir, initial);
    Path rerunDirectory = reporter.write(tempDir, rerun);

    assertTrue(initialDirectory.endsWith(Path.of("logs", "failed", "initial-run")));
    assertTrue(rerunDirectory.endsWith(Path.of("logs", "passed", "rerun-1")));
    assertTrue(Files.isRegularFile(initialDirectory.resolve("cases.jsonl")));
    assertTrue(Files.isRegularFile(initialDirectory.resolve("run-summary.json")));
    assertTrue(Files.isRegularFile(rerunDirectory.resolve("cases.jsonl")));
    List<String> lines = Files.readAllLines(rerunDirectory.resolve("cases.jsonl"));
    assertEquals(29, lines.size());

    List<JsonNode> caseRecords =
        lines.stream()
            .map(
                line -> {
                  try {
                    return objectMapper.readTree(line);
                  } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                  }
                })
            .toList();
    Set<String> caseIds =
        caseRecords.stream().map(node -> node.path("case_id").asText()).collect(Collectors.toSet());
    assertEquals(29, caseIds.size());
    assertTrue(caseIds.contains("E01"));
    JsonNode firstRecord =
        caseRecords.stream()
            .filter(record -> record.path("case_id").asText().equals("E01"))
            .findFirst()
            .orElseThrow();
    assertTrue(firstRecord.has("structural"));
    assertTrue(firstRecord.has("invariant"));
    assertTrue(firstRecord.has("semantic"));
    assertTrue(firstRecord.path("case").path("applicable_dimensions").isArray());
    assertTrue(firstRecord.has("recorded_at"));

    JsonNode summary =
        objectMapper.readTree(Files.readString(rerunDirectory.resolve("run-summary.json")));
    assertEquals(new BigDecimal("90.0"), summary.path("semantic_threshold").decimalValue());
    assertEquals("initial-run", summary.path("reevaluation_of_run_id").asText());
    assertEquals(29, summary.path("case_ids").size());
    assertTrue(summary.path("repair_links").isArray());

    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () -> reporter.write(tempDir, rerun));
  }

  @Test
  void redactsCredentialFieldsAndHeaderValuesFromEvidence(@TempDir Path tempDir)
      throws Exception {
    EvidenceReporter reporter = new EvidenceReporter(objectMapper);
    Path directory = reporter.write(tempDir, run("redaction-run", null, false));
    String evidence =
        Files.readString(directory.resolve("cases.jsonl"))
            + Files.readString(directory.resolve("run-summary.json"));

    assertFalse(evidence.contains("canary-api-key-value"));
    assertFalse(evidence.contains("canary-bearer-value"));
    assertTrue(evidence.contains("[REDACTED]"));
  }

  private EvaluationRunRecord run(String runId, String rerunOf, boolean accepted)
      throws Exception {
    List<EvaluationCase> dataset = new EvaluationDatasetLoader(objectMapper).loadDefault();
    List<EvaluationCaseResult> results = new ArrayList<>(dataset.size());
    for (EvaluationCase evaluationCase : dataset) {
      EvaluationCase recordedCase = evaluationCase;
      if (runId.equals("redaction-run") && evaluationCase.id().equals("E01")) {
        JsonNode secretInput =
            objectMapper.readTree(
                """
                {
                  "raw_body": "Authorization: Bearer canary-bearer-value",
                  "headers": {"x-goog-api-key": "canary-api-key-value"}
                }
                """);
        recordedCase =
            new EvaluationCase(
                evaluationCase.id(),
                evaluationCase.category(),
                evaluationCase.kind(),
                secretInput,
                evaluationCase.expectedBehavior(),
                evaluationCase.applicableDimensions(),
                evaluationCase.requirementReferences(),
                evaluationCase.successCriteriaReferences());
      }
      LayerResult notRun = new LayerResult(Status.NOT_RUN, List.of("Not executed in reporter fixture."));
      LayerResult notApplicable = new LayerResult(Status.NOT_APPLICABLE, List.of());
      results.add(
          new EvaluationCaseResult(
              recordedCase,
              null,
              null,
              recordedCase.kind() == Kind.ISOLATED_PROBE
                  ? new ProbeOutcome(accepted, List.of("Fixture probe outcome."))
                  : null,
              recordedCase.kind() == Kind.ISOLATED_PROBE ? notApplicable : notRun,
              recordedCase.kind() == Kind.ISOLATED_PROBE ? notApplicable : notRun,
              recordedCase.kind() == Kind.ISOLATED_PROBE ? notApplicable : notRun,
              null,
              null,
              RECORDED_AT,
              List.of(),
              List.of()));
    }
    return new EvaluationRunRecord(
        runId,
        RECORDED_AT,
        RECORDED_AT,
        results,
        null,
        ScoreCalculator.ACCEPTANCE_THRESHOLD,
        BigDecimal.ZERO.setScale(1),
        0,
        0,
        accepted,
        accepted,
        rerunOf,
        List.of());
  }
}
