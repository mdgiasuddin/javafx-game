package com.example.javagame.tetris;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import static javafx.scene.input.KeyCode.P;
import static javafx.scene.input.KeyCode.R;
import static javafx.scene.paint.Color.BLACK;
import static javafx.scene.paint.Color.WHITE;
import static javafx.scene.paint.Color.YELLOW;
import static javafx.scene.text.FontWeight.BOLD;

public class TetrisGame extends Application {

    // Grid Dimensions
    private static final int BOARD_WIDTH = 10;
    private static final int BOARD_HEIGHT = 20;
    private static final int BLOCK_SIZE = 30; // pixels per block

    // UI Dimensions
    private static final int SIDEBAR_WIDTH = 150;
    private static final int CANVAS_WIDTH = BOARD_WIDTH * BLOCK_SIZE;
    private static final int CANVAS_HEIGHT = BOARD_HEIGHT * BLOCK_SIZE;

    // Game state
    private final Color[][] board = new Color[BOARD_HEIGHT][BOARD_WIDTH];
    private Tetromino currentPiece;
    private Tetromino nextPiece;
    private int currentX, currentY;

    private int score = 0;
    private int linesCleared = 0;
    private boolean gameOver = false;
    private boolean isPaused = false;

    // Timing & Game Loop
    private long lastDropTime = 0;
    private long dropInterval = 500_000_000L; // nanoseconds (0.5 sec default)

    private Canvas gameCanvas;
    private Canvas previewCanvas;
    private Label scoreLabel;
    private Label linesLabel;
    private Label statusLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();

        // Main playfield canvas
        gameCanvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        root.setCenter(gameCanvas);

        // Sidebar for next piece & score
        VBox sidebar = new VBox(15);
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setStyle("-fx-background-color: #222; -fx-padding: 15;");

        Label nextLabel = createStyledLabel("NEXT PIECE:");
        previewCanvas = new Canvas(120, 120);

        Label scoreTitle = createStyledLabel("SCORE:");
        scoreLabel = createStyledLabel("0");

        Label linesTitle = createStyledLabel("LINES:");
        linesLabel = createStyledLabel("0");

        statusLabel = createStyledLabel("");
        statusLabel.setTextFill(YELLOW);

        sidebar.getChildren().addAll(
                nextLabel, previewCanvas,
                scoreTitle, scoreLabel,
                linesTitle, linesLabel,
                statusLabel
        );
        root.setRight(sidebar);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(event -> handleKeyPress(event.getCode()));

        primaryStage.setTitle("JavaFX Tetris");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        initGame();

