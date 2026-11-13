package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationRunRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class EvidenceReporter {

  private static final Pattern SAFE_RUN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
  private static final Pattern AUTHORIZATION_VALUE =
      Pattern.compile("(?i)(\\bBearer\\s+)[A-Za-z0-9._~+/-]+=*");
  private static final Pattern NAMED_SECRET =
      Pattern.compile(
          "(?i)(\\b(?:x-goog-api-key|authorization|api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[:=]\\s*)([^\\s,;]+)");
  private static final Pattern GOOGLE_API_KEY =
      Pattern.compile("\\bAIza[0-9A-Za-z_-]{20,}\\b");

  private final ObjectMapper objectMapper;

  public EvidenceReporter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Path write(Path harnessRoot, EvaluationRunRecord run) throws IOException {
    if (harnessRoot == null || run == null) {
      throw new IllegalArgumentException("Harness root and evaluation run are required");
    }
    if (!SAFE_RUN_ID.matcher(run.runId()).matches()) {
      throw new IllegalArgumentException("runId contains unsupported path characters");
    }
    validateCaseSet(run.caseResults());

    String outcome = run.accepted() ? "passed" : "failed";
    Path outcomeDirectory = harnessRoot.resolve("logs").resolve(outcome);
    Files.createDirectories(outcomeDirectory);
    Path runDirectory = outcomeDirectory.resolve(run.runId());
    Files.createDirectory(runDirectory);

    Path casesPath = runDirectory.resolve("cases.jsonl");
    try (BufferedWriter writer =
        Files.newBufferedWriter(
            casesPath,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE)) {
      for (EvaluationCaseResult result : run.caseResults()) {
        JsonNode record = redact(objectMapper.valueToTree(result));
        writer.write(objectMapper.writeValueAsString(record));
        writer.newLine();
      }
    }

    ObjectNode summary = objectMapper.valueToTree(run);
    summary.remove("case_results");
    ArrayNode caseIds = objectMapper.createArrayNode();
    run.caseResults().forEach(result -> caseIds.add(result.caseId()));
    summary.set("case_ids", caseIds);
    Files.writeString(
        runDirectory.resolve("run-summary.json"),
        objectMapper.writeValueAsString(redact(summary)) + System.lineSeparator(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    return runDirectory;
  }

  private void validateCaseSet(List<EvaluationCaseResult> results) {
    if (results.size() != 29) {
      throw new IllegalArgumentException("Evidence must contain exactly the frozen 29 cases");
    }
    Set<String> expected = new HashSet<>();
    for (int i = 1; i <= 29; i++) {
      expected.add("E%02d".formatted(i));
    }
    Set<String> actual = new HashSet<>();
    for (EvaluationCaseResult result : results) {
      if (!actual.add(result.caseId())) {
        throw new IllegalArgumentException("Duplicate evaluation case ID: " + result.caseId());
      }
    }
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("Evidence case IDs must be exactly E01–E29");
    }
  }

  private JsonNode redact(JsonNode value) {
    if (value.isObject()) {
      ObjectNode redacted = objectMapper.createObjectNode();
      value.fields()
          .forEachRemaining(
              entry -> {
                if (isSensitiveField(entry.getKey())) {
                  redacted.put(entry.getKey(), "[REDACTED]");
                } else {
                  redacted.set(entry.getKey(), redact(entry.getValue()));
                }
              });
      return redacted;
    }
    if (value.isArray()) {
      ArrayNode redacted = objectMapper.createArrayNode();
      value.forEach(item -> redacted.add(redact(item)));
      return redacted;
    }
    if (value.isTextual()) {
      return objectMapper.getNodeFactory().textNode(redactText(value.textValue()));
    }
    return value.deepCopy();
  }

  private boolean isSensitiveField(String fieldName) {
    String normalized = fieldName.toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
    return normalized.contains("apikey")
        || normalized.contains("authorization")
        || normalized.contains("credential")
        || normalized.contains("secret")
        || normalized.contains("password")
        || normalized.contains("accesstoken");
  }

  private String redactText(String value) {
    String redacted = AUTHORIZATION_VALUE.matcher(value).replaceAll("$1[REDACTED]");
    redacted = NAMED_SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
    return GOOGLE_API_KEY.matcher(redacted).replaceAll("[REDACTED]");
  }
}
