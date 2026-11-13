package com.assignment.reminderagent.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.assignment.reminderagent.provider.ProviderResult.OperationalFailure;
import com.assignment.reminderagent.provider.ProviderResult.Success;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ProviderRetryPolicyTest {

  @Test
  void retriesTransientFailuresAtMostThreeTimesWithinThe63SecondBudget() {
    AtomicLong elapsedNanos = new AtomicLong();
    List<Duration> timeouts = new ArrayList<>();
    List<Duration> delays = new ArrayList<>();
    var policy =
        new ProviderRetryPolicy(
            delay -> {
              delays.add(delay);
              elapsedNanos.addAndGet(delay.toNanos());
            },
            elapsedNanos::get);

    ProviderResult result =
        policy.execute(
            timeout -> {
              timeouts.add(timeout);
              elapsedNanos.addAndGet(timeout.toNanos());
              return new OperationalFailure(FailureCategory.SERVER);
            });

    assertEquals(new OperationalFailure(FailureCategory.SERVER), result);
    assertEquals(
        List.of(Duration.ofSeconds(20), Duration.ofSeconds(20), Duration.ofSeconds(20)), timeouts);
    assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), delays);
    assertEquals(Duration.ofSeconds(63).toNanos(), elapsedNanos.get());
  }

  @Test
  void doesNotRetryPermanentFailuresOrMalformedProviderOutput() {
    var policy = new ProviderRetryPolicy();
    var attempts = new java.util.concurrent.atomic.AtomicInteger();

    ProviderResult authentication =
        policy.execute(
            timeout -> {
              attempts.incrementAndGet();
              return new OperationalFailure(FailureCategory.AUTHENTICATION);
            });
    assertEquals(new OperationalFailure(FailureCategory.AUTHENTICATION), authentication);
    assertEquals(1, attempts.get());

    attempts.set(0);
    ProviderResult malformedCompletion =
        policy.execute(
            timeout -> {
              attempts.incrementAndGet();
              return new Success("{truncated");
            });
    assertEquals(new Success("{truncated"), malformedCompletion);
    assertEquals(1, attempts.get());
  }

  @Test
  void retriesOnlyTransientNetworkRateLimitTimeoutAndServerCategories() {
    for (FailureCategory category :
        List.of(
            FailureCategory.NETWORK,
            FailureCategory.RATE_LIMIT,
            FailureCategory.TIMEOUT,
            FailureCategory.SERVER)) {
      var attempts = new java.util.concurrent.atomic.AtomicInteger();
      List<Duration> delays = new ArrayList<>();
      var policy = new ProviderRetryPolicy(delays::add, () -> 0L);

      ProviderResult result =
          policy.execute(
              timeout ->
                  attempts.incrementAndGet() == 1
                      ? new OperationalFailure(category)
                      : new Success("{}"));

      assertEquals(new Success("{}"), result, category.toString());
      assertEquals(2, attempts.get(), category.toString());
      assertEquals(List.of(Duration.ofSeconds(1)), delays, category.toString());
    }
  }
}
