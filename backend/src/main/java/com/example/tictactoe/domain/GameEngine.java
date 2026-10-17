package com.example.tictactoe.domain;

import com.example.tictactoe.utility.UtilityMethods;
import jdk.jshell.execution.Util;

import java.util.List;

/** Pure game rules: no state, no I/O, easy to unit test. */
public final class GameEngine {
    public static final int SIZE = 3;
    public static final int CELL_COUNT = SIZE * SIZE;

    private static final int[][] LINES = {
        {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // rows
        {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // columns
        {0, 4, 8}, {2, 4, 6}             // diagonals
    };

    private static final int[] CORNERS = {0, 2, 6, 8};

    private GameEngine() { }

    public static Player opponent(Player player) {
        return player == Player.X ? Player.O : Player.X;
    }

    /** Builds a 9-cell board (row-major); null means empty. */
    public static Player[] buildBoard(List<Move> moves) {
        Player[] board = new Player[CELL_COUNT];
        for (Move move : moves) {
            board[move.cell()] = move.player();
        }
        return board;
    }

    public static Evaluation evaluate(Player[] board) {
        for (int[] line : LINES) {
            Player first = board[line[0]];
            if (first != null && board[line[1]] == first && board[line[2]] == first) {
                return new Evaluation(GameStatus.WON, first, List.of(line[0], line[1], line[2]));
            }
        }
        for (Player cell : board) {
            if (cell == null) {
                return new Evaluation(GameStatus.IN_PROGRESS, null, List.of());
            }
        }
        return new Evaluation(GameStatus.DRAW, null, List.of());
    }

    /** Computer priority: win, block the opponent's win, centre, corner, any free cell. */
    public static int chooseComputerMove(Player[] board, Player computer) {
        Integer win = findCompletingCell(board, computer);
        if (win != null) return win;
        Integer block = findCompletingCell(board, opponent(computer));
        if (block != null) return block;

        if (board[4] == null) return 4;

        for (int corner : CORNERS) {
            if (board[corner] == null) return corner;
        }
        for (int i = 0; i < CELL_COUNT; i++) {
            if (board[i] == null) return i;
        }
        throw new IllegalStateException("The board is full; there is no move to make.");
    }

    /** Returns the empty cell that would complete a line for the player, or null if there is none. */
    private static Integer findCompletingCell(Player[] board, Player player) {
        for (int[] line : LINES) {
            int owned = 0;
            int emptyCount = 0;
            int emptyCell = -1;
            for (int cell : line) {
                if (board[cell] == player) {
                    owned++;
                } else if (board[cell] == null) {
                    emptyCount++;
                    emptyCell = cell;
                }
            }
            if (owned == 2 && emptyCount == 1) return emptyCell;
        }
        return null;
    }
}
