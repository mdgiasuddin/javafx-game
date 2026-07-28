package com.example.tetrisgame;

import javafx.scene.paint.Color;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import static javafx.scene.paint.Color.BLUE;
import static javafx.scene.paint.Color.CYAN;
import static javafx.scene.paint.Color.GREEN;
import static javafx.scene.paint.Color.ORANGE;
import static javafx.scene.paint.Color.PURPLE;
import static javafx.scene.paint.Color.RED;
import static javafx.scene.paint.Color.YELLOW;

public class TetrominoUtil {
    private TetrominoUtil() {

    }

    private static final Random random = new SecureRandom();

    private static final List<int[][]> shapes = List.of(
            new int[][]{{1, 1, 1, 1}},          // I
            new int[][]{{1, 1}, {1, 1}},        // O
            new int[][]{{0, 1, 0}, {1, 1, 1}},  // T
            new int[][]{{0, 1, 1}, {1, 1, 0}},  // S
            new int[][]{{1, 1, 0}, {0, 1, 1}},  // Z
            new int[][]{{1, 0, 0}, {1, 1, 1}},  // J
            new int[][]{{0, 0, 1}, {1, 1, 1}}   // L
    );

    private static final List<Color> colors = List.of(
            CYAN, YELLOW, PURPLE, GREEN, RED, BLUE, ORANGE
    );

    public static Tetromino getRandomPiece() {
        return new Tetromino(shapes.get(random.nextInt(shapes.size())), colors.get(random.nextInt(colors.size())));
    }
}
