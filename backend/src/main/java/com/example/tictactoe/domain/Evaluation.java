package com.example.tictactoe.domain;

import java.util.List;

public record Evaluation(GameStatus status, Player winner, List<Integer> winningCells) { }
