package com.assignment.reminderagent.provider;

import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.assignment.reminderagent.provider.ProviderResult.OperationalFailure;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class ProviderRetryPolicy {

  public static final int MAX_ATTEMPTS = 3;
  public static final Duration MAX_ATTEMPT_TIMEOUT = Duration.ofSeconds(20);
  public static final Duration MAX_TOTAL_DURATION = Duration.ofSeconds(63);

  private final Sleeper sleeper;
  private final LongSupplier nanoTime;

  public ProviderRetryPolicy() {
    this(
        delay -> TimeUnit.NANOSECONDS.sleep(delay.toNanos()),
        System::nanoTime);
  }

  public ProviderRetryPolicy(Sleeper sleeper, LongSupplier nanoTime) {
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
  }

  public ProviderResult execute(Attempt attempt) {
    return executeWithAttempts(attempt).result();
  }

  public ExecutionResult executeWithAttempts(Attempt attempt) {
    Objects.requireNonNull(attempt, "attempt");
    long startedAt = nanoTime.getAsLong();
    List<ProviderAttemptTrace> attempts = new ArrayList<>();

    for (int attemptNumber = 1; attemptNumber <= MAX_ATTEMPTS; attemptNumber++) {
      Duration remaining = remaining(startedAt);
      if (remaining.isZero() || remaining.isNegative()) {
        return new ExecutionResult(new OperationalFailure(FailureCategory.TIMEOUT), attempts);
      }

      Duration timeout = remaining.compareTo(MAX_ATTEMPT_TIMEOUT) < 0
          ? remaining
          : MAX_ATTEMPT_TIMEOUT;
      ProviderResult result = Objects.requireNonNull(attempt.execute(timeout), "attempt result");
      if (!(result instanceof OperationalFailure failure)) {
        attempts.add(trace(attemptNumber, "SUCCESS", ProviderAttemptTrace.Disposition.SUCCEEDED));
        return new ExecutionResult(result, attempts);
      }
      if (!isRetryable(failure.category()) || attemptNumber == MAX_ATTEMPTS) {
        attempts.add(trace(attemptNumber, failure.category().name(),
            ProviderAttemptTrace.Disposition.TERMINAL_FAILURE));
        return new ExecutionResult(result, attempts);
      }

      Duration delay = retryDelayAfter(attemptNumber);
      if (remaining(startedAt).compareTo(delay) <= 0) {
        attempts.add(trace(attemptNumber, failure.category().name(),
            ProviderAttemptTrace.Disposition.TERMINAL_FAILURE));
        return new ExecutionResult(new OperationalFailure(FailureCategory.TIMEOUT), attempts);
      }
      attempts.add(trace(attemptNumber, failure.category().name(),
          ProviderAttemptTrace.Disposition.RETRY_SCHEDULED));
      try {
        sleeper.sleep(delay);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return new ExecutionResult(new OperationalFailure(FailureCategory.NETWORK), attempts);
      }
    }

    return new ExecutionResult(new OperationalFailure(FailureCategory.TIMEOUT), attempts);
  }

  private ProviderAttemptTrace trace(
      int number, String category, ProviderAttemptTrace.Disposition disposition) {
    return new ProviderAttemptTrace(number, category, Instant.now(), disposition);
  }

  private Duration remaining(long startedAt) {
    long elapsed = Math.max(0, nanoTime.getAsLong() - startedAt);
    return MAX_TOTAL_DURATION.minusNanos(elapsed);
  }

  private boolean isRetryable(FailureCategory category) {
    return category == FailureCategory.NETWORK
        || category == FailureCategory.TIMEOUT
        || category == FailureCategory.RATE_LIMIT
        || category == FailureCategory.SERVER;
  }

  private Duration retryDelayAfter(int attemptNumber) {
    return attemptNumber == 1 ? Duration.ofSeconds(1) : Duration.ofSeconds(2);
  }

  @FunctionalInterface
  public interface Attempt {
    ProviderResult execute(Duration timeout);
  }

  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration delay) throws InterruptedException;
  }

  public record ExecutionResult(ProviderResult result, List<ProviderAttemptTrace> attempts) {
    public ExecutionResult {
      Objects.requireNonNull(result, "result");
      attempts = List.copyOf(attempts);
    }
  }
}
