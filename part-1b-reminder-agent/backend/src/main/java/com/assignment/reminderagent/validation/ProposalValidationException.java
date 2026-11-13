package com.assignment.reminderagent.validation;

public final class ProposalValidationException extends RuntimeException {

  private final Layer layer;

  public ProposalValidationException(Layer layer) {
    super("Provider proposal failed " + layer.name().toLowerCase() + " validation");
    this.layer = layer;
  }

  public Layer getLayer() {
    return layer;
  }

  public enum Layer {
    SCHEMA,
    INVARIANT
  }
}
