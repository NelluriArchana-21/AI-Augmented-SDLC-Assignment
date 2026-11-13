package com.assignment.reminderagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reminder-agent.provider")
public class ReminderAgentProperties {

  public static final String API_KEY_HEADER = "x-goog-api-key";

  private String apiKey = "";
  private String modelId = "";

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  @Override
  public String toString() {
    return "ReminderAgentProperties[apiKey=REDACTED, modelId=REDACTED]";
  }
}
