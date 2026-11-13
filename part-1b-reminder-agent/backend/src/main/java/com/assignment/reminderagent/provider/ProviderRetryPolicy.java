package com.assignment.reminderagent.provider;

import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.assignment.reminderagent.provider.ProviderResult.OperationalFailure;
import java.time.Duration;
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
    Objects.requireNonNull(attempt, "attempt");
    long startedAt = nanoTime.getAsLong();

    for (int attemptNumber = 1; attemptNumber <= MAX_ATTEMPTS; attemptNumber++) {
      Duration remaining = remaining(startedAt);
      if (remaining.isZero() || remaining.isNegative()) {
        return new OperationalFailure(FailureCategory.TIMEOUT);
      }

      Duration timeout = remaining.compareTo(MAX_ATTEMPT_TIMEOUT) < 0
          ? remaining
          : MAX_ATTEMPT_TIMEOUT;
      ProviderResult result = Objects.requireNonNull(attempt.execute(timeout), "attempt result");
      if (!(result instanceof OperationalFailure failure)
          || !isRetryable(failure.category())
          || attemptNumber == MAX_ATTEMPTS) {
        return result;
      }

      Duration delay = retryDelayAfter(attemptNumber);
      if (remaining(startedAt).compareTo(delay) <= 0) {
        return new OperationalFailure(FailureCategory.TIMEOUT);
      }
      try {
        sleeper.sleep(delay);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return new OperationalFailure(FailureCategory.NETWORK);
      }
    }

    return new OperationalFailure(FailureCategory.TIMEOUT);
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
}
