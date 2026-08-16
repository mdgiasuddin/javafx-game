package com.example.javagame.chessnetwork;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChessNetworkApp extends Application {

    private static final int BOARD_SIZE = 8;
    private static final int TILE_SIZE = 100;

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

    // UI Enhancements for Turn Clarity
    private Label statusLabel;
    private VBox statusContainer;
    private GridPane gridPane;

    private PrintWriter out;
    private BufferedReader in;
    private boolean isMyTurn = false;
    private boolean playerIsWhite = true;

    @Override
    public void start(Stage primaryStage) {
        gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                StackPane tile = createTile(row, col);
                gridPane.add(tile, col, row);
            }
        }

        // Enhanced Status Display Container
        statusLabel = new Label("Connecting to server...");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        statusLabel.setTextFill(Color.WHITE);

        statusContainer = new VBox(statusLabel);
        statusContainer.setAlignment(Pos.CENTER);
        statusContainer.setPrefHeight(60);
        statusContainer.setStyle("-fx-background-color: #555555; -fx-background-radius: 8px;");

        VBox mainLayout = new VBox(15);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setStyle("-fx-padding: 15px; -fx-background-color: #2b2b2b;");
        mainLayout.getChildren().addAll(statusContainer, gridPane);

        Scene scene = new Scene(mainLayout, 900, 950);
        primaryStage.setTitle("JavaFX Networked Chess");
        primaryStage.setScene(scene);
        primaryStage.show();

        setupNetworking("127.0.0.1", 12345);
    }

    private void updateTurnIndicator() {
        Platform.runLater(() -> {
            boolean isMyColorTurn = (whiteTurn == playerIsWhite);
            if (gameOver) {
                statusContainer.setStyle("-fx-background-color: #d9534f; -fx-background-radius: 8px;");
                return;
            }

            if (isMyColorTurn) {
                // Bright Green banner highlighting YOUR turn
                statusContainer.setStyle("-fx-background-color: #4CAF50; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(76,175,80,0.6), 10, 0, 0, 0);");
                statusLabel.setText("⭐ YOUR TURN (" + (playerIsWhite ? "White" : "Black") + ") ⭐");
            } else {
                // Dim Muted banner highlighting OPPONENT's turn
                statusContainer.setStyle("-fx-background-color: #607D8B; -fx-background-radius: 8px;");
                statusLabel.setText("⏳ OPPONENT'S TURN (" + (whiteTurn ? "White" : "Black") + ") ⏳");
            }
        });
    }

    private void setupNetworking(String address, int port) {
        try {
            // Networking fields
            Socket socket = new Socket(address, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String colorAssignment = in.readLine();
            if ("WHITE".equals(colorAssignment)) {
                playerIsWhite = true;
                isMyTurn = true;
            } else {
                playerIsWhite = false;
                isMyTurn = false;

                Platform.runLater(() -> {
                    gridPane.setRotate(180);
                    for (int r = 0; r < BOARD_SIZE; r++) {
                        for (int c = 0; c < BOARD_SIZE; c++) {
                            boardUI[r][c].setRotate(180);
                        }
                    }
                });
            }
            updateTurnIndicator();
            new Thread(new IncomingReader()).start();

        } catch (Exception e) {
            Platform.runLater(() -> {
                statusContainer.setStyle("-fx-background-color: #d9534f; -fx-background-radius: 8px;");
                statusLabel.setText("Could not connect to server.");
            });
        }
    }

    private StackPane createTile(int row, int col) {
        StackPane stack = new StackPane();
        stack.setPrefSize(TILE_SIZE, TILE_SIZE);

        Rectangle bg = new Rectangle(TILE_SIZE, TILE_SIZE);
        boolean isLight = (row + col) % 2 == 0;
        bg.setFill(isLight ? Color.valueOf("#f0d9b5") : Color.valueOf("#b58863"));
        bgTiles[row][col] = bg;

        Label pieceLabel = new Label(INITIAL_BOARD[row][col]);
        pieceLabel.setFont(Font.font("Arial", 72));
        boardUI[row][col] = pieceLabel;

        stack.getChildren().addAll(bg, pieceLabel);
        stack.setOnMouseClicked(event -> handleTileClick(row, col));

        return stack;
    }

    private void handleTileClick(int row, int col) {
        if (gameOver) return;

        if (!isMyTurn) {
            return;
        }

        String clickedPiece = boardUI[row][col].getText();

        if (selectedRow == -1) {
            if (!clickedPiece.isEmpty()) {
                if (isCurrentPlayerPiece(clickedPiece)) {
                    selectedRow = row;
                    selectedCol = col;
                    bgTiles[row][col].setFill(Color.valueOf("#7b9c50"));
                }
            }
        } else {
            if (row == selectedRow && col == selectedCol) {
                resetBoardColors();
                selectedRow = -1;
                selectedCol = -1;
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

            String originalTargetContent = boardUI[row][col].getText();
            boardUI[row][col].setText(movingPiece);
            boardUI[selectedRow][selectedCol].setText("");

            boolean isStillInCheck = isKingInCheck(whiteTurn);

            boardUI[selectedRow][selectedCol].setText(movingPiece);
            boardUI[row][col].setText(originalTargetContent);

            if (isStillInCheck) {
                return;
            }

            String moveMessage = selectedRow + "," + selectedCol + "," + row + "," + col;
            out.println(moveMessage);

            executeMoveLocally(selectedRow, selectedCol, row, col);
            isMyTurn = false;
            updateTurnIndicator();
        }
    }

    private void executeMoveLocally(int sRow, int sCol, int tRow, int tCol) {
        String movingPiece = boardUI[sRow][sCol].getText();
        String targetPiece = boardUI[tRow][tCol].getText();

        if (targetPiece.equals("♔") || targetPiece.equals("♚")) {
            boardUI[tRow][tCol].setText(movingPiece);
            boardUI[sRow][sCol].setText("");
            resetBoardColors();
            statusContainer.setStyle("-fx-background-color: #d9534f; -fx-background-radius: 8px;");
            statusLabel.setText("GAME OVER!");
            gameOver = true;
            return;
        }

        boardUI[tRow][tCol].setText(movingPiece);
        boardUI[sRow][sCol].setText("");

        if (movingPiece.equals("♙") && tRow == 0) boardUI[tRow][tCol].setText("♕");
        if (movingPiece.equals("♟") && tRow == 7) boardUI[tRow][tCol].setText("♛");

        resetBoardColors();
        selectedRow = -1;
        selectedCol = -1;
        whiteTurn = !whiteTurn;

        updateTurnIndicator();
    }

    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    String[] parts = line.split(",");
                    int sRow = Integer.parseInt(parts[0]);
                    int sCol = Integer.parseInt(parts[1]);
                    int tRow = Integer.parseInt(parts[2]);
                    int tCol = Integer.parseInt(parts[3]);

                    Platform.runLater(() -> {
                        executeMoveLocally(sRow, sCol, tRow, tCol);
                        isMyTurn = true;
                        updateTurnIndicator();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusContainer.setStyle("-fx-background-color: #d9534f; -fx-background-radius: 8px;");
                    statusLabel.setText("Connection lost with opponent.");
                });
            }
        }
    }

    private boolean isCurrentPlayerPiece(String piece) {
        return isPieceOfColor(piece, whiteTurn);
    }

    private boolean isPieceOfColor(String piece, boolean white) {
        boolean isWhitePiece = "♙♖♘♗♕♔".contains(piece);
        boolean isBlackPiece = "♟♜♞♝♛♚".contains(piece);
        return (white && isWhitePiece) || (!white && isBlackPiece);
    }

    private boolean isValidMove(String piece, int sRow, int sCol, int tRow, int tCol) {
        boolean isWhite = "♙♖♘♗♕♔".contains(piece);
        String targetPiece = boardUI[tRow][tCol].getText();
        if (!targetPiece.isEmpty() && isCurrentPlayerPiece(targetPiece) == isCurrentPlayerPiece(piece)) return false;

        final boolean isDiagonalMove = Math.abs(sRow - tRow) == Math.abs(sCol - tCol);
        return switch (piece) {
            case "♖", "♜" -> (sRow == tRow || sCol == tCol) && isPathClear(sRow, sCol, tRow, tCol);
            case "♗", "♝" -> isDiagonalMove && isPathClear(sRow, sCol, tRow, tCol);
            case "♕", "♛" -> ((sRow == tRow || sCol == tCol) || isDiagonalMove) && isPathClear(sRow, sCol, tRow, tCol);
            case "♘", "♞" -> {
                int rD = Math.abs(sRow - tRow), cD = Math.abs(sCol - tCol);
                yield (rD == 2 && cD == 1) || (rD == 1 && cD == 2);
            }
            case "♔", "♚" -> Math.abs(sRow - tRow) <= 1 && Math.abs(sCol - tCol) <= 1;
            case "♙", "♟" -> validatePawnMove(sRow, sCol, tRow, tCol, isWhite);
            default -> false;
        };
    }

    private boolean validatePawnMove(int sRow, int sCol, int tRow, int tCol, boolean isWhite) {
        int dir = isWhite ? -1 : 1;
        int start = isWhite ? 6 : 1;
        String target = boardUI[tRow][tCol].getText();
        if (sCol == tCol && tRow == sRow + dir && target.isEmpty()) return true;
        if (sCol == tCol && sRow == start && tRow == sRow + (2 * dir) && target.isEmpty() && boardUI[sRow + dir][sCol].getText().isEmpty())
            return true;
        return Math.abs(sCol - tCol) == 1 && tRow == sRow + dir && !target.isEmpty();
    }

    private boolean isPathClear(int sRow, int sCol, int tRow, int tCol) {
        int rStep = Integer.compare(tRow, sRow), cStep = Integer.compare(tCol, sCol);
        int r = sRow + rStep, c = sCol + cStep;
        while (r != tRow || c != tCol) {
            if (!boardUI[r][c].getText().isEmpty()) return false;
            r += rStep;
            c += cStep;
        }
        return true;
    }

    private boolean isKingInCheck(boolean isWhiteKing) {
        String kingSymbol = isWhiteKing ? "♔" : "♚";
        int kRow = -1, kCol = -1;
        outer:
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (boardUI[r][c].getText().equals(kingSymbol)) {
                    kRow = r;
                    kCol = c;
                    break outer;
                }
            }
        }
        if (kRow == -1) return false;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String p = boardUI[r][c].getText();
                if (!p.isEmpty() && isPieceOfColor(p, !isWhiteKing) && isValidMove(p, r, c, kRow, kCol)) return true;
            }
        }
        return false;
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