package com.assignment.reminderagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EvaluationDatasetLoaderTest {

  private static final Path DATASET_PATH =
      Path.of("..", "harness", "config", "evaluation-cases.json");

  @Test
  void loadsAllTwentyNineStableCasesWithTraceabilityAndExplicitApplicability() {
    List<EvaluationCase> cases = new EvaluationDatasetLoader(new com.fasterxml.jackson.databind.ObjectMapper())
        .load(DATASET_PATH);
    List<String> expectedIds =
        IntStream.rangeClosed(1, 29).mapToObj(index -> "E%02d".formatted(index)).toList();
    List<String> actualIds = cases.stream().map(EvaluationCase::id).toList();

    assertEquals(29, cases.size());
    assertEquals(expectedIds, actualIds);
    assertEquals(29, new HashSet<>(actualIds).size());

    Set<String> categories = cases.stream().map(EvaluationCase::category).collect(Collectors.toSet());
    assertTrue(
        categories.containsAll(
            Set.of(
                "Normal CREATE",
                "Boundary leap day",
                "Missing time",
                "Ambiguous date",
                "Contradictory date",
                "Unsupported request",
                "Adversarial unsupported instruction",
                "Malformed input JSON",
                "Malformed provider JSON",
                "Provider/network failure",
                "Invalid calendar-date output",
                "Missing provider credential")));
    assertEquals(
        25, cases.stream().filter(evaluationCase -> evaluationCase.kind() == Kind.MODEL_QUALITY).count());
    assertEquals(
        4, cases.stream().filter(evaluationCase -> evaluationCase.kind() == Kind.ISOLATED_PROBE).count());

    List<ScoreDimension> semanticDimensions =
        List.of(ScoreDimension.I, ScoreDimension.T, ScoreDimension.D, ScoreDimension.C, ScoreDimension.S);
    for (int index = 0; index < cases.size(); index++) {
      EvaluationCase evaluationCase = cases.get(index);
      assertFalse(evaluationCase.expectedBehavior().isBlank(), evaluationCase.id());
      assertNotNull(evaluationCase.input(), evaluationCase.id());
      assertFalse(evaluationCase.requirementReferences().isEmpty(), evaluationCase.id());
      assertFalse(evaluationCase.successCriteriaReferences().isEmpty(), evaluationCase.id());
      assertTrue(
          evaluationCase.requirementReferences().stream().allMatch(reference -> reference.matches("FR-\\d{3}")),
          evaluationCase.id());
      assertTrue(
          evaluationCase.successCriteriaReferences().stream()
              .allMatch(reference -> reference.matches("SC-\\d{3}")),
          evaluationCase.id());

      List<ScoreDimension> expectedDimensions =
          index < 25 ? semanticDimensions : List.of(ScoreDimension.NOT_APPLICABLE);
      assertEquals(expectedDimensions, evaluationCase.applicableDimensions(), evaluationCase.id());
      assertEquals(
          evaluationCase.applicableDimensions().size(),
          new HashSet<>(evaluationCase.applicableDimensions()).size(),
          evaluationCase.id());
    }
  }
}
