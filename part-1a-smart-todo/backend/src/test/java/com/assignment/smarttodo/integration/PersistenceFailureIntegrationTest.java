package com.assignment.smarttodo.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PersistenceFailureIntegrationTest {

  @TempDir static Path tempDir;

  @DynamicPropertySource
  static void registerStorage(DynamicPropertyRegistry registry) {
    registry.add("task.storage.path", () -> storagePath().toString());
  }

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void clearStoredTasks() throws Exception {
    Files.deleteIfExists(storagePath());
  }

  @Test
  void persistenceReadFailureReturnsSafeServerErrorAndIsLogged() throws Exception {
    byte[] invalidContent = "{ invalid task data".getBytes();
    Files.write(storagePath(), invalidContent);

    Logger logger =
        (Logger) LoggerFactory.getLogger("com.assignment.smarttodo.exception.ApiExceptionHandler");
    ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    String responseBody;
    try {
      responseBody =
          mockMvc
              .perform(get("/api/tasks"))
              .andExpect(status().isInternalServerError())
              .andExpect(jsonPath("$.error").value("Persistence operation failed"))
              .andReturn()
              .getResponse()
              .getContentAsString();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertFalse(responseBody.contains(storagePath().toString()));
    assertArrayEquals(invalidContent, Files.readAllBytes(storagePath()));
    assertTrue(
        appender.list.stream()
            .anyMatch(
                event ->
                    event.getFormattedMessage().equals("Persistence failure")
                        && event.getThrowableProxy() != null
                        && event
                            .getThrowableProxy()
                            .getMessage()
                            .contains("Unable to read tasks from storage")));
  }

  private static Path storagePath() {
    return tempDir.resolve("tasks.json");
  }
}
