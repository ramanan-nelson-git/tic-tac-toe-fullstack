package com.example.tictactoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GameStatus {
    @JsonProperty("InProgress") IN_PROGRESS,
    @JsonProperty("Won") WON,
    @JsonProperty("Draw") DRAW
}
