package com.example.tictactoe;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP-level checks: JSON shape, enum values and status codes. */
@SpringBootTest
@AutoConfigureMockMvc
class GameApiTest {

    @Autowired
    private MockMvc mvc;

    private String createGame(String mode) throws Exception {
        String body = mvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"" + mode + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value(mode))
                .andExpect(jsonPath("$.status").value("InProgress"))
                .andExpect(jsonPath("$.currentPlayer").value("X"))
                .andExpect(jsonPath("$.board", hasSize(9)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions move(String id, String json) throws Exception {
        return mvc.perform(post("/api/games/" + id + "/moves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void createThenPlayAMoveReturnsUpdatedState() throws Exception {
        String id = createGame("TwoPlayer");

        move(id, "{\"player\":\"X\",\"row\":1,\"column\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.board[4]").value("X"))
                .andExpect(jsonPath("$.currentPlayer").value("O"))
                .andExpect(jsonPath("$.moves[0].moveNumber").value(1))
                .andExpect(jsonPath("$.moves[0].row").value(1));
    }

    @Test
    void occupiedCellReturns409WithAnErrorMessage() throws Exception {
        String id = createGame("TwoPlayer");
        move(id, "{\"player\":\"X\",\"row\":0,\"column\":0}").andExpect(status().isOk());

        move(id, "{\"player\":\"O\",\"row\":0,\"column\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error", not(emptyString())));
    }

    @Test
    void outOfRangeMoveReturns400() throws Exception {
        String id = createGame("TwoPlayer");
        move(id, "{\"player\":\"X\",\"row\":3,\"column\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void missingRowReturns400() throws Exception {
        String id = createGame("TwoPlayer");
        move(id, "{\"player\":\"X\",\"column\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void invalidPlayerReturns400() throws Exception {
        String id = createGame("TwoPlayer");
        move(id, "{\"player\":\"Z\",\"row\":0,\"column\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void unknownGameReturns404() throws Exception {
        mvc.perform(get("/api/games/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void undoWithNothingToUndoReturns409() throws Exception {
        String id = createGame("TwoPlayer");
        mvc.perform(post("/api/games/" + id + "/undo")).andExpect(status().isConflict());
    }

    @Test
    void scoreboardEndpointReturnsCounts() throws Exception {
        mvc.perform(get("/api/scoreboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xWins").isNumber())
                .andExpect(jsonPath("$.oWins").isNumber())
                .andExpect(jsonPath("$.draws").isNumber());
    }
}
