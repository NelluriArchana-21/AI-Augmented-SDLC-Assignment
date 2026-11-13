package com.assignment.reminderagent.support;

import com.assignment.reminderagent.model.ReminderRequest;
import com.assignment.reminderagent.provider.ProviderResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

public final class FakeReminderExtractionProvider {

  private final Queue<ProviderResult> results;
  private final List<ReminderRequest> requests = new ArrayList<>();

  public FakeReminderExtractionProvider(Collection<? extends ProviderResult> results) {
    this.results = new ArrayDeque<>(results);
  }

  public ProviderResult extract(ReminderRequest request) {
    requests.add(Objects.requireNonNull(request, "request"));
    ProviderResult result = results.poll();
    if (result == null) {
      throw new IllegalStateException("No fake provider result is configured");
    }
    return result;
  }

  public List<ReminderRequest> requests() {
    return List.copyOf(requests);
  }
}
