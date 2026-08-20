package com.example.javagame.caromnetwork;

import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;

public class IncomingReader implements Runnable {
    private CaromNetworkGame game;
    private BufferedReader in;

    public IncomingReader(CaromNetworkGame game, BufferedReader in) {
        this.game = game;
        this.in = in;
    }


    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if ("DISCONNECT".equals(line)) {
                    Platform.runLater(() -> game.showNetworkError("Opponent disconnected."));
                    break;
                }

                if ("RESET".equals(line)) {
                    Platform.runLater(game::newGame);
                    continue;
                }

                if (line.startsWith("STATE|")) {
                    String stateMessage = line;
                    Platform.runLater(() -> game.applyStateMessage(stateMessage));
                    continue;
                }

                if (line.startsWith("PLACE|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length != 3) {
                        Platform.runLater(() -> game.showNetworkError("Received invalid striker placement."));
                        break;
                    }

                    double strikerX = Double.parseDouble(parts[1]);
                    double strikerY = Double.parseDouble(parts[2]);

                    Platform.runLater(() -> game.applyRemoteStrikerPlacement(strikerX, strikerY));
                    continue;
                }

                if (line.startsWith("AIM|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length != 6) {
                        Platform.runLater(() -> game.showNetworkError("Received invalid aim data."));
                        break;
                    }

                    double strikerX = Double.parseDouble(parts[1]);
                    double strikerY = Double.parseDouble(parts[2]);
                    double dirX = Double.parseDouble(parts[3]);
                    double dirY = Double.parseDouble(parts[4]);
                    double power = Double.parseDouble(parts[5]);

                    Platform.runLater(() -> {
                        game.applyRemoteStrikerPlacement(strikerX, strikerY);
                        game.showRemoteAimVisuals(strikerX, strikerY, dirX, dirY, power);
                    });
                    continue;
                }

                if ("CLEAR_AIM".equals(line)) {
                    Platform.runLater(game::hideAimVisuals);
                    continue;
                }

                if (!line.startsWith("SHOT|")) {
                    Platform.runLater(() -> game.showNetworkError("Received invalid carom message."));
                    break;
                }

                String[] parts = line.split("\\|");
                if (parts.length != 6) {
                    Platform.runLater(() -> game.showNetworkError("Received invalid shot data."));
                    break;
                }

                double strikerX = Double.parseDouble(parts[1]);
                double strikerY = Double.parseDouble(parts[2]);
                double dirX = Double.parseDouble(parts[3]);
                double dirY = Double.parseDouble(parts[4]);
                double speed = Double.parseDouble(parts[5]);

                Platform.runLater(() -> game.applyRemoteShot(strikerX, strikerY, dirX, dirY, speed));
            }
        } catch (IOException e) {
            Platform.runLater(() -> game.showNetworkError("Connection lost with opponent."));
        } catch (NumberFormatException e) {
            Platform.runLater(() -> game.showNetworkError("Received corrupted shot data."));
        } finally {
            game.closeConnection();
        }
    }
}
