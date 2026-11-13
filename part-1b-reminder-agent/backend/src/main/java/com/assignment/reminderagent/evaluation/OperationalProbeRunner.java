package com.assignment.reminderagent.evaluation;

import com.assignment.reminderagent.config.ReminderAgentProperties;
import com.assignment.reminderagent.evaluation.model.EvaluationCase;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.LayerResult;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.ProbeOutcome;
import com.assignment.reminderagent.evaluation.model.EvaluationCaseResult.Status;
import com.assignment.reminderagent.evaluation.model.ProviderAttemptRecord;
import com.assignment.reminderagent.parsing.MalformedModelOutputException;
import com.assignment.reminderagent.parsing.ModelOutputParser;
import com.assignment.reminderagent.provider.GeminiProviderAdapter;
import com.assignment.reminderagent.provider.ProviderAttemptTrace;
import com.assignment.reminderagent.provider.ProviderRetryPolicy;
import com.assignment.reminderagent.provider.ProviderResult;
import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.assignment.reminderagent.validation.ProposalValidationException;
import com.assignment.reminderagent.validation.ReminderProposalSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public final class OperationalProbeRunner {

  private static final Instant PROBE_RECORDED_AT = Instant.parse("2026-01-15T00:00:00Z");

  private final ObjectMapper objectMapper;
  private final ModelOutputParser outputParser;
  private final ReminderProposalSchemaValidator schemaValidator;

  public OperationalProbeRunner(
      ObjectMapper objectMapper,
      ModelOutputParser outputParser,
      ReminderProposalSchemaValidator schemaValidator) {
    this.objectMapper = objectMapper;
    this.outputParser = outputParser;
    this.schemaValidator = schemaValidator;
  }

  public EvaluationCaseResult run(EvaluationCase evaluationCase) {
    if (evaluationCase == null
        || evaluationCase.kind() != EvaluationCase.Kind.ISOLATED_PROBE) {
      throw new IllegalArgumentException("Only isolated probe cases can be run here");
    }

    return switch (evaluationCase.id()) {
      case "E26" -> malformedProviderOutput(evaluationCase);
      case "E27" -> providerFailure(evaluationCase);
      case "E28" -> invalidCalendarDate(evaluationCase);
      case "E29" -> missingCredential(evaluationCase);
      default -> throw new IllegalArgumentException("Unsupported isolated probe case");
    };
  }

  private EvaluationCaseResult malformedProviderOutput(EvaluationCase evaluationCase) {
    String completion = evaluationCase.input().path("provider_completion").asText(null);
    if (completion == null) {
      throw new IllegalArgumentException("E26 requires provider completion content");
    }
    boolean rejected = false;
    try {
      outputParser.parse(completion);
    } catch (MalformedModelOutputException exception) {
      rejected = true;
    }
    return result(
        evaluationCase,
        rejected,
        rejected ? "Malformed provider output was rejected structurally." : "Malformed output was accepted.",
        null,
        null,
        List.of());
  }

  private EvaluationCaseResult providerFailure(EvaluationCase evaluationCase) {
    ProviderRetryPolicy policy = new ProviderRetryPolicy(delay -> {}, () -> 0L);
    ProviderRetryPolicy.ExecutionResult execution =
        policy.executeWithAttempts(
            timeout -> new ProviderResult.OperationalFailure(FailureCategory.NETWORK));
    boolean surfaced =
        execution.result() instanceof ProviderResult.OperationalFailure failure
            && failure.category() == FailureCategory.NETWORK
            && execution.attempts().size() == ProviderRetryPolicy.MAX_ATTEMPTS;
    List<ProviderAttemptRecord> attempts = execution.attempts().stream().map(this::convert).toList();
    return result(
        evaluationCase,
        surfaced,
        surfaced
            ? "Injected network failure remained an operational failure after bounded retries."
            : "Injected network failure did not remain an operational failure.",
        FailureCategory.NETWORK.name(),
        null,
        attempts);
  }

  private EvaluationCaseResult invalidCalendarDate(EvaluationCase evaluationCase) {
    JsonNode candidate = evaluationCase.input().get("candidate_proposal");
    if (candidate == null) {
      throw new IllegalArgumentException("E28 requires a candidate proposal");
    }
    boolean rejected = false;
    try {
      schemaValidator.validate(candidate);
    } catch (ProposalValidationException exception) {
      if (exception.getLayer() != ProposalValidationException.Layer.SCHEMA) {
        throw exception;
      }
      rejected = true;
    }
    return result(
        evaluationCase,
        rejected,
        rejected ? "Invalid Gregorian date was rejected by structural validation." : "Invalid date was accepted.",
        null,
        null,
        List.of());
  }

  private EvaluationCaseResult missingCredential(EvaluationCase evaluationCase) {
    ReminderAgentProperties properties = new ReminderAgentProperties();
    AtomicBoolean transportCalled = new AtomicBoolean();
    GeminiProviderAdapter adapter =
        new GeminiProviderAdapter(
            properties,
            objectMapper,
            (uri, headers, body, timeout) -> {
              transportCalled.set(true);
              throw new IOException("Missing-credential probe must not make a network request");
            },
            new ProviderRetryPolicy(delay -> {}, () -> 0L));
    ProviderResult providerResult = adapter.extract("trusted", "synthetic probe");
    boolean redactedConfigurationFailure =
        providerResult instanceof ProviderResult.OperationalFailure failure
            && failure.category() == FailureCategory.CONFIGURATION
            && !transportCalled.get();
    return result(
        evaluationCase,
        redactedConfigurationFailure,
        redactedConfigurationFailure
            ? "Missing credentials failed before transport invocation."
            : "Missing credentials did not fail safely.",
        FailureCategory.CONFIGURATION.name(),
        null,
        List.of());
  }

  private EvaluationCaseResult result(
      EvaluationCase evaluationCase,
      boolean passed,
      String finding,
      String operationalFailure,
      JsonNode actualOutput,
      List<ProviderAttemptRecord> attempts) {
    LayerResult notApplicable = new LayerResult(Status.NOT_APPLICABLE, List.of());
    return new EvaluationCaseResult(
        evaluationCase,
        actualOutput,
        operationalFailure,
        new ProbeOutcome(passed, List.of(finding)),
        notApplicable,
        notApplicable,
        notApplicable,
        null,
        null,
        PROBE_RECORDED_AT,
        attempts,
        List.of());
  }

  private ProviderAttemptRecord convert(ProviderAttemptTrace trace) {
    return new ProviderAttemptRecord(
        trace.attemptNumber(),
        trace.category(),
        trace.attemptedAt(),
        ProviderAttemptRecord.Disposition.valueOf(trace.disposition().name()));
  }
}
