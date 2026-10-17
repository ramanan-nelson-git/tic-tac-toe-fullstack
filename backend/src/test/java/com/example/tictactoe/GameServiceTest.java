package com.example.tictactoe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tictactoe.api.Dtos.GameStateDto;
import com.example.tictactoe.api.Dtos.MoveDto;
import com.example.tictactoe.api.Dtos.ScoreboardDto;
import com.example.tictactoe.domain.GameException;
import com.example.tictactoe.domain.GameMode;
import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.service.GameService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameServiceTest {
    private GameService svc;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        svc = new GameService(registry);
    }

    private GameStateDto play(UUID id, Player player, int cell) {
        return svc.playMove(id, player, cell / 3, cell % 3);
    }

    private UUID newGame(GameMode mode) {
        return svc.create(mode).id();
    }

    private UUID newGame() {
        return newGame(GameMode.TWO_PLAYER);
    }

    /** X wins the top row: X0 O3 X1 O4 X2. */
    private void playXTopRowWin(UUID id) {
        play(id, Player.X, 0);
        play(id, Player.O, 3);
        play(id, Player.X, 1);
        play(id, Player.O, 4);
        play(id, Player.X, 2);
    }

    private static ScoreboardDto score(int x, int o, int d) {
        return new ScoreboardDto(x, o, d);
    }

    // ---- basic moves and turns ----

    @Test
    void newGameStartsEmptyWithXToMove() {
        GameStateDto state = svc.create(GameMode.TWO_PLAYER);

        assertTrue(Arrays.stream(state.board()).allMatch(c -> c == null));
        assertEquals(Player.X, state.currentPlayer());
        assertEquals(GameStatus.IN_PROGRESS, state.status());
        assertTrue(state.moves().isEmpty());
    }

    @Test
    void validMoveIsRecordedInTheBoardAndHistory() {
        UUID id = newGame();

        GameStateDto state = play(id, Player.X, 0);

        assertEquals(Player.X, state.board()[0]);
        assertEquals(List.of(new MoveDto(1, Player.X, 0, 0)), state.moves());
    }

    @Test
    void turnsAlternateAfterEveryValidMove() {
        UUID id = newGame();

        assertEquals(Player.O, play(id, Player.X, 0).currentPlayer());
        GameStateDto state = play(id, Player.O, 4);
        assertEquals(Player.X, state.currentPlayer());
        assertEquals(new MoveDto(2, Player.O, 1, 1), state.moves().get(1));
    }

    @Test
    void occupiedCellIsRejectedAndTurnDoesNotChange() {
        UUID id = newGame();
        play(id, Player.X, 0);

        GameException ex = assertThrows(GameException.class, () -> play(id, Player.O, 0));

        assertEquals(409, ex.getStatus());
        GameStateDto state = svc.get(id);
        assertEquals(Player.O, state.currentPlayer());
        assertEquals(1, state.moves().size());
    }

    @Test
    void playingOutOfTurnIsRejectedAndTurnDoesNotChange() {
        UUID id = newGame();

        GameException ex = assertThrows(GameException.class, () -> play(id, Player.O, 0));

        assertEquals(409, ex.getStatus());
        assertEquals(Player.X, svc.get(id).currentPlayer());
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,3", "3,3"})
    void moveOutsideTheBoardIsABadRequest(int row, int column) {
        UUID id = newGame();

        GameException ex = assertThrows(GameException.class, () -> svc.playMove(id, Player.X, row, column));

        assertEquals(400, ex.getStatus());
        assertEquals(Player.X, svc.get(id).currentPlayer());
    }

    @Test
    void unknownGameIsNotFound() {
        GameException ex = assertThrows(GameException.class, () -> svc.get(UUID.randomUUID()));
        assertEquals(404, ex.getStatus());
    }

    // ---- wins and draws ----

    @Test
    void rowWinIsDetectedScoredOnceAndLocksTheGame() {
        UUID id = newGame();
        playXTopRowWin(id);

        GameStateDto state = svc.get(id);
        assertEquals(GameStatus.WON, state.status());
        assertEquals(Player.X, state.winner());
        assertEquals(List.of(0, 1, 2), state.winningCells());
        assertNull(state.currentPlayer());
        assertEquals(score(1, 0, 0), state.scoreboard());

        // Move after game completion is rejected and does not touch the scoreboard.
        GameException ex = assertThrows(GameException.class, () -> play(id, Player.O, 8));
        assertEquals(409, ex.getStatus());
        assertEquals(score(1, 0, 0), svc.getScoreboard());
    }

    @Test
    void columnWinIsDetected() {
        UUID id = newGame();
        play(id, Player.X, 0);
        play(id, Player.O, 1);
        play(id, Player.X, 3);
        play(id, Player.O, 2);
        GameStateDto state = play(id, Player.X, 6);

        assertEquals(GameStatus.WON, state.status());
        assertEquals(Player.X, state.winner());
        assertEquals(List.of(0, 3, 6), state.winningCells());
    }

    @Test
    void diagonalWinIsDetectedForO() {
        UUID id = newGame();
        play(id, Player.X, 1);
        play(id, Player.O, 2);
        play(id, Player.X, 3);
        play(id, Player.O, 4);
        play(id, Player.X, 5);
        GameStateDto state = play(id, Player.O, 6);

        assertEquals(GameStatus.WON, state.status());
        assertEquals(Player.O, state.winner());
        assertEquals(List.of(2, 4, 6), state.winningCells());
        assertEquals(score(0, 1, 0), state.scoreboard());
    }

    @Test
    void drawIsDetectedAndScored() {
        UUID id = newGame();
        // X O X / X O O / O X X
        int[] cells = {0, 1, 2, 4, 3, 5, 7, 6, 8};
        GameStateDto state = null;
        for (int i = 0; i < cells.length; i++) {
            state = play(id, i % 2 == 0 ? Player.X : Player.O, cells[i]);
        }

        assertEquals(GameStatus.DRAW, state.status());
        assertNull(state.winner());
        assertTrue(state.winningCells().isEmpty());
        assertEquals(score(0, 0, 1), state.scoreboard());

        GameException ex = assertThrows(GameException.class, () -> play(id, Player.X, 0));
        assertEquals(409, ex.getStatus());
    }

    // ---- undo ----

    @Test
    void undoInTwoPlayerModeRemovesOnlyTheLastMove() {
        UUID id = newGame();
        play(id, Player.X, 0);
        play(id, Player.O, 4);

        GameStateDto state = svc.undo(id);

        assertEquals(1, state.moves().size());
        assertNull(state.board()[4]);
        assertEquals(Player.O, state.currentPlayer());
    }

    @Test
    void undoWithNoMovesIsRejected() {
        UUID id = newGame();
        GameException ex = assertThrows(GameException.class, () -> svc.undo(id));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void undoingAWinReopensTheGameAndRevertsTheScoreboard() {
        UUID id = newGame();
        playXTopRowWin(id);

        GameStateDto state = svc.undo(id);

        assertEquals(GameStatus.IN_PROGRESS, state.status());
        assertEquals(Player.X, state.currentPlayer());
        assertTrue(state.winningCells().isEmpty());
        assertNull(state.winner());
        assertEquals(score(0, 0, 0), state.scoreboard());

        // Replaying the winning move counts the win again, exactly once.
        state = play(id, Player.X, 2);
        assertEquals(score(1, 0, 0), state.scoreboard());
    }

    @Test
    void undoingADrawRevertsTheDrawCount() {
        UUID id = newGame();
        int[] cells = {0, 1, 2, 4, 3, 5, 7, 6, 8};
        for (int i = 0; i < cells.length; i++) {
            play(id, i % 2 == 0 ? Player.X : Player.O, cells[i]);
        }

        GameStateDto state = svc.undo(id);

        assertEquals(GameStatus.IN_PROGRESS, state.status());
        assertEquals(score(0, 0, 0), state.scoreboard());
    }

    // ---- reset ----

    @Test
    void resetGameClearsTheBoardButKeepsTheScoreboard() {
        UUID id = newGame();
        playXTopRowWin(id);

        GameStateDto state = svc.reset(id, null);

        assertTrue(Arrays.stream(state.board()).allMatch(c -> c == null));
        assertTrue(state.moves().isEmpty());
        assertEquals(GameStatus.IN_PROGRESS, state.status());
        assertNull(state.winner());
        assertEquals(Player.X, state.currentPlayer());
        assertEquals(score(1, 0, 0), state.scoreboard());
    }

    @Test
    void undoAfterResetCannotTakeBackAnEarlierResult() {
        UUID id = newGame();
        playXTopRowWin(id);
        svc.reset(id, null);

        assertThrows(GameException.class, () -> svc.undo(id));
        assertEquals(score(1, 0, 0), svc.getScoreboard());
    }

    @Test
    void resetGameCanSwitchTheMode() {
        UUID id = newGame();
        assertEquals(GameMode.VS_COMPUTER, svc.reset(id, GameMode.VS_COMPUTER).mode());
    }

    @Test
    void scoreboardCanBeResetSeparately() {
        UUID id = newGame();
        playXTopRowWin(id);

        assertEquals(score(0, 0, 0), svc.resetScoreboard());

        // The finished game can still be undone without driving a counter negative.
        assertEquals(score(0, 0, 0), svc.undo(id).scoreboard());
    }

    // ---- computer mode ----

    @Test
    void computerRepliesAfterTheHumanMove() {
        UUID id = newGame(GameMode.VS_COMPUTER);

        GameStateDto state = play(id, Player.X, 0); // human takes a corner, computer takes the centre

        assertEquals(2, state.moves().size());
        assertEquals(Player.O, state.moves().get(1).player());
        assertEquals(Player.O, state.board()[4]);
        assertEquals(Player.X, state.currentPlayer());
    }

    @Test
    void humanCannotPlayOInComputerMode() {
        UUID id = newGame(GameMode.VS_COMPUTER);
        GameException ex = assertThrows(GameException.class, () -> play(id, Player.O, 0));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void undoInComputerModeRemovesTheComputerMoveAndThePreviousHumanMove() {
        UUID id = newGame(GameMode.VS_COMPUTER);
        play(id, Player.X, 0); // + computer reply
        play(id, Player.X, 8); // + computer reply
        assertEquals(4, svc.get(id).moves().size());

        GameStateDto state = svc.undo(id);
        assertEquals(2, state.moves().size());
        assertEquals(Player.X, state.currentPlayer());

        state = svc.undo(id);
        assertTrue(state.moves().isEmpty());
        assertEquals(Player.X, state.currentPlayer());
    }

    @Test
    void computerNeverMovesAfterTheGameEndsAndUndoThenRestoresTheHumansTurn() {
        // The human always plays the first free cell. Whatever the result, the game must end and the
        // computer must never move after that.
        UUID id = newGame(GameMode.VS_COMPUTER);
        GameStateDto state = svc.get(id);

        while (state.status() == GameStatus.IN_PROGRESS) {
            int cell = firstFree(state.board());
            state = play(id, Player.X, cell);
        }

        int movesAtEnd = state.moves().size();
        assertThrows(GameException.class, () -> play(id, Player.X, 0));
        assertEquals(movesAtEnd, svc.get(id).moves().size());

        // If the human's move ended the game (odd count) undo removes just that move;
        // if the computer won (even count) undo removes the pair. Either way it is X's turn.
        GameStateDto after = svc.undo(id);
        assertEquals(movesAtEnd % 2 == 1 ? movesAtEnd - 1 : movesAtEnd - 2, after.moves().size());
        assertEquals(Player.X, after.currentPlayer());
        assertEquals(GameStatus.IN_PROGRESS, after.status());
    }

    // ---- metrics ----

    @Test
    void metricsCountMovesAndGamesAndMirrorTheScoreboard() {
        UUID id = newGame();
        playXTopRowWin(id); // X0 O3 X1 O4 X2

        assertEquals(1.0, registry.get("tictactoe.games.created").tag("mode", "two_player").counter().count());
        assertEquals(3.0, registry.get("tictactoe.moves.played").tag("player", "X").counter().count());
        assertEquals(2.0, registry.get("tictactoe.moves.played").tag("player", "O").counter().count());
        assertEquals(1.0, registry.get("tictactoe.games.completed").tag("outcome", "x_win").counter().count());
        assertEquals(1.0, registry.get("tictactoe.scoreboard").tag("result", "x_win").gauge().value());
        assertEquals(1.0, registry.get("tictactoe.games.active").gauge().value());
    }

    @Test
    void scoreboardGaugeDropsBackWhenAResultIsUndone() {
        UUID id = newGame();
        playXTopRowWin(id);

        svc.undo(id);

        assertEquals(0.0, registry.get("tictactoe.scoreboard").tag("result", "x_win").gauge().value());
        assertEquals(1.0, registry.get("tictactoe.undos").counter().count());
    }

    private static int firstFree(Player[] board) {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == null) return i;
        }
        throw new IllegalStateException("No free cell");
    }
}
