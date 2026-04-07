package com.vivek.web;

import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for {@link GlobalExceptionHandler}.
 *
 * <p>A small stub controller exposes endpoints that throw each exception type
 * so the handler's JSON shape and HTTP status codes can be verified.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.StubController.class)
class GlobalExceptionHandlerTest {

  @Autowired MockMvc mvc;

  /** Stub controller that deliberately throws exceptions for each handler. */
  @RestController
  static class StubController {
    @GetMapping("/test/connection-not-found")
    void throwConnectionNotFound() throws ConnectionDetailNotFound {
      throw new ConnectionDetailNotFound("group/db not found");
    }

    @GetMapping("/test/illegal-argument")
    void throwIllegalArgument() {
      throw new IllegalArgumentException("bad input");
    }

    @GetMapping("/test/sql-exception")
    void throwSqlException() throws SQLException {
      throw new SQLException("db error");
    }

    @GetMapping("/test/generic-exception")
    void throwGeneric() throws Exception {
      throw new RuntimeException("unexpected failure");
    }
  }

  @Test
  @WithMockUser
  void connectionNotFound_returns404WithCode() throws Exception {
    mvc.perform(get("/test/connection-not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CONNECTION_NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").exists());
  }

  @Test
  @WithMockUser
  void illegalArgument_returns400WithValidationError() throws Exception {
    mvc.perform(get("/test/illegal-argument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("bad input"));
  }

  @Test
  @WithMockUser
  void sqlException_returns500WithDatabaseError() throws Exception {
    mvc.perform(get("/test/sql-exception"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("DATABASE_ERROR"));
  }

  @Test
  @WithMockUser
  void genericException_returns500WithInternalError() throws Exception {
    mvc.perform(get("/test/generic-exception"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
  }

  @Test
  @WithMockUser
  void errorResponse_includesRequestIdField() throws Exception {
    mvc.perform(get("/test/illegal-argument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.requestId").exists());
  }
}
