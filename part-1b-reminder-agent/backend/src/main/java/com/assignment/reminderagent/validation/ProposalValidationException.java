package com.assignment.reminderagent.validation;

import com.assignment.reminderagent.provider.ProviderAttemptTrace;
import java.util.List;

public final class ProposalValidationException extends RuntimeException {

  private final Layer layer;
  private final List<ProviderAttemptTrace> attempts;

  public ProposalValidationException(Layer layer) {
    this(layer, List.of());
  }

  public ProposalValidationException(Layer layer, List<ProviderAttemptTrace> attempts) {
    super("Provider proposal failed " + layer.name().toLowerCase() + " validation");
    this.layer = layer;
    this.attempts = List.copyOf(attempts);
  }

  public Layer getLayer() {
    return layer;
  }

  public List<ProviderAttemptTrace> getAttempts() {
    return attempts;
  }

  public enum Layer {
    SCHEMA,
    INVARIANT
  }
}