        // Core Game Loop
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!gameOver && !isPaused) {
                    if (now - lastDropTime >= dropInterval) {
                        moveDown();
                        lastDropTime = now;
                    }
                }
                render();
            }
        };
        timer.start();
    }

    private void initGame() {
        // Clear board
        for (int r = 0; r < BOARD_HEIGHT; r++) {
            for (int c = 0; c < BOARD_WIDTH; c++) {
                board[r][c] = null;
            }
        }
        score = 0;
        linesCleared = 0;
        gameOver = false;
        isPaused = false;
        dropInterval = 500_000_000L;

        nextPiece = TetrominoUtil.getRandomPiece();
        spawnPiece();
        updateUI();
    }

    private void spawnPiece() {
        currentPiece = nextPiece;
        nextPiece = TetrominoUtil.getRandomPiece();

        currentX = (BOARD_WIDTH - currentPiece.getShape()[0].length) / 2;
        currentY = 0;

        // Check if spawn position is blocked (Game Over)
        if (!isValidMove(currentPiece.getShape(), currentX, currentY)) {
            gameOver = true;
            statusLabel.setText("GAME OVER!\nPress R to Restart");
        }
    }

    private void handleKeyPress(KeyCode code) {
        if (gameOver) {
            if (code == R) {
                initGame();
                statusLabel.setText("");
            }
            return;
        }

        if (code == P) {
            isPaused = !isPaused;
            statusLabel.setText(isPaused ? "PAUSED" : "");
            return;
        }

        if (isPaused) return;

        switch (code) {
            case LEFT:
            case A:
                if (isValidMove(currentPiece.getShape(), currentX - 1, currentY)) {
                    currentX--;
                }
                break;

            case RIGHT:
            case D:
                if (isValidMove(currentPiece.getShape(), currentX + 1, currentY)) {
                    currentX++;
                }
                break;

            case DOWN:
            case S:
                moveDown();
                break;

            case UP:
            case W:
                rotatePiece();
                break;

            case SPACE:
                hardDrop();
                break;
        }
    }

    private void moveDown() {
        if (isValidMove(currentPiece.getShape(), currentX, currentY + 1)) {
            currentY++;
        } else {
            lockPiece();
            clearLines();
            spawnPiece();
            updateUI();
        }
    }

    private void hardDrop() {
        while (isValidMove(currentPiece.getShape(), currentX, currentY + 1)) {
            currentY++;
            score += 2; // Small reward for hard dropping
        }
        lockPiece();
        clearLines();
        spawnPiece();
        updateUI();
    }

    private void rotatePiece() {
        int[][] rotated = currentPiece.getRotatedShape();
        // Check normal rotation
        if (isValidMove(rotated, currentX, currentY)) {
            currentPiece.setShape(rotated);
        } else if (isValidMove(rotated, currentX - 1, currentY)) {
            // Basic wall kick left
            currentX--;
            currentPiece.setShape(rotated);
        } else if (isValidMove(rotated, currentX + 1, currentY)) {
            // Basic wall kick right
            currentX++;
            currentPiece.setShape(rotated);
        }
    }

    private boolean isValidMove(int[][] shape, int targetX, int targetY) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int boardX = targetX + c;
                    int boardY = targetY + r;

                    // Bounds checking
                    if (boardX < 0 || boardX >= BOARD_WIDTH || boardY >= BOARD_HEIGHT) {
                        return false;
                    }
                    if (boardY >= 0 && board[boardY][boardX] != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void lockPiece() {
        int[][] shape = currentPiece.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int boardY = currentY + r;
                    int boardX = currentX + c;
                    if (boardY >= 0 && boardY < BOARD_HEIGHT) {
                        board[boardY][boardX] = currentPiece.getColor();
                    }
                }
            }
        }
    }

    private void clearLines() {
        int linesClearedThisTurn = 0;

        for (int r = BOARD_HEIGHT - 1; r >= 0; r--) {
            boolean fullLine = true;
            for (int c = 0; c < BOARD_WIDTH; c++) {
                if (board[r][c] == null) {
                    fullLine = false;
                    break;
                }
            }

            if (fullLine) {
                linesClearedThisTurn++;
                // Shift rows down
                for (int rowToShift = r; rowToShift > 0; rowToShift--) {
                    System.arraycopy(board[rowToShift - 1], 0, board[rowToShift], 0, BOARD_WIDTH);
                }
                // Clear top row
                for (int c = 0; c < BOARD_WIDTH; c++) {
                    board[0][c] = null;
                }
                r++; // Re-check current row index after shift
            }
        }

        if (linesClearedThisTurn > 0) {
            linesCleared += linesClearedThisTurn;
            // Classic Tetris scoring system
            switch (linesClearedThisTurn) {
                case 1:
                    score += 100;
                    break;
                case 2:
                    score += 300;
                    break;
                case 3:
                    score += 500;
                    break;
                case 4:
                    score += 800;
                    break;
            }
            // Increase speed every 5 lines cleared
            dropInterval = Math.max(100_000_000L, 500_000_000L - (linesCleared / 5) * 40_000_000L);
            updateUI();
        }
    }

    private void updateUI() {
        scoreLabel.setText(String.valueOf(score));
        linesLabel.setText(String.valueOf(linesCleared));
        drawPreview();
    }

    // Graphics Rendering
    private void render() {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();

        // Clear background
        gc.setFill(Color.web("#111111"));
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Draw grid guidelines
        gc.setStroke(Color.web("#222222"));
        gc.setLineWidth(1);
        for (int r = 0; r <= BOARD_HEIGHT; r++) {
            gc.strokeLine(0, r * BLOCK_SIZE, CANVAS_WIDTH, r * BLOCK_SIZE);
        }
        for (int c = 0; c <= BOARD_WIDTH; c++) {
            gc.strokeLine(c * BLOCK_SIZE, 0, c * BLOCK_SIZE, CANVAS_HEIGHT);
        }

        // Draw locked blocks
        for (int r = 0; r < BOARD_HEIGHT; r++) {
            for (int c = 0; c < BOARD_WIDTH; c++) {
                if (board[r][c] != null) {
                    drawBlock(gc, c * BLOCK_SIZE, r * BLOCK_SIZE, board[r][c]);
                }
            }
        }

        // Draw active piece
        if (currentPiece != null && !gameOver) {
            int[][] shape = currentPiece.getShape();
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        drawBlock(gc, (currentX + c) * BLOCK_SIZE, (currentY + r) * BLOCK_SIZE, currentPiece.getColor());
                    }
                }
            }
        }
    }

    private void drawPreview() {
        GraphicsContext gc = previewCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#222222"));
        gc.fillRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());

        if (nextPiece != null) {
            int[][] shape = nextPiece.getShape();
            double offsetX = (previewCanvas.getWidth() - shape[0].length * BLOCK_SIZE) / 2;
            double offsetY = (previewCanvas.getHeight() - shape.length * BLOCK_SIZE) / 2;

            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        drawBlock(gc, offsetX + c * BLOCK_SIZE, offsetY + r * BLOCK_SIZE, nextPiece.getColor());
                    }
                }
            }
        }
    }

    private void drawBlock(GraphicsContext gc, double x, double y, Color color) {
        // Main block color
        gc.setFill(color);
        gc.fillRect(x, y, BLOCK_SIZE, BLOCK_SIZE);

        // Subtle bevel effect
        gc.setFill(color.brighter());
        gc.fillRect(x, y, BLOCK_SIZE, 3);
        gc.fillRect(x, y, 3, BLOCK_SIZE);

        gc.setFill(color.darker());
        gc.fillRect(x, y + BLOCK_SIZE - 3, BLOCK_SIZE, 3);
        gc.fillRect(x + BLOCK_SIZE - 3, y, 3, BLOCK_SIZE);

        // Border outline
        gc.setStroke(BLACK);
        gc.setLineWidth(1);
        gc.strokeRect(x, y, BLOCK_SIZE, BLOCK_SIZE);
    }

    private Label createStyledLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", BOLD, 14));
        label.setTextFill(WHITE);
        return label;
    }
}
