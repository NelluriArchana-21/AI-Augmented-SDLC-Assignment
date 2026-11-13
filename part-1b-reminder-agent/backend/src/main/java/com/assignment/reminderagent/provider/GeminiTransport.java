package com.assignment.reminderagent.provider;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface GeminiTransport {

  Response send(URI uri, Map<String, String> headers, String body, Duration timeout)
      throws IOException, InterruptedException;

  record Response(int statusCode, String body) {
    @Override
    public String toString() {
      return "Response[statusCode=%d, bodyPresent=%s]".formatted(statusCode, body != null);
    }
  }
}
