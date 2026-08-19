package com.example.javagame.caromnetwork;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class CaromServer {
    private static final int PORT = 12346;

    public static void main(String[] args) {
        System.out.println("Carom Server started on port " + PORT + ". Waiting for players...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket player1Socket = serverSocket.accept();
                System.out.println("Player 1 connected from " + player1Socket.getInetAddress());

                Socket player2Socket = serverSocket.accept();
                System.out.println("Player 2 connected from " + player2Socket.getInetAddress());

                Thread gameThread = new Thread(
                        () -> handleGame(player1Socket, player2Socket),
                        "Carom-Game-" + player1Socket.getPort() + "-" + player2Socket.getPort()
                );
                gameThread.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void handleGame(Socket player1Socket, Socket player2Socket) {
        try (
                Socket whiteSocket = player1Socket;
                Socket blackSocket = player2Socket;
                PrintWriter whiteOut = new PrintWriter(whiteSocket.getOutputStream(), true);
                BufferedReader whiteIn = new BufferedReader(new InputStreamReader(whiteSocket.getInputStream()));
                PrintWriter blackOut = new PrintWriter(blackSocket.getOutputStream(), true);
                BufferedReader blackIn = new BufferedReader(new InputStreamReader(blackSocket.getInputStream()))
        ) {
            whiteOut.println("WHITE");
            blackOut.println("BLACK");

            System.out.println("Both carom players connected! Game starting...");

            Thread whiteToBlack = new Thread(
                    () -> relayMessages(whiteIn, blackOut, "White", "Black"),
                    "Carom-Relay-White-To-Black"
            );

            Thread blackToWhite = new Thread(
                    () -> relayMessages(blackIn, whiteOut, "Black", "White"),
                    "Carom-Relay-Black-To-White"
            );

            whiteToBlack.start();
            blackToWhite.start();

            whiteToBlack.join();
            blackToWhite.join();
        } catch (IOException e) {
            System.out.println("Carom game ended because of a connection error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Carom game thread interrupted.");
        } finally {
            System.out.println("Carom game session closed.");
        }
    }

    private static void relayMessages(
            BufferedReader reader,
            PrintWriter writer,
            String sourcePlayer,
            String targetPlayer
    ) {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                writer.println(message);
            }

            writer.println("DISCONNECT");
            System.out.println(sourcePlayer + " disconnected. Notified " + targetPlayer + ".");
        } catch (IOException e) {
            writer.println("DISCONNECT");
            System.out.println(sourcePlayer + " disconnected unexpectedly.");
        }
    }
}