package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.api.ReminderProposalService;
import com.assignment.reminderagent.api.ReminderProposalService.ProposalExecution;
import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.Kind;
import com.assignment.reminderagent.evaluation.model.EvaluationCase.ScoreDimension;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.LayerResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.ProbeOutcome;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.SemanticReview;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.evaluation.model.EvaluationRunRecord;
import com.assignment.reminderagent.evaluation.model.ProviderAttemptRecord;
import com.assignment.reminderagent.parsing.MalformedModelOutputException;
import com.assignment.reminderagent.provider.ProviderAttemptTrace;
import com.assignment.reminderagent.provider.ProviderOperationalException;
import com.assignment.reminderagent.validation.ProposalValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class EvaluationPipeline {

  private static final BigDecimal STRUCTURAL_PASS = BigDecimal.valueOf(100);
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(1);

  private final EvaluationDatasetLoader datasetLoader;
  private final ModelQualityExecutor modelQualityExecutor;
  private final OperationalProbeRunner probeRunner;
  private final SemanticQualityEvaluator semanticEvaluator;
  private final ScoreCalculator scoreCalculator;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public EvaluationPipeline(
      EvaluationDatasetLoader datasetLoader,
      ReminderProposalService proposalService,
      OperationalProbeRunner probeRunner,
      SemanticQualityEvaluator semanticEvaluator,
      ScoreCalculator scoreCalculator,
      ObjectMapper objectMapper) {
    this(
        datasetLoader,
        input -> proposalService.proposeWithAttempts(input),
        probeRunner,
        semanticEvaluator,
        scoreCalculator,
        objectMapper,
        Clock.systemUTC());
  }

  public EvaluationPipeline(
      EvaluationDatasetLoader datasetLoader,
      ModelQualityExecutor modelQualityExecutor,
      OperationalProbeRunner probeRunner,
      SemanticQualityEvaluator semanticEvaluator,
      ScoreCalculator scoreCalculator,
      ObjectMapper objectMapper,
      Clock clock) {
    this.datasetLoader = Objects.requireNonNull(datasetLoader, "datasetLoader");
    this.modelQualityExecutor =
        Objects.requireNonNull(modelQualityExecutor, "modelQualityExecutor");
    this.probeRunner = Objects.requireNonNull(probeRunner, "probeRunner");
    this.semanticEvaluator = Objects.requireNonNull(semanticEvaluator, "semanticEvaluator");
    this.scoreCalculator = Objects.requireNonNull(scoreCalculator, "scoreCalculator");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public EvaluationRunRecord evaluate(
      String runId, Map<String, ReviewInput> humanReviews) {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("runId is required");
    }
    Map<String, ReviewInput> reviews =
        humanReviews == null ? Map.of() : Map.copyOf(humanReviews);
    List<EvaluationCase> cases = datasetLoader.loadDefault();
    ensureReviewsBelongToDataset(reviews, cases);

    Instant startedAt = Instant.now(clock);
    List<EvaluationCaseResult> results = new ArrayList<>(cases.size());
    boolean modelEvaluationFailureSeen = false;
    for (EvaluationCase evaluationCase : cases) {
      if (evaluationCase.kind() == Kind.ISOLATED_PROBE) {
        results.add(probeRunner.run(evaluationCase));
      } else if (modelEvaluationFailureSeen) {
        results.add(notRunAfterModelFailure(evaluationCase));
      } else {
        EvaluationCaseResult result = evaluateModelQualityCase(evaluationCase, reviews);
        results.add(result);
        modelEvaluationFailureSeen =
            result.operationalFailureCategory() != null
                || result.structural().status() == Status.FAIL
                || result.invariant().status() == Status.FAIL;
      }
    }

    Optional<BigDecimal> aggregate = scoreCalculator.aggregate(results);
    BigDecimal structuralCompliance = structuralCompliance(results);
    int invariantFailures =
        (int)
            results.stream()
                .filter(result -> result.invariant().status() == Status.FAIL)
                .count();
    int operationalFailures =
        (int)
            results.stream()
                .filter(result -> result.evaluationCase().kind() == Kind.MODEL_QUALITY)
                .filter(result -> result.operationalFailureCategory() != null)
                .count();
    boolean complete = isComplete(results);
    boolean accepted =
        complete
            && scoreCalculator.meetsAcceptanceThreshold(aggregate.orElse(null))
            && structuralCompliance.compareTo(STRUCTURAL_PASS) == 0
            && invariantFailures == 0
            && operationalFailures == 0
            && results.stream()
                .filter(result -> result.probeOutcome() != null)
                .allMatch(result -> result.probeOutcome().passed());

    return new EvaluationRunRecord(
        runId,
        startedAt,
        Instant.now(clock),
        results,
        aggregate.orElse(null),
        ScoreCalculator.ACCEPTANCE_THRESHOLD,
        structuralCompliance,
        invariantFailures,
        operationalFailures,
        complete,
        accepted,
        null,
        List.of());
  }

  private EvaluationCaseResult evaluateModelQualityCase(
      EvaluationCase evaluationCase, Map<String, ReviewInput> reviews) {
    Instant recordedAt = Instant.now(clock);
    try {
      ProposalExecution execution =
          modelQualityExecutor.execute(serviceInput(evaluationCase.input()));
      JsonNode actualOutput = objectMapper.valueToTree(execution.proposal());
      SemanticReview review = null;
      BigDecimal score = null;
      LayerResult semanticLayer = notRun("No human semantic review supplied.");
      ReviewInput reviewInput = reviews.get(evaluationCase.id());
      if (reviewInput != null) {
        review =
            semanticEvaluator
                .evaluate(
                    evaluationCase,
                    actualOutput,
                    reviewInput.evaluatorIdentity(),
                    reviewInput.ratings(),
                    reviewInput.rationales())
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Model-quality cases must have applicable semantic dimensions"));
        score =
            scoreCalculator
                .scoreCase(evaluationCase.applicableDimensions(), review.ratings())
                .orElseThrow();
        semanticLayer = pass();
      }
      return new EvaluationCaseResult(
          evaluationCase,
          actualOutput,
          null,
          null,
          pass(),
          pass(),
          semanticLayer,
          review,
          score,
          recordedAt,
          convertAttempts(execution.attempts()),
          List.of());
    } catch (ProviderOperationalException exception) {
      return new EvaluationCaseResult(
          evaluationCase,
          null,
          exception.getCategory().name(),
          null,
          notRun("No agent response was produced."),
          notRun("No agent response was produced."),
          notRun("Operational failure; semantic scoring is prohibited."),
          null,
          null,
          recordedAt,
          convertAttempts(exception.getAttempts()),
          List.of());
    } catch (MalformedModelOutputException exception) {
      return new EvaluationCaseResult(
          evaluationCase,
          null,
          null,
          null,
          fail("Provider output was malformed."),
          notRun("Structural validation failed."),
          notRun("Malformed provider output is not a CLARIFY result."),
          null,
          null,
          recordedAt,
          convertAttempts(exception.getAttempts()),
          List.of());
    } catch (ProposalValidationException exception) {
      boolean structuralFailure = exception.getLayer() == ProposalValidationException.Layer.SCHEMA;
      return new EvaluationCaseResult(
          evaluationCase,
          null,
          null,
          null,
          structuralFailure ? fail("Provider output failed schema validation.") : pass(),
          structuralFailure ? notRun("Structural validation failed.") : fail("Invariant validation failed."),
          notRun("Semantic scoring requires a structurally and invariant-valid response."),
          null,
          null,
          recordedAt,
          convertAttempts(exception.getAttempts()),
          List.of());
    }
  }

  private EvaluationCaseResult notRunAfterModelFailure(EvaluationCase evaluationCase) {
    String finding = "Skipped after an earlier model-evaluation failure in this run.";
    return new EvaluationCaseResult(
        evaluationCase,
        null,
        null,
        null,
        notRun(finding),
        notRun(finding),
        notRun("No response was produced; semantic scoring is prohibited."),
        null,
        null,
        Instant.now(clock),
        List.of(),
        List.of());
  }

  private JsonNode serviceInput(JsonNode input) {
    return input != null && input.has("raw_body") ? null : input;
  }

  private void ensureReviewsBelongToDataset(
      Map<String, ReviewInput> reviews, List<EvaluationCase> cases) {
    java.util.Set<String> caseIds =
        cases.stream().filter(evaluationCase -> evaluationCase.kind() == Kind.MODEL_QUALITY)
            .map(EvaluationCase::id)
            .collect(java.util.stream.Collectors.toSet());
    if (!caseIds.containsAll(reviews.keySet())) {
      throw new IllegalArgumentException("Semantic review references an unknown model-quality case");
    }
  }

  private BigDecimal structuralCompliance(List<EvaluationCaseResult> results) {
    long evaluatedResponses =
        results.stream()
            .filter(result -> result.evaluationCase().kind() == Kind.MODEL_QUALITY)
            .filter(
                result ->
                    result.structural().status() == Status.PASS
                        || result.structural().status() == Status.FAIL)
            .count();
    if (evaluatedResponses == 0) {
      return ZERO;
    }
    long structurallyValid =
        results.stream()
            .filter(result -> result.evaluationCase().kind() == Kind.MODEL_QUALITY)
            .filter(result -> result.structural().status() == Status.PASS)
            .count();
    return BigDecimal.valueOf(structurallyValid)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(evaluatedResponses), 1, java.math.RoundingMode.HALF_UP);
  }

  private boolean isComplete(List<EvaluationCaseResult> results) {
    if (results.size() != 29) {
      return false;
    }
    return results.stream()
        .allMatch(
            result -> {
              if (result.evaluationCase().kind() == Kind.ISOLATED_PROBE) {
                return result.probeOutcome() != null && result.probeOutcome().passed();
              }
              return result.actualOutput() != null
                  && result.structural().status() == Status.PASS
                  && result.invariant().status() == Status.PASS
                  && result.semantic().status() == Status.PASS
                  && result.score() != null
                  && result.operationalFailureCategory() == null;
            });
  }

  private List<ProviderAttemptRecord> convertAttempts(List<ProviderAttemptTrace> attempts) {
    return attempts.stream()
        .map(
            attempt ->
                new ProviderAttemptRecord(
                    attempt.attemptNumber(),
                    attempt.category(),
                    attempt.attemptedAt(),
                    ProviderAttemptRecord.Disposition.valueOf(attempt.disposition().name())))
        .toList();
  }

  private LayerResult pass() {
    return new LayerResult(Status.PASS, List.of());
  }

  private LayerResult fail(String finding) {
    return new LayerResult(Status.FAIL, List.of(finding));
  }

  private LayerResult notRun(String finding) {
    return new LayerResult(Status.NOT_RUN, List.of(finding));
  }

  @FunctionalInterface
  public interface ModelQualityExecutor {
    ProposalExecution execute(JsonNode input);
  }

  public record ReviewInput(
      String evaluatorIdentity,
      Map<ScoreDimension, Integer> ratings,
      Map<ScoreDimension, String> rationales) {

    public ReviewInput {
      Objects.requireNonNull(evaluatorIdentity, "evaluatorIdentity");
      ratings = Map.copyOf(ratings);
      rationales = Map.copyOf(rationales);
    }
  }
}
