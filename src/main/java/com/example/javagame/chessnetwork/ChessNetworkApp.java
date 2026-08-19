package com.example.javagame.chessnetwork;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static javafx.geometry.Pos.CENTER;
import static javafx.scene.paint.Color.WHITE;
import static javafx.scene.text.FontWeight.BOLD;

public class ChessNetworkApp extends Application {

    private static final int BOARD_SIZE = 8;
    private static final int TILE_SIZE = 100;
    private static final int DEFAULT_PORT = 12345;
    private static final String DEFAULT_HOST = "127.0.0.1";

    private static final String WHITE_PIECES = "♙♖♘♗♕♔";
    private static final String BLACK_PIECES = "♟♜♞♝♛♚";

    private static final String LIGHT_TILE = "#f0d9b5";
    private static final String DARK_TILE = "#b58863";
    private static final String SELECTED_TILE = "#7b9c50";

    private static final String ERROR_STYLE = "-fx-background-color: #d9534f; -fx-background-radius: 8px;";
    private static final String CONNECTING_STYLE = "-fx-background-color: #555555; -fx-background-radius: 8px;";
    private static final String MY_TURN_STYLE = "-fx-background-color: #4CAF50; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(76,175,80,0.6), 10, 0, 0, 0);";
    private static final String OPPONENT_TURN_STYLE = "-fx-background-color: #607D8B; -fx-background-radius: 8px;";
    private static final String CHECK_STYLE = "-fx-background-color: #f0ad4e; -fx-background-radius: 8px;";

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

    private boolean whiteKingMoved = false;
    private boolean whiteRookA1Moved = false;
    private boolean whiteRookH1Moved = false;
    private boolean blackKingMoved = false;
    private boolean blackRookA8Moved = false;
    private boolean blackRookH8Moved = false;

    private Label statusLabel;
    private VBox statusContainer;
    private GridPane gridPane;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isMyTurn = false;
    private boolean playerIsWhite = true;

