package com.example.tictactoe.domain;

/** A single move. {@code cell} is a flat index 0-8 (row * 3 + column). */
public record Move(Player player, int cell) { }
