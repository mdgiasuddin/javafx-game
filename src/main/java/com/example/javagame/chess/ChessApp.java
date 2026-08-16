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
    private boolean gameOver = false;
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
        primaryStage.setTitle("JavaFX Chess - Legal Check Evasion");
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
        if (gameOver) return;

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

            String movingPiece = boardUI[selectedRow][selectedCol].getText();
            if (!isValidMove(movingPiece, selectedRow, selectedCol, row, col)) {
                return;
            }

            // SIMULATE MOVE: Test if this move leaves the friendly king in check
            String originalTargetContent = boardUI[row][col].getText();

            // Apply temporary move
            boardUI[row][col].setText(movingPiece);
            boardUI[selectedRow][selectedCol].setText("");

            boolean isStillInCheck = isKingInCheck(whiteTurn);

            // Revert the temporary state update
            boardUI[selectedRow][selectedCol].setText(movingPiece);
            boardUI[row][col].setText(originalTargetContent);

            if (isStillInCheck) {
                statusLabel.setText("Illegal move! Your King remains in check.");
                return;
            }

            // Game over capture check
            if (originalTargetContent.equals("♔") || originalTargetContent.equals("♚")) {
                boardUI[row][col].setText(movingPiece);
                boardUI[selectedRow][selectedCol].setText("");
                resetBoardColors();

                String winner = whiteTurn ? "White" : "Black";
                statusLabel.setText("Checkmate! " + winner + " wins the game!");
                gameOver = true;
                return;
            }

            // Commit final move execution
            boardUI[row][col].setText(movingPiece);
            boardUI[selectedRow][selectedCol].setText("");

            resetBoardColors();
            selectedRow = -1;
            selectedCol = -1;

            // Switch turn
            whiteTurn = !whiteTurn;

            // Evaluate check status for the new player
            if (isKingInCheck(whiteTurn)) {
                statusLabel.setText((whiteTurn ? "White" : "Black") + " is in CHECK!");
            } else {
                statusLabel.setText(whiteTurn ? "White's Turn" : "Black's Turn");
            }
        }
    }

    private boolean isKingInCheck(boolean isWhiteKing) {
        String kingSymbol = isWhiteKing ? "♔" : "♚";
        int kingRow = -1, kingCol = -1;

        outer:
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (boardUI[r][c].getText().equals(kingSymbol)) {
                    kingRow = r;
                    kingCol = c;
                    break outer;
                }
            }
        }

        if (kingRow == -1) return false;

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String piece = boardUI[r][c].getText();
                if (!piece.isEmpty() && isPieceOfColor(piece, !isWhiteKing)) {
                    if (isValidMoveSimulation(piece, r, c, kingRow, kingCol)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isValidMoveSimulation(String piece, int sRow, int sCol, int tRow, int tCol) {
        switch (piece) {
            case "♖":
            case "♜":
                return (sRow == tRow || sCol == tCol) && isPathClear(sRow, sCol, tRow, tCol);
            case "♗":
            case "♝":
                return (Math.abs(sRow - tRow) == Math.abs(sCol - tCol)) && isPathClear(sRow, sCol, tRow, tCol);
            case "♕":
            case "♛":
                return ((sRow == tRow || sCol == tCol) || Math.abs(sRow - tRow) == Math.abs(sCol - tCol)) && isPathClear(sRow, sCol, tRow, tCol);
            case "♘":
            case "♞":
                int rDiff = Math.abs(sRow - tRow);
                int cDiff = Math.abs(sCol - tCol);
                return (rDiff == 2 && cDiff == 1) || (rDiff == 1 && cDiff == 2);
            case "♔":
            case "♚":
                return Math.abs(sRow - tRow) <= 1 && Math.abs(sCol - tCol) <= 1;
            case "♙":
            case "♟":
                int dir = piece.equals("♙") ? -1 : 1;
                return Math.abs(sCol - tCol) == 1 && tRow == sRow + dir;
            default:
                return false;
        }
    }

    private boolean isValidMove(String piece, int sRow, int sCol, int tRow, int tCol) {
        boolean isWhite = "♙♖♘♗♕♔".contains(piece);
        String targetPiece = boardUI[tRow][tCol].getText();

        if (!targetPiece.isEmpty() && isCurrentPlayerPiece(targetPiece) == isCurrentPlayerPiece(piece)) {
            statusLabel.setText("Invalid move! Target square is occupied by a friendly piece.");
            return false;
        }

        boolean legalGeometry = false;
        switch (piece) {
            case "♖":
            case "♜":
                legalGeometry = (sRow == tRow || sCol == tCol) && isPathClear(sRow, sCol, tRow, tCol);
                if (!legalGeometry) statusLabel.setText("Rooks move straight and paths must be clear.");
                break;
            case "♗":
            case "♝":
                legalGeometry = (Math.abs(sRow - tRow) == Math.abs(sCol - tCol)) && isPathClear(sRow, sCol, tRow, tCol);
                if (!legalGeometry) statusLabel.setText("Bishops move diagonally and paths must be clear.");
                break;
            case "♕":
            case "♛":
                boolean straight = (sRow == tRow || sCol == tCol);
                boolean diag = (Math.abs(sRow - tRow) == Math.abs(sCol - tCol));
                legalGeometry = (straight || diag) && isPathClear(sRow, sCol, tRow, tCol);
                if (!legalGeometry) statusLabel.setText("Queen move blocked or invalid.");
                break;
            case "♘":
            case "♞":
                int rowDiff = Math.abs(sRow - tRow);
                int colDiff = Math.abs(sCol - tCol);
                legalGeometry = (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
                if (!legalGeometry) statusLabel.setText("Invalid L-shape knight move.");
                break;
            case "♔":
            case "♚":
                legalGeometry = Math.abs(sRow - tRow) <= 1 && Math.abs(sCol - tCol) <= 1;
                if (!legalGeometry) statusLabel.setText("King can only move 1 square.");
                break;
            case "♙":
            case "♟":
                legalGeometry = validatePawnMove(sRow, sCol, tRow, tCol, isWhite);
                break;
        }
        return legalGeometry;
    }

    private boolean validatePawnMove(int sRow, int sCol, int tRow, int tCol, boolean isWhite) {
        int direction = isWhite ? -1 : 1;
        int startRow = isWhite ? 6 : 1;
        String targetPiece = boardUI[tRow][tCol].getText();

        if (sCol == tCol && tRow == sRow + direction && targetPiece.isEmpty()) return true;
        if (sCol == tCol && sRow == startRow && tRow == sRow + (2 * direction)) {
            if (targetPiece.isEmpty() && boardUI[sRow + direction][sCol].getText().isEmpty()) return true;
        }
        if (Math.abs(sCol - tCol) == 1 && tRow == sRow + direction && !targetPiece.isEmpty()) return true;

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
        return isPieceOfColor(piece, whiteTurn);
    }

    private boolean isPieceOfColor(String piece, boolean white) {
        boolean isWhitePiece = "♙♖♘♗♕♔".contains(piece);
        boolean isBlackPiece = "♟♜♞♝♛♚".contains(piece);
        return (white && isWhitePiece) || (!white && isBlackPiece);
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