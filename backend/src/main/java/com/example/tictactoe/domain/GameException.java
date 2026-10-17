package com.example.tictactoe.domain;

/** A rule or validation failure that maps directly to an HTTP status code. */
public class GameException extends RuntimeException {
    private final int status;

    private GameException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }

    public static GameException badRequest(String message) { return new GameException(400, message); }
    public static GameException notFound(String message) { return new GameException(404, message); }
    public static GameException conflict(String message) { return new GameException(409, message); }
}
