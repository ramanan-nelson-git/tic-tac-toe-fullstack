package com.example.tictactoe;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Actuator and Prometheus endpoints that the monitoring setup depends on. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability // metrics export is switched off in tests unless this is present
class ActuatorTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusEndpointExposesGameMetrics() throws Exception {
        mvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TwoPlayer\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tictactoe_games_total")))
                .andExpect(content().string(containsString("tictactoe_scoreboard")))
                .andExpect(content().string(containsString("tictactoe_games_active")))
                .andExpect(content().string(containsString("http_server_requests_seconds")));
    }
}
