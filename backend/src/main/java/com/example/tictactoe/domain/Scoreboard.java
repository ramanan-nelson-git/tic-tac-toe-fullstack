package com.example.tictactoe.domain;

public class Scoreboard {
    private volatile int xWins;
    private volatile int oWins;
    private volatile int draws;

    public void add(ScoreOutcome outcome, int delta) {
        switch (outcome) {
            case X_WIN -> xWins += delta;
            case O_WIN -> oWins += delta;
            case DRAW -> draws += delta;
        }
    }

    public void clear() {
        xWins = 0;
        oWins = 0;
        draws = 0;
    }

    public int getXWins() { return xWins; }
    public int getOWins() { return oWins; }
    public int getDraws() { return draws; }
}
