package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.config.ReminderAgentProperties;
import com.assignment.reminderagent.evaluation.EvaluationPipeline.ReviewInput;
import com.assignment.reminderagent.evaluation.model.EvaluationRunRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public final class EvaluationApplicationRunner implements ApplicationRunner {

  private final EvaluationPipeline pipeline;
  private final EvidenceReporter evidenceReporter;
  private final ReminderAgentProperties providerProperties;
  private final ObjectMapper objectMapper;
  private final String runId;
  private final Path harnessRoot;
  private final String reviewsFile;

  public EvaluationApplicationRunner(
      EvaluationPipeline pipeline,
      EvidenceReporter evidenceReporter,
      ReminderAgentProperties providerProperties,
      ObjectMapper objectMapper,
      @Value("${evaluation.run-id:}") String runId,
      @Value("${evaluation.harness-root:../harness}") String harnessRoot,
      @Value("${evaluation.reviews-file:}") String reviewsFile) {
    this.pipeline = pipeline;
    this.evidenceReporter = evidenceReporter;
    this.providerProperties = providerProperties;
    this.objectMapper = objectMapper;
    this.runId = runId;
    this.harnessRoot = Path.of(harnessRoot);
    this.reviewsFile = reviewsFile;
  }

  @Override
  public void run(ApplicationArguments arguments) throws IOException {
    if (runId == null || runId.isBlank()) {
      return;
    }
    if (isBlank(providerProperties.getApiKey()) || isBlank(providerProperties.getModelId())) {
      throw new IllegalStateException(
          "Evaluation requires GEMINI_API_KEY and REMINDER_AGENT_MODEL configuration");
    }

    Map<String, ReviewInput> reviews = readReviews();
    EvaluationRunRecord run = pipeline.evaluate(runId, reviews);
    Path evidenceDirectory = evidenceReporter.write(harnessRoot, run);
    if (!run.accepted()) {
      throw new IllegalStateException(
          "Evaluation did not meet all acceptance gates; evidence written to "
              + evidenceDirectory);
    }
  }

  private Map<String, ReviewInput> readReviews() throws IOException {
    if (reviewsFile == null || reviewsFile.isBlank()) {
      return Map.of();
    }
    Path path = Path.of(reviewsFile);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Configured semantic review file does not exist");
    }
    return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
