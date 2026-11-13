package com.assignment.reminderagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReminderAgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReminderAgentApplication.class, args);
  }
}
