package com.example.javagame.chessnetwork;

import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;

public class IncomingReader implements Runnable {

    private ChessNetworkApp app;
    private BufferedReader in;

    public IncomingReader(ChessNetworkApp app, BufferedReader in) {
        this.app = app;
        this.in = in;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if ("DISCONNECT".equals(line)) {
                    Platform.runLater(() -> app.showError("Opponent disconnected."));
                    break;
                }

                if (!app.isValidMoveMessage(line)) {
                    Platform.runLater(() -> app.showError("Received invalid move from server."));
                    break;
                }

                String[] parts = line.split(",");
                int sRow = Integer.parseInt(parts[0]);
                int sCol = Integer.parseInt(parts[1]);
                int tRow = Integer.parseInt(parts[2]);
                int tCol = Integer.parseInt(parts[3]);

                Platform.runLater(() -> {
                    if (!app.gameOver) {
                        app.executeMoveLocally(sRow, sCol, tRow, tCol);
                        app.isMyTurn = true;
                        app.updateTurnIndicator();
                    }
                });
            }
        } catch (IOException e) {
            Platform.runLater(() -> app.showError("Connection lost with opponent."));
        } finally {
            app.closeConnection();
        }
    }
}
