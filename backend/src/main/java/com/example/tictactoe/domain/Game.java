package com.example.tictactoe.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A game session. The move list is the single source of truth: board, current player,
 * status and winner are always derived from it, which keeps undo trivially correct.
 */
public class Game {
    private final UUID id = UUID.randomUUID();
    private GameMode mode;
    private final List<Move> moves = new ArrayList<>();
    /** What this game has contributed to the scoreboard (null = nothing yet). */
    private ScoreOutcome recordedOutcome;

    public Game(GameMode mode) {
        this.mode = mode;
    }

    public UUID getId() { return id; }
    public GameMode getMode() { return mode; }
    public void setMode(GameMode mode) { this.mode = mode; }
    public List<Move> getMoves() { return moves; }
    public ScoreOutcome getRecordedOutcome() { return recordedOutcome; }
    public void setRecordedOutcome(ScoreOutcome recordedOutcome) { this.recordedOutcome = recordedOutcome; }
}
