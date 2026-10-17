package com.example.tictactoe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.tictactoe.domain.Evaluation;
import com.example.tictactoe.domain.GameEngine;
import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameEngineTest {
    private static final Player X = Player.X;
    private static final Player O = Player.O;
    private static final Player E = null; // empty cell shorthand

    @ParameterizedTest(name = "line {0}-{1}-{2}")
    @CsvSource({
        "0,1,2", "3,4,5", "6,7,8", // rows
        "0,3,6", "1,4,7", "2,5,8", // columns
        "0,4,8", "2,4,6"           // diagonals
    })
    void detectsEveryWinningLine(int a, int b, int c) {
        Player[] board = new Player[9];
        board[a] = X;
        board[b] = X;
        board[c] = X;

        Evaluation result = GameEngine.evaluate(board);

        assertEquals(GameStatus.WON, result.status());
        assertEquals(X, result.winner());
        assertEquals(List.of(a, b, c), result.winningCells());
    }

    @Test
    void fullBoardWithoutALineIsADraw() {
        Player[] board = {X, O, X, X, O, O, O, X, X};

        Evaluation result = GameEngine.evaluate(board);

        assertEquals(GameStatus.DRAW, result.status());
        assertNull(result.winner());
    }

    @Test
    void partiallyFilledBoardIsInProgress() {
        Player[] board = {X, O, E, E, E, E, E, E, E};
        assertEquals(GameStatus.IN_PROGRESS, GameEngine.evaluate(board).status());
    }

    @Test
    void computerWinsWhenItCanEvenIfItCouldAlsoBlock() {
        // O can win at 5; X also threatens at 2. Winning takes priority.
        Player[] board = {X, X, E, O, O, E, E, E, E};
        assertEquals(5, GameEngine.chooseComputerMove(board, O));
    }

    @Test
    void computerBlocksWhenOpponentCanWinNext() {
        Player[] board = {X, X, E, E, O, E, E, E, E};
        assertEquals(2, GameEngine.chooseComputerMove(board, O));
    }

    @Test
    void computerTakesCentreWhenFree() {
        Player[] board = {X, E, E, E, E, E, E, E, E};
        assertEquals(4, GameEngine.chooseComputerMove(board, O));
    }

    @Test
    void computerTakesACornerWhenCentreIsTaken() {
        Player[] board = {E, E, E, E, X, E, E, E, E};
        assertEquals(0, GameEngine.chooseComputerMove(board, O));
    }

    @Test
    void computerSkipsTakenCornersAndPicksTheNextFreeOne() {
        Player[] board = {X, E, E, E, O, E, E, E, E};
        assertEquals(2, GameEngine.chooseComputerMove(board, O));
    }

    @Test
    void computerTakesAnyFreeCellAsALastResort() {
        // Centre and all four corners are taken and no line can be completed or blocked,
        // so only edge cells remain; the first free one (index 1) is chosen.
        Player[] board = {X, E, O, E, O, E, O, E, X};
        assertEquals(1, GameEngine.chooseComputerMove(board, O));
    }
}
