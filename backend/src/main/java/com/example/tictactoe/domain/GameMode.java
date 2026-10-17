package com.example.tictactoe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GameMode {
    @JsonProperty("TwoPlayer") TWO_PLAYER,
    @JsonProperty("VsComputer") VS_COMPUTER
}
