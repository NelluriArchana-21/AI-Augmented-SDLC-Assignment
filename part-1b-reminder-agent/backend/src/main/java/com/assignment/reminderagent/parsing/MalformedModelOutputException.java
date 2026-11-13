package com.assignment.reminderagent.parsing;

public final class MalformedModelOutputException extends RuntimeException {

  public MalformedModelOutputException() {
    super("Provider output is not one valid reminder proposal JSON object");
  }
}
