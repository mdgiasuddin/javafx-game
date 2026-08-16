package com.example.javagame.chessnetwork;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ChessServer {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Chess Server started on port " + PORT + ". Waiting for players...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // Accept Player 1 (White)
            Socket player1Socket = serverSocket.accept();
            System.out.println("Player 1 connected from " + player1Socket.getInetAddress());
            PrintWriter out1 = new PrintWriter(player1Socket.getOutputStream(), true);
            BufferedReader in1 = new BufferedReader(new InputStreamReader(player1Socket.getInputStream()));
            out1.println("WHITE");

            // Accept Player 2 (Black)
            Socket player2Socket = serverSocket.accept();
            System.out.println("Player 2 connected from " + player2Socket.getInetAddress());
            PrintWriter out2 = new PrintWriter(player2Socket.getOutputStream(), true);
            BufferedReader in2 = new BufferedReader(new InputStreamReader(player2Socket.getInputStream()));
            out2.println("BLACK");

            System.out.println("Both players connected! Game starting...");

            // Relay thread for Player 1 -> Player 2
            new Thread(() -> relayMessages(in1, out2)).start();

            // Relay thread for Player 2 -> Player 1
            new Thread(() -> relayMessages(in2, out1)).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void relayMessages(BufferedReader reader, PrintWriter writer) {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                writer.println(message);
            }
        } catch (IOException e) {
            System.out.println("A player disconnected.");
        }
    }
}
