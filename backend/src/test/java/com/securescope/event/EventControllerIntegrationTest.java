package com.securescope.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("securescope")
            .withUsername("securescope")
            .withPassword("securescope");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/events — 유효한 요청이면 201 과 저장된 이벤트를 반환한다")
    void createEvent_validRequest_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "eventType", "LOGIN_FAIL",
                "sourceIp", "192.168.1.100",
                "userId", "admin",
                "occurredAt", Instant.now().toString()
        );

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventType").value("LOGIN_FAIL"))
                .andExpect(jsonPath("$.data.sourceIp").value("192.168.1.100"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/events — eventType 누락 시 400 을 반환한다")
    void createEvent_missingEventType_returns400() throws Exception {
        Map<String, Object> body = Map.of("sourceIp", "1.2.3.4");

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/events — 저장된 이벤트 목록을 반환한다")
    void listEvents_returnsPagedEvents() throws Exception {
        // 이벤트 1개 먼저 저장
        Map<String, Object> body = Map.of(
                "eventType", "LOGIN_SUCCESS",
                "sourceIp", "10.0.0.1"
        );
        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));

        mockMvc.perform(get("/api/events").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
