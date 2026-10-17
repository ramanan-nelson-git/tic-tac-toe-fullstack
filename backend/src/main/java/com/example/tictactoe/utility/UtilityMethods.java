package com.example.tictactoe.utility;

public class UtilityMethods {

    public static void waitInMilli(long waitInMili) {
        long counterOne = System.currentTimeMillis();
        long counterTwo = 0L;
        long counterThree = 0L;
        while (counterThree < waitInMili) {
            counterThree = (counterTwo = System.currentTimeMillis()) - counterOne;
        }
    }
}