    @Override
    public void start(Stage primaryStage) {
        gridPane = new GridPane();
        gridPane.setAlignment(CENTER);

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                StackPane tile = createTile(row, col);
                gridPane.add(tile, col, row);
            }
        }

        statusLabel = new Label("Connecting to server...");
        statusLabel.setFont(Font.font("Arial", BOLD, 18));
        statusLabel.setTextFill(WHITE);

        statusContainer = new VBox(statusLabel);
        statusContainer.setAlignment(CENTER);
        statusContainer.setPrefHeight(60);
        statusContainer.setStyle(CONNECTING_STYLE);

        VBox mainLayout = new VBox(15);
        mainLayout.setAlignment(CENTER);
        mainLayout.setStyle("-fx-padding: 15px; -fx-background-color: #2b2b2b;");
        mainLayout.getChildren().addAll(statusContainer, gridPane);

        Scene scene = new Scene(mainLayout, 900, 950);
        primaryStage.setTitle("JavaFX Networked Chess");
        primaryStage.setScene(scene);
        primaryStage.show();

        String host = getParameters().getUnnamed().isEmpty()
                ? DEFAULT_HOST
                : getParameters().getUnnamed().getFirst();

        setupNetworking(host, DEFAULT_PORT);
    }

    @Override
    public void stop() throws Exception {
        closeConnection();
        super.stop();
    }

    private void setupNetworking(String address, int port) {
        Thread setupThread = new Thread(() -> {
            try {
                socket = new Socket(address, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String colorAssignment = in.readLine();
                if (!"WHITE".equals(colorAssignment) && !"BLACK".equals(colorAssignment)) {
                    throw new IOException("Invalid color assignment from server: " + colorAssignment);
                }

                Platform.runLater(() -> handleColorAssignment(colorAssignment));

                Thread incomingThread = new Thread(new IncomingReader(), "Chess-Incoming-Reader");
                incomingThread.setDaemon(true);
                incomingThread.start();
            } catch (Exception e) {
                Platform.runLater(() -> showError("Could not connect to server."));
                closeConnection();
            }
        }, "Chess-Network-Setup");

        setupThread.setDaemon(true);
        setupThread.start();
    }

    private void handleColorAssignment(String colorAssignment) {
        playerIsWhite = "WHITE".equals(colorAssignment);
        isMyTurn = playerIsWhite;

        if (!playerIsWhite) {
            gridPane.setRotate(180);
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    boardUI[r][c].setRotate(180);
                }
            }
        }

        updateTurnIndicator();
    }

    private StackPane createTile(int row, int col) {
        StackPane stack = new StackPane();
        stack.setPrefSize(TILE_SIZE, TILE_SIZE);

        Rectangle bg = new Rectangle(TILE_SIZE, TILE_SIZE);
        bg.setFill(getTileColor(row, col));
        bgTiles[row][col] = bg;

        Label pieceLabel = new Label(INITIAL_BOARD[row][col]);
        pieceLabel.setFont(Font.font("Arial", 72));
        boardUI[row][col] = pieceLabel;

        stack.getChildren().addAll(bg, pieceLabel);
        stack.setOnMouseClicked(event -> handleTileClick(row, col));

        return stack;
    }

    private void handleTileClick(int row, int col) {
        if (gameOver) {
            return;
        }

        if (!isMyTurn) {
            setStatus("Wait for your opponent's move.", OPPONENT_TURN_STYLE);
            return;
        }

        String clickedPiece = boardUI[row][col].getText();

        if (selectedRow == -1) {
            selectPiece(row, col, clickedPiece);
            return;
        }

        if (row == selectedRow && col == selectedCol) {
            clearSelection();
            updateTurnIndicator();
            return;
        }

        if (!clickedPiece.isEmpty() && isPieceOfColor(clickedPiece, playerIsWhite)) {
            if (selectedRow != -1) {
                bgTiles[selectedRow][selectedCol].setFill(getTileColor(selectedRow, selectedCol));
            }
            selectedRow = row;
            selectedCol = col;
            bgTiles[row][col].setFill(Color.valueOf(SELECTED_TILE));
            return;
        }

        attemptMove(row, col);
    }

    private void selectPiece(int row, int col, String clickedPiece) {
        if (clickedPiece.isEmpty()) {
            setStatus("Select one of your pieces.", MY_TURN_STYLE);
            return;
        }

        if (!isPieceOfColor(clickedPiece, playerIsWhite)) {
            setStatus("You cannot move your opponent's piece.", MY_TURN_STYLE);
            return;
        }

        if (!isPieceOfColor(clickedPiece, whiteTurn)) {
            setStatus("It is not that color's turn.", MY_TURN_STYLE);
            return;
        }

        selectedRow = row;
        selectedCol = col;
        bgTiles[row][col].setFill(Color.valueOf(SELECTED_TILE));
    }

    private void attemptMove(int targetRow, int targetCol) {
        String movingPiece = boardUI[selectedRow][selectedCol].getText();

        if (!isValidMove(movingPiece, selectedRow, selectedCol, targetRow, targetCol)) {
            setStatus("Invalid move.", MY_TURN_STYLE);
            return;
        }

        if (wouldLeaveKingInCheck(selectedRow, selectedCol, targetRow, targetCol, whiteTurn)) {
            setStatus("Illegal move! Your king would be in check.", CHECK_STYLE);
            return;
        }

        String moveMessage = selectedRow + "," + selectedCol + "," + targetRow + "," + targetCol;
        if (out == null) {
            showError("Not connected to server.");
            return;
        }

        out.println(moveMessage);
        executeMoveLocally(selectedRow, selectedCol, targetRow, targetCol);
        isMyTurn = false;
        updateTurnIndicator();
    }

    private void executeMoveLocally(int sRow, int sCol, int tRow, int tCol) {
        String movingPiece = boardUI[sRow][sCol].getText();

        if (isCastlingMove(movingPiece, sRow, sCol, tRow, tCol)) {
            executeCastling(sRow, sCol, tRow, tCol);
        } else {
            boardUI[tRow][tCol].setText(movingPiece);
            boardUI[sRow][sCol].setText("");

            promotePawnIfNeeded(movingPiece, tRow, tCol);
        }

        updateMovementFlags(movingPiece, sRow, sCol);

        if (selectedRow != -1) {
            bgTiles[selectedRow][selectedCol].setFill(getTileColor(selectedRow, selectedCol));
        }

        selectedRow = -1;
        selectedCol = -1;
        whiteTurn = !whiteTurn;

        updateGameStateAfterMove();
    }

    private boolean isCastlingMove(String piece, int sRow, int sCol, int tRow, int tCol) {
        if (!piece.equals("♔") && !piece.equals("♚")) {
            return false;
        }

        return sRow == tRow && Math.abs(sCol - tCol) == 2;
    }

    private void executeCastling(int sRow, int sCol, int tRow, int tCol) {
        String king = boardUI[sRow][sCol].getText();

        boardUI[sRow][sCol].setText("");
        boardUI[tRow][tCol].setText(king);

        if (tCol > sCol) {
            String rook = boardUI[sRow][7].getText();
            boardUI[sRow][7].setText("");
            boardUI[sRow][5].setText(rook);
        } else {
            String rook = boardUI[sRow][0].getText();
            boardUI[sRow][0].setText("");
            boardUI[sRow][3].setText(rook);
        }
    }

    private void updateMovementFlags(String piece, int row, int col) {
        switch (piece) {
            case "♔" -> whiteKingMoved = true;
            case "♚" -> blackKingMoved = true;
            case "♖" -> {
                if (row == 7 && col == 0) {
                    whiteRookA1Moved = true;
                } else if (row == 7 && col == 7) {
                    whiteRookH1Moved = true;
                }
            }
            case "♜" -> {
                if (row == 0 && col == 0) {
                    blackRookA8Moved = true;
                } else if (row == 0 && col == 7) {
                    blackRookH8Moved = true;
                }
            }
        }
    }

    private void updateGameStateAfterMove() {
        boolean sideToMoveInCheck = isKingInCheck(whiteTurn);
        boolean sideToMoveHasMove = hasAnyLegalMove(whiteTurn);

        if (sideToMoveInCheck && !sideToMoveHasMove) {
            gameOver = true;
            String winner = whiteTurn ? "Black" : "White";
            setStatus("CHECKMATE! " + winner + " wins.", ERROR_STYLE);
            return;
        }

        if (!sideToMoveInCheck && !sideToMoveHasMove) {
            gameOver = true;
            setStatus("STALEMATE! Draw.", ERROR_STYLE);
            return;
        }

        if (sideToMoveInCheck) {
            String checkedPlayer = whiteTurn ? "White" : "Black";
            setStatus(checkedPlayer + " is in CHECK!", CHECK_STYLE);
            return;
        }

        updateTurnIndicator();
    }

    private void promotePawnIfNeeded(String movingPiece, int row, int col) {
        if (movingPiece.equals("♙") && row == 0) {
            boardUI[row][col].setText("♕");
        } else if (movingPiece.equals("♟") && row == 7) {
            boardUI[row][col].setText("♛");
        }
    }

    private boolean hasAnyLegalMove(boolean white) {
        for (int sRow = 0; sRow < BOARD_SIZE; sRow++) {
            for (int sCol = 0; sCol < BOARD_SIZE; sCol++) {
                String piece = boardUI[sRow][sCol].getText();

                if (piece.isEmpty() || !isPieceOfColor(piece, white)) {
                    continue;
                }

                for (int tRow = 0; tRow < BOARD_SIZE; tRow++) {
                    for (int tCol = 0; tCol < BOARD_SIZE; tCol++) {
                        if (isValidMove(piece, sRow, sCol, tRow, tCol)
                                && !wouldLeaveKingInCheck(sRow, sCol, tRow, tCol, white)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean wouldLeaveKingInCheck(int sRow, int sCol, int tRow, int tCol, boolean whiteKing) {
        String movingPiece = boardUI[sRow][sCol].getText();
        String originalTargetContent = boardUI[tRow][tCol].getText();

        boardUI[tRow][tCol].setText(movingPiece);
        boardUI[sRow][sCol].setText("");

        boolean inCheck = isKingInCheck(whiteKing);

        boardUI[sRow][sCol].setText(movingPiece);
        boardUI[tRow][tCol].setText(originalTargetContent);

        return inCheck;
    }

    private void updateTurnIndicator() {
        Platform.runLater(() -> {
            if (gameOver) {
                statusContainer.setStyle(ERROR_STYLE);
                return;
            }

            boolean isMyColorTurn = whiteTurn == playerIsWhite;

            if (isMyColorTurn) {
                statusContainer.setStyle(MY_TURN_STYLE);
                statusLabel.setText("⭐ YOUR TURN (" + (playerIsWhite ? "White" : "Black") + ") ⭐");
            } else {
                statusContainer.setStyle(OPPONENT_TURN_STYLE);
                statusLabel.setText("⏳ OPPONENT'S TURN (" + (whiteTurn ? "White" : "Black") + ") ⏳");
            }
        });
    }

    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if ("DISCONNECT".equals(line)) {
                        Platform.runLater(() -> showError("Opponent disconnected."));
                        break;
                    }

                    if (!isValidMoveMessage(line)) {
                        Platform.runLater(() -> showError("Received invalid move from server."));
                        break;
                    }

                    String[] parts = line.split(",");
                    int sRow = Integer.parseInt(parts[0]);
                    int sCol = Integer.parseInt(parts[1]);
                    int tRow = Integer.parseInt(parts[2]);
                    int tCol = Integer.parseInt(parts[3]);

                    Platform.runLater(() -> {
                        if (!gameOver) {
                            executeMoveLocally(sRow, sCol, tRow, tCol);
                            isMyTurn = true;
                            updateTurnIndicator();
                        }
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> showError("Connection lost with opponent."));
            } finally {
                closeConnection();
            }
        }
    }

    private boolean isValidMoveMessage(String line) {
        String[] parts = line.split(",");
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value >= BOARD_SIZE) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidMove(String piece, int sRow, int sCol, int tRow, int tCol) {
        if (isOutsideBoard(sRow, sCol) || isOutsideBoard(tRow, tCol)) {
            return false;
        }

        if (sRow == tRow && sCol == tCol) {
            return false;
        }

        boolean isWhite = isWhitePiece(piece);
        String targetPiece = boardUI[tRow][tCol].getText();

        if (!targetPiece.isEmpty() && isSameColor(piece, targetPiece)) {
            return false;
        }

        final boolean isDiagonalMove = Math.abs(sRow - tRow) == Math.abs(sCol - tCol);

        return switch (piece) {
            case "♖", "♜" -> (sRow == tRow || sCol == tCol) && isPathClear(sRow, sCol, tRow, tCol);
            case "♗", "♝" -> isDiagonalMove && isPathClear(sRow, sCol, tRow, tCol);
            case "♕", "♛" -> ((sRow == tRow || sCol == tCol) || isDiagonalMove) && isPathClear(sRow, sCol, tRow, tCol);
            case "♘", "♞" -> {
                int rD = Math.abs(sRow - tRow);
                int cD = Math.abs(sCol - tCol);
                yield (rD == 2 && cD == 1) || (rD == 1 && cD == 2);
            }
            case "♔", "♚" -> validateKingMove(piece, sRow, sCol, tRow, tCol);
            case "♙", "♟" -> validatePawnMove(sRow, sCol, tRow, tCol, isWhite);
            default -> false;
        };
    }

    private boolean validateKingMove(String piece, int sRow, int sCol, int tRow, int tCol) {
        if (Math.abs(sRow - tRow) <= 1 && Math.abs(sCol - tCol) <= 1) {
            return true;
        }

        if (isCastlingMove(piece, sRow, sCol, tRow, tCol)) {
            return validateCastling(piece, sRow, sCol, tRow, tCol);
        }

        return false;
    }

    private boolean validateCastling(String king, int sRow, int sCol, int tRow, int tCol) {
        boolean isWhite = king.equals("♔");

        if (isWhite && sRow != 7) {
            return false;
        }

        if (!isWhite && sRow != 0) {
            return false;
        }

        if (sCol != 4 || sRow != tRow) {
            return false;
        }

        if (isWhite && whiteKingMoved) {
            return false;
        }

        if (!isWhite && blackKingMoved) {
            return false;
        }

        boolean kingSide = tCol > sCol;
        int rookCol = kingSide ? 7 : 0;
        int rookTargetCol = kingSide ? 5 : 3;
        int kingPassCol = kingSide ? 5 : 3;
        String expectedRook = isWhite ? "♖" : "♜";

        if (kingSide) {
            if (isWhite && whiteRookH1Moved) {
                return false;
            }

            if (!isWhite && blackRookH8Moved) {
                return false;
            }
        } else {
            if (isWhite && whiteRookA1Moved) {
                return false;
            }

            if (!isWhite && blackRookA8Moved) {
                return false;
            }
        }

        if (!boardUI[sRow][rookCol].getText().equals(expectedRook)) {
            return false;
        }

        int step = kingSide ? 1 : -1;
        for (int col = sCol + step; col != rookCol; col += step) {
            if (!boardUI[sRow][col].getText().isEmpty()) {
                return false;
            }
        }

        if (isKingInCheck(isWhite)) {
            return false;
        }

        if (wouldLeaveKingInCheck(sRow, sCol, sRow, kingPassCol, isWhite)) {
            return false;
        }

        return !wouldLeaveKingInCheck(sRow, sCol, tRow, tCol, isWhite)
                && boardUI[sRow][rookTargetCol].getText().isEmpty();
    }

    private boolean validatePawnMove(int sRow, int sCol, int tRow, int tCol, boolean isWhite) {
        int direction = isWhite ? -1 : 1;
        int startRow = isWhite ? 6 : 1;
        String target = boardUI[tRow][tCol].getText();

        if (sCol == tCol && tRow == sRow + direction && target.isEmpty()) {
            return true;
        }

        if (sCol == tCol
                && sRow == startRow
                && tRow == sRow + (2 * direction)
                && target.isEmpty()
                && boardUI[sRow + direction][sCol].getText().isEmpty()) {
            return true;
        }

        return Math.abs(sCol - tCol) == 1
                && tRow == sRow + direction
                && !target.isEmpty()
                && !isPieceOfColor(target, isWhite);
    }

    private boolean canAttackSquare(String piece, int sRow, int sCol, int tRow, int tCol) {
        boolean isWhite = isWhitePiece(piece);
        final boolean isDiagonalMove = Math.abs(sRow - tRow) == Math.abs(sCol - tCol);

        return switch (piece) {
            case "♖", "♜" -> (sRow == tRow || sCol == tCol) && isPathClear(sRow, sCol, tRow, tCol);
            case "♗", "♝" -> isDiagonalMove && isPathClear(sRow, sCol, tRow, tCol);
            case "♕", "♛" -> ((sRow == tRow || sCol == tCol) || isDiagonalMove) && isPathClear(sRow, sCol, tRow, tCol);
            case "♘", "♞" -> {
                int rD = Math.abs(sRow - tRow);
                int cD = Math.abs(sCol - tCol);
                yield (rD == 2 && cD == 1) || (rD == 1 && cD == 2);
            }
            case "♔", "♚" -> Math.abs(sRow - tRow) <= 1 && Math.abs(sCol - tCol) <= 1;
            case "♙", "♟" -> {
                int direction = isWhite ? -1 : 1;
                yield Math.abs(sCol - tCol) == 1 && tRow == sRow + direction;
            }
            default -> false;
        };
    }

    private boolean isPathClear(int sRow, int sCol, int tRow, int tCol) {
        int rStep = Integer.compare(tRow, sRow);
        int cStep = Integer.compare(tCol, sCol);
        int r = sRow + rStep;
        int c = sCol + cStep;

        while (r != tRow || c != tCol) {
            if (!boardUI[r][c].getText().isEmpty()) {
                return false;
            }

            r += rStep;
            c += cStep;
        }

        return true;
    }

    private boolean isKingInCheck(boolean isWhiteKing) {
        String kingSymbol = isWhiteKing ? "♔" : "♚";
        int kingRow = -1;
        int kingCol = -1;

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

        if (kingRow == -1) {
            return true;
        }

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String piece = boardUI[r][c].getText();
                if (!piece.isEmpty()
                        && isPieceOfColor(piece, !isWhiteKing)
                        && canAttackSquare(piece, r, c, kingRow, kingCol)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isOutsideBoard(int row, int col) {
        return row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE;
    }

    private boolean isSameColor(String firstPiece, String secondPiece) {
        return isWhitePiece(firstPiece) == isWhitePiece(secondPiece);
    }

    private boolean isWhitePiece(String piece) {
        return WHITE_PIECES.contains(piece);
    }

    private boolean isPieceOfColor(String piece, boolean white) {
        boolean isWhitePiece = WHITE_PIECES.contains(piece);
        boolean isBlackPiece = BLACK_PIECES.contains(piece);
        return (white && isWhitePiece) || (!white && isBlackPiece);
    }

    private void clearSelection() {
        bgTiles[selectedRow][selectedCol].setFill(getTileColor(selectedRow, selectedCol));
        selectedRow = -1;
        selectedCol = -1;
    }

    private Color getTileColor(int row, int col) {
        boolean isLight = (row + col) % 2 == 0;
        return Color.valueOf(isLight ? LIGHT_TILE : DARK_TILE);
    }

    private void setStatus(String message, String style) {
        statusContainer.setStyle(style);
        statusLabel.setText(message);
    }

    private void showError(String message) {
        gameOver = true;
        setStatus(message, ERROR_STYLE);
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Application is shutting down or connection is already broken.
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}