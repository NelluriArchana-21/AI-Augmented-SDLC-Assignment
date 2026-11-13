package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public final class EvaluationDatasetLoader {

  public static final Path DEFAULT_DATASET =
      Path.of("..", "harness", "config", "evaluation-cases.json");

  private static final List<ScoreDimension> MODEL_QUALITY_DIMENSIONS =
      List.of(
          ScoreDimension.I,
          ScoreDimension.T,
          ScoreDimension.D,
          ScoreDimension.C,
          ScoreDimension.S);

  private final ObjectMapper objectMapper;

  public EvaluationDatasetLoader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<EvaluationCase> loadDefault() {
    return load(DEFAULT_DATASET);
  }

  public List<EvaluationCase> load(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      throw new IllegalStateException("Frozen evaluation dataset is unavailable");
    }
    try {
      List<EvaluationCase> cases =
          objectMapper.readValue(Files.readString(path), new TypeReference<>() {});
      validate(cases);
      return List.copyOf(cases);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read the frozen evaluation dataset", exception);
    }
  }

  private void validate(List<EvaluationCase> cases) {
    List<String> expectedIds =
        IntStream.rangeClosed(1, 29).mapToObj(index -> "E%02d".formatted(index)).toList();
    if (cases == null || cases.size() != expectedIds.size()) {
      throw new IllegalStateException("Frozen evaluation dataset must contain E01-E29");
    }
    List<String> actualIds = cases.stream().map(EvaluationCase::id).toList();
    if (!actualIds.equals(expectedIds) || new HashSet<>(actualIds).size() != expectedIds.size()) {
      throw new IllegalStateException("Frozen evaluation dataset IDs do not match E01-E29");
    }
    for (int index = 0; index < cases.size(); index++) {
      EvaluationCase evaluationCase = cases.get(index);
      boolean modelQuality = index < 25;
      List<ScoreDimension> expectedDimensions =
          modelQuality ? MODEL_QUALITY_DIMENSIONS : List.of(ScoreDimension.NOT_APPLICABLE);
      if (evaluationCase.kind()
              != (modelQuality ? Kind.MODEL_QUALITY : Kind.ISOLATED_PROBE)
          || !evaluationCase.applicableDimensions().equals(expectedDimensions)
          || evaluationCase.requirementReferences().isEmpty()
          || evaluationCase.successCriteriaReferences().isEmpty()
          || evaluationCase.expectedBehavior().isBlank()) {
        throw new IllegalStateException(
            "Frozen evaluation dataset case is incomplete: " + evaluationCase.id());
      }
      if (!evaluationCase.requirementReferences().stream().allMatch(ref -> ref.matches("FR-\\d{3}"))
          || !evaluationCase.successCriteriaReferences().stream()
              .allMatch(ref -> ref.matches("SC-\\d{3}"))) {
        throw new IllegalStateException(
            "Frozen evaluation dataset traceability is invalid: " + evaluationCase.id());
      }
    }
  }
}
