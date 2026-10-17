package com.example.tictactoe.api;

import com.example.tictactoe.domain.GameMode;
import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import java.util.List;
import java.util.UUID;

/** Request and response contracts. Request fields are nullable on the wire; the API validates them. */
public final class Dtos {
    private Dtos() { }

    public record CreateGameRequest(GameMode mode) { }

    public record ResetGameRequest(GameMode mode) { }

    public record MoveRequest(Player player, Integer row, Integer column) { }

    public record MoveDto(int moveNumber, Player player, int row, int column) { }

    public record ScoreboardDto(int xWins, int oWins, int draws) { }

    public record ErrorResponse(int status, String error) { }

    /**
     * @param board         9 cells, row-major; null = empty
     * @param currentPlayer null once the game is over
     * @param winningCells  flat cell indexes, empty unless status is Won
     */
    public record GameStateDto(
            UUID id,
            Player[] board,
            Player currentPlayer,
            GameMode mode,
            GameStatus status,
            Player winner,
            List<Integer> winningCells,
            List<MoveDto> moves,
            ScoreboardDto scoreboard) { }
}
