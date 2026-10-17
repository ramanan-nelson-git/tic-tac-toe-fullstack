package com.example.tictactoe.api;

import com.example.tictactoe.api.Dtos.CreateGameRequest;
import com.example.tictactoe.api.Dtos.GameStateDto;
import com.example.tictactoe.api.Dtos.MoveRequest;
import com.example.tictactoe.api.Dtos.ResetGameRequest;
import com.example.tictactoe.api.Dtos.ScoreboardDto;
import com.example.tictactoe.domain.GameException;
import com.example.tictactoe.domain.GameMode;
import com.example.tictactoe.service.GameService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameController {
    private final GameService games;

    public GameController(GameService games) {
        this.games = games;
    }

    @PostMapping("/games")
    public ResponseEntity<GameStateDto> create(@RequestBody(required = false) CreateGameRequest request) {
        GameMode mode = request != null && request.mode() != null ? request.mode() : GameMode.TWO_PLAYER;
        GameStateDto state = games.create(mode);
        return ResponseEntity.created(URI.create("/api/games/" + state.id())).body(state);
    }

    @GetMapping("/games/{id}")
    public GameStateDto get(@PathVariable UUID id) {
        return games.get(id);
    }

    @PostMapping("/games/{id}/moves")
    public GameStateDto move(@PathVariable UUID id, @RequestBody MoveRequest request) {
        if (request.player() == null) {
            throw GameException.badRequest("Player must be \"X\" or \"O\".");
        }
        if (request.row() == null || request.column() == null) {
            throw GameException.badRequest("Row and column are required.");
        }
        return games.playMove(id, request.player(), request.row(), request.column());
    }

    @PostMapping("/games/{id}/undo")
    public GameStateDto undo(@PathVariable UUID id) {
        return games.undo(id);
    }

    @PostMapping("/games/{id}/reset")
    public GameStateDto reset(@PathVariable UUID id, @RequestBody(required = false) ResetGameRequest request) {
        return games.reset(id, request == null ? null : request.mode());
    }

    @GetMapping("/scoreboard")
    public ScoreboardDto scoreboard() {
        return games.getScoreboard();
    }

    @PostMapping("/scoreboard/reset")
    public ScoreboardDto resetScoreboard() {
        return games.resetScoreboard();
    }
}
