package com.assignment.reminderagent.support;

import com.assignment.reminderagent.provider.GeminiTransport;
import java.net.URI;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class FakeProviderTransport implements GeminiTransport {

  private final Deque<Outcome> outcomes;
  private final List<Request> requests = new ArrayList<>();
  private final Function<Request, ? extends Outcome> outcomeFactory;

  public FakeProviderTransport(Collection<? extends Outcome> outcomes) {
    this.outcomes = new ArrayDeque<>(outcomes);
    this.outcomeFactory = null;
  }

  public FakeProviderTransport(Function<Request, ? extends Outcome> outcomeFactory) {
    this.outcomes = new ArrayDeque<>();
    this.outcomeFactory = Objects.requireNonNull(outcomeFactory, "outcomeFactory");
  }

  public Outcome exchange(Request request) {
    requests.add(Objects.requireNonNull(request, "request"));
    Outcome outcome =
        outcomeFactory == null ? outcomes.pollFirst() : outcomeFactory.apply(request);
    if (outcome == null) {
      throw new IllegalStateException("No fake transport outcome is configured");
    }
    return outcome;
  }

  @Override
  public GeminiTransport.Response send(
      URI uri, Map<String, String> headers, String body, Duration timeout) throws IOException {
    Outcome outcome = exchange(new Request(uri, headers, body, timeout));
    if (outcome instanceof Response response) {
      return new GeminiTransport.Response(response.statusCode(), response.body());
    }
    throw new IOException("Simulated network failure");
  }

  public List<Request> requests() {
    return List.copyOf(requests);
  }

  public sealed interface Outcome permits Response, NetworkFailure {}

  public record Response(int statusCode, String body) implements Outcome {
    @Override
    public String toString() {
      return "Response[statusCode=%d, bodyPresent=%s]".formatted(statusCode, body != null);
    }
  }

  public record NetworkFailure() implements Outcome {}

  public record Request(URI uri, Map<String, String> headers, String body, Duration timeout) {
    public Request {
      Objects.requireNonNull(uri, "uri");
      headers = Map.copyOf(headers);
      Objects.requireNonNull(timeout, "timeout");
    }

    public Request(URI uri, Map<String, String> headers, String body) {
      this(uri, headers, body, Duration.ZERO);
    }

    @Override
    public String toString() {
      return "Request[headerNames=%s, bodyPresent=%s]".formatted(headers.keySet(), body != null);
    }
  }
}
