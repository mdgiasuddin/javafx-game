package com.example.javagame.chess;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class ChessApp extends Application {

    private static final int BOARD_SIZE = 8;
    private static final int TILE_SIZE = 80;

    // Unicode symbols for chess pieces
    // White: ♙ ♖ ♘ ♗ ♕ ♔
    // Black: ♟ ♜ ♞ ♝ ♛ ♚
    private static final String[][] INITIAL_BOARD = {
            {"♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜"},
            {"♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟"},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙"},
            {"♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖"}
    };

    private final Label[][] boardUI = new Label[BOARD_SIZE][BOARD_SIZE];
    private final Rectangle[][] bgTiles = new Rectangle[BOARD_SIZE][BOARD_SIZE];

    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean whiteTurn = true;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) {
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                StackPane tile = createTile(row, col);
                gridPane.add(tile, col, row);
            }
        }

        VBox mainLayout = new VBox(15);
        mainLayout.setAlignment(Pos.CENTER);

        statusLabel = new Label("White's Turn");
        statusLabel.setFont(Font.font("Arial", 20));

        mainLayout.getChildren().addAll(statusLabel, gridPane);

        Scene scene = new Scene(mainLayout, 700, 750);
        primaryStage.setTitle("JavaFX Chess - Full Rule Validation");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private StackPane createTile(int row, int col) {
        StackPane stack = new StackPane();
        stack.setPrefSize(TILE_SIZE, TILE_SIZE);

        Rectangle bg = new Rectangle(TILE_SIZE, TILE_SIZE);
        boolean isLight = (row + col) % 2 == 0;
        bg.setFill(isLight ? Color.valueOf("#f0d9b5") : Color.valueOf("#b58863"));
        bgTiles[row][col] = bg;

        Label pieceLabel = new Label(INITIAL_BOARD[row][col]);
        pieceLabel.setFont(Font.font("Arial", 48));
        boardUI[row][col] = pieceLabel;

        stack.getChildren().addAll(bg, pieceLabel);
        stack.setOnMouseClicked(event -> handleTileClick(row, col));

        return stack;
    }

    private void handleTileClick(int row, int col) {
        String clickedPiece = boardUI[row][col].getText();

        if (selectedRow == -1) {
            if (!clickedPiece.isEmpty()) {
                if (isCurrentPlayerPiece(clickedPiece)) {
                    selectedRow = row;
                    selectedCol = col;
                    bgTiles[row][col].setFill(Color.valueOf("#7b9c50"));
                } else {
                    statusLabel.setText((whiteTurn ? "White's" : "Black's") + " turn! Cannot select opponent's piece.");
                }
            }
        } else {
            if (row == selectedRow && col == selectedCol) {
                resetBoardColors();
                selectedRow = -1;
                selectedCol = -1;
                statusLabel.setText(whiteTurn ? "White's Turn" : "Black's Turn");
                return;
            }

            if (!clickedPiece.isEmpty() && isCurrentPlayerPiece(clickedPiece)) {
                resetBoardColors();
                selectedRow = row;
                selectedCol = col;
                bgTiles[row][col].setFill(Color.valueOf("#7b9c50"));
                return;
            }

            // Execute move validation check
            String movingPiece = boardUI[selectedRow][selectedCol].getText();
            if (!isValidMove(movingPiece, selectedRow, selectedCol, row, col)) {
                return; // Rejects move and leaves error message in statusLabel
            }

            // Prevent capturing own piece
            if (!clickedPiece.isEmpty() && isCurrentPlayerPiece(clickedPiece)) {
                statusLabel.setText("Invalid move! Cannot capture your own piece.");
                return;
            }

            // Execute the move
            boardUI[row][col].setText(movingPiece);
            boardUI[selectedRow][selectedCol].setText("");

            resetBoardColors();
            selectedRow = -1;
            selectedCol = -1;

            whiteTurn = !whiteTurn;
            statusLabel.setText(whiteTurn ? "White's Turn" : "Black's Turn");
        }
    }

    private boolean isValidMove(String piece, int sRow, int sCol, int tRow, int tCol) {
        boolean isWhite = "♙♖♘♗♕♔".contains(piece);
        String targetPiece = boardUI[tRow][tCol].getText();

        // Cannot capture friendly pieces
        if (!targetPiece.isEmpty() && isCurrentPlayerPiece(targetPiece) == isCurrentPlayerPiece(piece)) {
            statusLabel.setText("Invalid move! Target square is occupied by a friendly piece.");
            return false;
        }

        switch (piece) {
            case "♖":
            case "♜": // Rook
                if (sRow != tRow && sCol != tCol) {
                    statusLabel.setText("Rooks move only in straight lines.");
                    return false;
                }
                if (!isPathClear(sRow, sCol, tRow, tCol)) {
                    statusLabel.setText("Path is blocked.");
                    return false;
                }
                return true;

            case "♗":
            case "♝": // Bishop
                if (Math.abs(sRow - tRow) != Math.abs(sCol - tCol)) {
                    statusLabel.setText("Bishops move only diagonally.");
                    return false;
                }
                if (!isPathClear(sRow, sCol, tRow, tCol)) {
                    statusLabel.setText("Path is blocked.");
                    return false;
                }
                return true;

            case "♕":
            case "♛": // Queen (Rook + Bishop combination)
                boolean isStraight = (sRow == tRow || sCol == tCol);
                boolean isDiagonal = (Math.abs(sRow - tRow) == Math.abs(sCol - tCol));
                if (!isStraight && !isDiagonal) {
                    statusLabel.setText("Queen moves straight or diagonally.");
                    return false;
                }
                if (!isPathClear(sRow, sCol, tRow, tCol)) {
                    statusLabel.setText("Path is blocked.");
                    return false;
                }
                return true;

            case "♘":
            case "♞": // Knight
                int rowDiff = Math.abs(sRow - tRow);
                int colDiff = Math.abs(sCol - tCol);
                if (!((rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2))) {
                    statusLabel.setText("Invalid L-shape knight move.");
                    return false;
                }
                return true;

            case "♔":
            case "♚": // King
                if (Math.abs(sRow - tRow) > 1 || Math.abs(sCol - tCol) > 1) {
                    statusLabel.setText("King can only move 1 square in any direction.");
                    return false;
                }
                return true;

            case "♙": // White Pawn
                return validatePawnMove(sRow, sCol, tRow, tCol, true);

            case "♟": // Black Pawn
                return validatePawnMove(sRow, sCol, tRow, tCol, false);

            default:
                return false;
        }
    }

    private boolean validatePawnMove(int sRow, int sCol, int tRow, int tCol, boolean isWhite) {
        int direction = isWhite ? -1 : 1;
        int startRow = isWhite ? 6 : 1;
        String targetPiece = boardUI[tRow][tCol].getText();

        // 1. Move forward 1 square
        if (sCol == tCol && tRow == sRow + direction && targetPiece.isEmpty()) {
            return true;
        }
        // 2. Move forward 2 squares from starting rank
        if (sCol == tCol && sRow == startRow && tRow == sRow + (2 * direction)) {
            int midRow = sRow + direction;
            if (targetPiece.isEmpty() && boardUI[midRow][sCol].getText().isEmpty()) {
                return true;
            }
        }
        // 3. Diagonal capture
        if (Math.abs(sCol - tCol) == 1 && tRow == sRow + direction && !targetPiece.isEmpty()) {
            return true;
        }

        statusLabel.setText("Invalid pawn move.");
        return false;
    }

    private boolean isPathClear(int startRow, int startCol, int targetRow, int targetCol) {
        int rowStep = Integer.compare(targetRow, startRow);
        int colStep = Integer.compare(targetCol, startCol);

        int currentRow = startRow + rowStep;
        int currentCol = startCol + colStep;

        while (currentRow != targetRow || currentCol != targetCol) {
            if (!boardUI[currentRow][currentCol].getText().isEmpty()) {
                return false;
            }
            currentRow += rowStep;
            currentCol += colStep;
        }
        return true;
    }

    private boolean isCurrentPlayerPiece(String piece) {
        boolean isWhitePiece = "♙♖♘♗♕♔".contains(piece);
        boolean isBlackPiece = "♟♜♞♝♛♚".contains(piece);
        return (whiteTurn && isWhitePiece) || (!whiteTurn && isBlackPiece);
    }

    private void resetBoardColors() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                boolean isLight = (r + c) % 2 == 0;
                bgTiles[r][c].setFill(isLight ? Color.valueOf("#f0d9b5") : Color.valueOf("#b58863"));
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}