package com.example.javagame.caromnetwork;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Hot-seat two player Carom.
 * <p>
 * Player 1 sits at the bottom of the board and owns the light coins, Player 2 sits at the
 * top and owns the dark coins. A turn is: slide the striker along your own baseline, then
 * drag away from it to aim and charge a shot. Pocketing one of your own coins earns a point
 * and another shot; anything else hands the board over.
 */
public class CaromNetworkGame extends Application {
    private static final int DEFAULT_PORT = 12346;
    private static final String DEFAULT_HOST = "127.0.0.1";

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private boolean playerIsWhite = true;
    private boolean isMyTurn = false;
    /**
     * True while the shot in flight was fired by this client. The shooter's client is the only
     * one allowed to decide how a shot ended and to broadcast the resulting board, so this is
     * set the moment a shot starts rather than cleared once one finishes - a flag that is only
     * cleared on resolution goes stale whenever a state message cuts a simulation short, and a
     * stale flag silently suppresses the next broadcast.
     */
    private boolean shotOwnedByMe = false;
    /**
     * True once a shot fired by the opponent has come to rest locally, until their authoritative
     * state message lands. Both clients simulate every shot, but frame timing differs, so the two
     * simulations can disagree about the outcome; the non-shooter waits instead of acting on its
     * own copy.
     */
    private boolean awaitingRemoteState = false;

    // ---- Layout -----------------------------------------------------------------------
    private static final double WIDTH = 660;
    private static final double HEIGHT = 836;
    private static final double BOARD_SIZE = 520;
    private static final double BOARD_OFFSET_X = 70;
    private static final double BOARD_OFFSET_Y = 150;

    private static final double COIN_RADIUS = 11;
    private static final double STRIKER_RADIUS = 16;
    private static final double POCKET_RADIUS = 20;
    private static final double POCKET_INSET = 21;
    private static final double BASELINE_INSET = 62;
    private static final double BASELINE_HALF_WIDTH = 170;

    // ---- Rules / feel -----------------------------------------------------------------
    private static final int COINS_PER_PLAYER = 9;
    private static final int QUEEN_BONUS = 5;
    private static final double MAX_DRAG = 150;

    /**
     * Speeds are in pixels per frame at 60fps. A full-power strike leaves at about 525px/s and
     * takes roughly a second to cross the board, so shots still read as a glide rather than a
     * flick.
     */
    private static final double MAX_SPEED = 11.25;
    private static final double MIN_SPEED = 0.9;

    /**
     * Coins slide, they do not roll, so the board takes a fixed bite out of the speed every
     * frame rather than a fixed percentage. Constant deceleration is what a sliding piece
     * actually does: it holds its pace through most of the glide and then settles decisively,
     * instead of creeping towards zero forever the way a multiplicative decay does. Kept low
     * so a hard shot stays alive for several seconds.
     */
    private static final double DECELERATION = 0.031;

    private static final double CUSHION_BOUNCE = 0.74;
    private static final double COIN_BOUNCE = 0.94;
    private static final double STRIKER_MASS = 2.2;
    private static final double COIN_MASS = 1.0;
    private static final double REST_SPEED = 0.02;

    // ---- Palette ----------------------------------------------------------------------
    private static final Color LIGHT_COIN = Color.web("#f6e7c4");
    private static final Color DARK_COIN = Color.web("#2b2118");
    private static final Color QUEEN_COIN = Color.web("#c1121f");
    private static final Color ACTIVE_GLOW = Color.web("#e94560");
    private static final Color IDLE_STROKE = Color.web("#0f3460");

    private enum Kind {STRIKER, LIGHT, DARK, QUEEN}

    private enum Phase {READY, SHOOTING, GAME_OVER}

    private enum DragMode {NONE, UNDECIDED, MOVE, AIM}

    private Pane root;
    private Group boardGroup;
    private Rotate boardRotation;
    private Group pieceLayer;
    private final List<CaromPiece> pieces = new ArrayList<>();
    private CaromPiece striker;

    private Phase phase = Phase.READY;
    private DragMode dragMode = DragMode.NONE;
    private long lastFrameNanos;
    private double pressX, pressY;
    private double aimDirX, aimDirY, aimPower;

    // ---- Players ----------------------------------------------------------------------
    private final Player p1 = new Player("Player 1", Kind.LIGHT, LIGHT_COIN);
    private final Player p2 = new Player("Player 2", Kind.DARK, DARK_COIN);
    private boolean player1Turn = true;

    // ---- Queen bookkeeping ------------------------------------------------------------
    private boolean queenOnBoard = true;
    private Player queenPendingCoverBy;
    private Player queenOwner;

    // ---- Shot bookkeeping -------------------------------------------------------------
    private final List<Kind> pocketedThisShot = new ArrayList<>();
    private boolean strikerPocketedThisShot;
    private boolean strikerTouchedCoin;

    // ---- Scene nodes ------------------------------------------------------------------
    private Rectangle strikerZone;
    private Line aimLine;
    private Line dragLine;
    private Circle aimTip;
    private Rectangle powerTrack, powerFill;
    private Label statusLabel, hintLabel, queenLabel;
    private Group overlay;
    private Label overlayTitle, overlaySubtitle;

    @Override
    public void start(Stage primaryStage) {
        root = new Pane();
        root.setFocusTraversable(true);

        boardGroup = new Group();
        boardRotation = new Rotate(0, WIDTH / 2, HEIGHT / 2);
        boardGroup.getTransforms().add(boardRotation);
        root.getChildren().add(boardGroup);

        Scene scene = new Scene(root, WIDTH, HEIGHT);

        buildBackground();
        buildBoard();
        buildAimVisuals();
        buildHud();
        buildOverlay();

        root.setOnMousePressed(this::onMousePressed);
        root.setOnMouseDragged(this::onMouseDragged);
        root.setOnMouseReleased(this::onMouseReleased);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.R && connected && playerIsWhite) {
                sendMessage("RESET");
                newGame();
            }
        });

        newGame();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (phase != Phase.SHOOTING || lastFrameNanos == 0) {
                    lastFrameNanos = now;
                    return;
                }
                double elapsedSeconds = (now - lastFrameNanos) / 1_000_000_000.0;
                lastFrameNanos = now;

                stepPhysics(Math.min(elapsedSeconds * 60.0, 3.0));
                if (!anythingMoving()) resolveShot();
            }
        }.start();

        primaryStage.setTitle("Network Carrom");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        root.requestFocus();

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

                Thread incomingThread = new Thread(new IncomingReader(), "Carom-Incoming-Reader");
                incomingThread.setDaemon(true);
                incomingThread.start();
            } catch (Exception e) {
                Platform.runLater(() -> showNetworkError("Could not connect to carom server."));
                closeConnection();
            }
        }, "Carom-Network-Setup");

        setupThread.setDaemon(true);
        setupThread.start();
    }

    private void handleColorAssignment(String colorAssignment) {
        connected = true;
        playerIsWhite = "WHITE".equals(colorAssignment);
        isMyTurn = playerIsWhite;

        p1.name = playerIsWhite ? "You" : "Opponent";
        p2.name = playerIsWhite ? "Opponent" : "You";

        boardRotation.setAngle(playerIsWhite ? 0 : 180);
        boardGroup.setLayoutX(0);
        boardGroup.setLayoutY(0);

        updateHud();
        updateTurnText();
    }

    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void showNetworkError(String message) {
        phase = Phase.GAME_OVER;
        dragMode = DragMode.NONE;
        hideAimVisuals();
        statusLabel.setText(message);
        hintLabel.setText("");
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Connection is already closed.
        }
    }

    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if ("DISCONNECT".equals(line)) {
                        Platform.runLater(() -> showNetworkError("Opponent disconnected."));
                        break;
                    }

                    if ("RESET".equals(line)) {
                        Platform.runLater(CaromNetworkGame.this::newGame);
                        continue;
                    }

                    if (line.startsWith("STATE|")) {
                        String stateMessage = line;
                        Platform.runLater(() -> applyStateMessage(stateMessage));
                        continue;
                    }

                    if (line.startsWith("PLACE|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length != 3) {
                            Platform.runLater(() -> showNetworkError("Received invalid striker placement."));
                            break;
                        }

                        double strikerX = Double.parseDouble(parts[1]);
                        double strikerY = Double.parseDouble(parts[2]);

                        Platform.runLater(() -> applyRemoteStrikerPlacement(strikerX, strikerY));
                        continue;
                    }

                    if (line.startsWith("AIM|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length != 6) {
                            Platform.runLater(() -> showNetworkError("Received invalid aim data."));
                            break;
                        }

                        double strikerX = Double.parseDouble(parts[1]);
                        double strikerY = Double.parseDouble(parts[2]);
                        double dirX = Double.parseDouble(parts[3]);
                        double dirY = Double.parseDouble(parts[4]);
                        double power = Double.parseDouble(parts[5]);

                        Platform.runLater(() -> {
                            applyRemoteStrikerPlacement(strikerX, strikerY);
                            showRemoteAimVisuals(strikerX, strikerY, dirX, dirY, power);
                        });
                        continue;
                    }

                    if ("CLEAR_AIM".equals(line)) {
                        Platform.runLater(CaromNetworkGame.this::hideAimVisuals);
                        continue;
                    }

                    if (!line.startsWith("SHOT|")) {
                        Platform.runLater(() -> showNetworkError("Received invalid carom message."));
                        break;
                    }

                    String[] parts = line.split("\\|");
                    if (parts.length != 6) {
                        Platform.runLater(() -> showNetworkError("Received invalid shot data."));
                        break;
                    }

                    double strikerX = Double.parseDouble(parts[1]);
                    double strikerY = Double.parseDouble(parts[2]);
                    double dirX = Double.parseDouble(parts[3]);
                    double dirY = Double.parseDouble(parts[4]);
                    double speed = Double.parseDouble(parts[5]);

                    Platform.runLater(() -> applyRemoteShot(strikerX, strikerY, dirX, dirY, speed));
                }
            } catch (IOException e) {
                Platform.runLater(() -> showNetworkError("Connection lost with opponent."));
            } catch (NumberFormatException e) {
                Platform.runLater(() -> showNetworkError("Received corrupted shot data."));
            } finally {
                closeConnection();
            }
        }
    }

    private void applyStateMessage(String line) {
        if (phase == Phase.SHOOTING && shotOwnedByMe) {
            // Our own shot is still travelling, so this state predates it and would rewind us.
            return;
        }

        String[] parts = line.split("\\|");

        if (parts.length < 10) {
            showNetworkError("Received invalid board state.");
            return;
        }

        try {
            int index = 1;

            player1Turn = Boolean.parseBoolean(parts[index++]);
            p1.pocketed = Integer.parseInt(parts[index++]);
            p2.pocketed = Integer.parseInt(parts[index++]);
            queenOnBoard = Boolean.parseBoolean(parts[index++]);
            queenOwner = playerFromIndex(Integer.parseInt(parts[index++]));
            queenPendingCoverBy = playerFromIndex(Integer.parseInt(parts[index++]));
            phase = Phase.valueOf(parts[index++]);

            int pieceCount = Integer.parseInt(parts[index++]);
            int expectedLength = 9 + pieceCount + 1;
            if (parts.length != expectedLength) {
                showNetworkError("Received invalid board state.");
                return;
            }

            pieces.clear();
            pieceLayer.getChildren().clear();

            for (int i = 0; i < pieceCount; i++) {
                CaromPiece piece = parsePiece(parts[index++]);
                addPiece(piece);
            }

            striker = parsePiece(parts[index]);
            addPiece(striker);

            dragMode = DragMode.NONE;
            hideAimVisuals();

            // The shot this state describes is over, so no local shot bookkeeping survives it.
            pocketedThisShot.clear();
            strikerPocketedThisShot = false;
            strikerTouchedCoin = false;
            shotOwnedByMe = false;
            awaitingRemoteState = false;

            strikerZone.setY(baselineY(player1Turn) - STRIKER_RADIUS);
            strikerZone.setVisible(phase != Phase.GAME_OVER);

            updateHud();
            updateTurnText();

            if (phase == Phase.GAME_OVER) {
                Player winner = findWinner();
                if (winner != null) endGame(winner);
            } else {
                overlay.setVisible(false);
            }
        } catch (RuntimeException e) {
            showNetworkError("Received corrupted board state.");
        }
    }

    private Player playerFromIndex(int index) {
        return switch (index) {
            case 1 -> p1;
            case 2 -> p2;
            default -> null;
        };
    }

    private CaromPiece parsePiece(String encodedPiece) {
        String[] values = encodedPiece.split(",");

        if (values.length != 5) {
            throw new IllegalArgumentException("Invalid piece data");
        }

        Kind kind = Kind.valueOf(values[0]);
        double x = Double.parseDouble(values[1]);
        double y = Double.parseDouble(values[2]);
        double vx = Double.parseDouble(values[3]);
        double vy = Double.parseDouble(values[4]);

        double radius = kind == Kind.STRIKER ? STRIKER_RADIUS : COIN_RADIUS;
        CaromPiece piece = new CaromPiece(x, y, radius, kind);
        piece.vx = vx;
        piece.vy = vy;
        piece.updateNodePosition();

        return piece;
    }

    private void applyRemoteShot(double strikerX, double strikerY, double dirX, double dirY, double speed) {
        // Recorded before the guard: even a shot we cannot apply is one we do not own, and we
        // must never broadcast a board for a shot the opponent fired.
        shotOwnedByMe = false;

        if (phase != Phase.READY) {
            return;
        }

        if (!pieces.contains(striker)) {
            striker = new CaromPiece(strikerX, strikerY, STRIKER_RADIUS, Kind.STRIKER);
            addPiece(striker);
        }

        striker.x = nearestLegalStrikerX(strikerX, strikerY);
        striker.y = strikerY;
        striker.vx = dirX * speed;
        striker.vy = dirY * speed;
        striker.updateNodePosition();

        pocketedThisShot.clear();
        strikerPocketedThisShot = false;
        strikerTouchedCoin = false;
        phase = Phase.SHOOTING;
        dragMode = DragMode.NONE;
        hideAimVisuals();
        hintLabel.setText("");
    }

    private boolean gameEnded() {
        return phase == Phase.GAME_OVER;
    }

    private void updateTurnText() {
        if (phase == Phase.GAME_OVER) {
            return;
        }

        boolean myColorTurn = player1Turn == playerIsWhite;
        isMyTurn = connected && myColorTurn && !awaitingRemoteState;

        if (isMyTurn) {
            statusLabel.setText("YOUR TURN - " + (playerIsWhite ? "light coins" : "dark coins"));
            hintLabel.setText("Place the striker, pull back, and release to shoot");
        } else if (awaitingRemoteState) {
            statusLabel.setText("SYNCING SHOT RESULT");
            hintLabel.setText("Waiting for the opponent's board update");
        } else {
            statusLabel.setText("OPPONENT'S TURN");
            hintLabel.setText("Wait for your opponent to shoot");
        }
    }

    // =====================================================================================
    // Board geometry helpers
    // =====================================================================================

    private static double boardLeft() {
        return BOARD_OFFSET_X;
    }

    private static double boardTop() {
        return BOARD_OFFSET_Y;
    }

    private static double boardRight() {
        return BOARD_OFFSET_X + BOARD_SIZE;
    }

    private static double boardBottom() {
        return BOARD_OFFSET_Y + BOARD_SIZE;
    }

    private static double centerX() {
        return BOARD_OFFSET_X + BOARD_SIZE / 2;
    }

    private static double centerY() {
        return BOARD_OFFSET_Y + BOARD_SIZE / 2;
    }

    private static double[][] pocketCenters() {
        return new double[][]{
                {boardLeft() + POCKET_INSET, boardTop() + POCKET_INSET},
                {boardRight() - POCKET_INSET, boardTop() + POCKET_INSET},
                {boardLeft() + POCKET_INSET, boardBottom() - POCKET_INSET},
                {boardRight() - POCKET_INSET, boardBottom() - POCKET_INSET},
        };
    }

    /**
     * Baseline the given player shoots from.
     */
    private static double baselineY(boolean forPlayer1) {
        return forPlayer1 ? boardBottom() - BASELINE_INSET : boardTop() + BASELINE_INSET;
    }

    private static double strikerMinX() {
        return centerX() - BASELINE_HALF_WIDTH + STRIKER_RADIUS;
    }

    private static double strikerMaxX() {
        return centerX() + BASELINE_HALF_WIDTH - STRIKER_RADIUS;
    }

    private double boardMouseX(MouseEvent e) {
        return boardGroup.sceneToLocal(e.getSceneX(), e.getSceneY()).getX();
    }

    private double boardMouseY(MouseEvent e) {
        return boardGroup.sceneToLocal(e.getSceneX(), e.getSceneY()).getY();
    }

    // =====================================================================================
    // Scene construction
    // =====================================================================================

    private void buildBackground() {
        Rectangle bg = new Rectangle(WIDTH, HEIGHT);
        bg.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#12122a")),
                new Stop(1, Color.web("#1f1f3d"))));

        root.getChildren().add(0, bg);
    }

    private void buildBoard() {
        Rectangle frame = new Rectangle(boardLeft() - 16, boardTop() - 16, BOARD_SIZE + 32, BOARD_SIZE + 32);
        frame.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#a9642a")),
                new Stop(1, Color.web("#7a4318"))));
        frame.setArcWidth(24);
        frame.setArcHeight(24);
        frame.setEffect(new DropShadow(24, Color.web("#000000cc")));
        boardGroup.getChildren().add(frame);

        Rectangle surface = new Rectangle(boardLeft(), boardTop(), BOARD_SIZE, BOARD_SIZE);
        surface.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fbe8b8")),
                new Stop(0.5, Color.web("#f2d99f")),
                new Stop(1, Color.web("#e0c087"))));
        boardGroup.getChildren().add(surface);

        // Playing-area border lines
        for (double inset : new double[]{POCKET_INSET + 16, POCKET_INSET + 20}) {
            Rectangle line = new Rectangle(boardLeft() + inset, boardTop() + inset,
                    BOARD_SIZE - 2 * inset, BOARD_SIZE - 2 * inset);
            line.setFill(Color.TRANSPARENT);
            line.setStroke(Color.web("#8d6b3f"));
            line.setStrokeWidth(inset > POCKET_INSET + 18 ? 1 : 2);
            boardGroup.getChildren().add(line);
        }

        // Centre circle group
        Circle outerRing = new Circle(centerX(), centerY(), 58, Color.TRANSPARENT);
        outerRing.setStroke(Color.web("#8d6b3f"));
        outerRing.setStrokeWidth(1.6);
        Circle innerRing = new Circle(centerX(), centerY(), 48, Color.TRANSPARENT);
        innerRing.setStroke(Color.web("#c8433f"));
        innerRing.setStrokeWidth(1.2);
        Circle queenSpot = new Circle(centerX(), centerY(), 13, Color.web("#c8433f44"));
        queenSpot.setStroke(Color.web("#c8433f"));
        boardGroup.getChildren().addAll(outerRing, innerRing, queenSpot);

        // Decorative diagonal arrows toward the corners
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                Line diag = new Line(
                        centerX() + sx * 70, centerY() + sy * 70,
                        centerX() + sx * 150, centerY() + sy * 150);
                diag.setStroke(Color.web("#b08c55"));
                diag.setStrokeWidth(1.2);
                boardGroup.getChildren().add(diag);
            }
        }

        // Baselines for both players
        for (boolean forP1 : new boolean[]{true, false}) {
            double y = baselineY(forP1);
            double x0 = centerX() - BASELINE_HALF_WIDTH;
            double x1 = centerX() + BASELINE_HALF_WIDTH;
            for (double dy : new double[]{-STRIKER_RADIUS, STRIKER_RADIUS}) {
                Line line = new Line(x0, y + dy, x1, y + dy);
                line.setStroke(Color.web("#c8433f"));
                line.setStrokeWidth(2);
                boardGroup.getChildren().add(line);
            }
            for (double x : new double[]{x0, x1}) {
                Circle end = new Circle(x, y, STRIKER_RADIUS, Color.TRANSPARENT);
                end.setStroke(Color.web("#c8433f"));
                end.setStrokeWidth(2);
                boardGroup.getChildren().add(end);
            }
        }

        // Pockets
        for (double[] c : pocketCenters()) {
            Circle rim = new Circle(c[0], c[1], POCKET_RADIUS + 4, Color.web("#5d3a16"));
            Circle hole = new Circle(c[0], c[1], POCKET_RADIUS, Color.web("#0b0b0b"));
            hole.setEffect(new DropShadow(8, Color.web("#000000")));
            boardGroup.getChildren().addAll(rim, hole);
        }

        // Highlight band showing where the active player may place the striker
        strikerZone = new Rectangle(centerX() - BASELINE_HALF_WIDTH, 0,
                BASELINE_HALF_WIDTH * 2, STRIKER_RADIUS * 2);
        strikerZone.setFill(Color.web("#e9456022"));
        strikerZone.setStroke(Color.web("#e9456088"));
        strikerZone.setArcWidth(STRIKER_RADIUS * 2);
        strikerZone.setArcHeight(STRIKER_RADIUS * 2);
        boardGroup.getChildren().add(strikerZone);

        pieceLayer = new Group();
        boardGroup.getChildren().add(pieceLayer);
    }

    private void buildAimVisuals() {
        dragLine = new Line();
        dragLine.setStroke(Color.web("#ffffff55"));
        dragLine.setStrokeWidth(2);
        dragLine.getStrokeDashArray().addAll(4.0, 6.0);
        dragLine.setVisible(false);

        aimLine = new Line();
        aimLine.setStroke(ACTIVE_GLOW);
        aimLine.setStrokeWidth(3);
        aimLine.getStrokeDashArray().addAll(9.0, 6.0);
        aimLine.setVisible(false);

        aimTip = new Circle(0, 0, 5, ACTIVE_GLOW);
        aimTip.setVisible(false);

        boardGroup.getChildren().addAll(dragLine, aimLine, aimTip);
    }

    private void buildHud() {
        p2.card = profileCard(18, IDLE_STROKE);
        p2.nameLabel = hudLabel(p2.name + " - dark coins", 62, 32, 14, Color.WHITE, true);
        p2.scoreLabel = hudLabel("Score 0", 430, 26, 13, ACTIVE_GLOW, true);
        p2.coinsLabel = hudLabel("Coins left 9", 430, 46, 12, Color.web("#9aa5c4"), false);

        p1.card = profileCard(HEIGHT - 78, ACTIVE_GLOW);
        p1.nameLabel = hudLabel(p1.name + " - light coins", 62, HEIGHT - 64, 14, Color.WHITE, true);
        p1.scoreLabel = hudLabel("Score 0", 430, HEIGHT - 70, 13, ACTIVE_GLOW, true);
        p1.coinsLabel = hudLabel("Coins left 9", 430, HEIGHT - 50, 12, Color.web("#9aa5c4"), false);

        p2.dot = new Circle(48, 46, 8, p2.color);
        p2.dot.setStroke(Color.web("#000000"));
        p1.dot = new Circle(48, HEIGHT - 50, 8, p1.color);
        p1.dot.setStroke(Color.web("#000000"));

        statusLabel = centeredLabel("Player 1 to break", 96, 16, Color.WHITE, true);
        hintLabel = centeredLabel("Drag the striker sideways to place it, then pull back and release to shoot",
                118, 11.5, Color.web("#8f98b8"), false);
        queenLabel = centeredLabel("Queen on board", boardBottom() + 18, 12, Color.web("#e8a0a8"), true);

        powerTrack = new Rectangle(centerX() - 110, boardBottom() + 44, 220, 10);
        powerTrack.setFill(Color.web("#00000055"));
        powerTrack.setStroke(Color.web("#ffffff22"));
        powerTrack.setArcWidth(10);
        powerTrack.setArcHeight(10);

        powerFill = new Rectangle(centerX() - 110, boardBottom() + 44, 0, 10);
        powerFill.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4cc9f0")),
                new Stop(0.6, Color.web("#f9c74f")),
                new Stop(1, Color.web("#e94560"))));
        powerFill.setArcWidth(10);
        powerFill.setArcHeight(10);

        root.getChildren().addAll(
                p2.card, p2.dot, p2.nameLabel, p2.scoreLabel, p2.coinsLabel,
                p1.card, p1.dot, p1.nameLabel, p1.scoreLabel, p1.coinsLabel,
                statusLabel, hintLabel, queenLabel, powerTrack, powerFill);
    }

    private Rectangle profileCard(double y, Color stroke) {
        Rectangle card = new Rectangle(30, y, WIDTH - 60, 60);
        card.setFill(Color.web("#16213e"));
        card.setArcWidth(16);
        card.setArcHeight(16);
        card.setStroke(stroke);
        card.setStrokeWidth(2);
        return card;
    }

    private Label hudLabel(String text, double x, double y, double size, Color fill, boolean bold) {
        Label label = new Label(text);
        label.setFont(bold ? Font.font("System", FontWeight.BOLD, size) : Font.font("System", size));
        label.setTextFill(fill);
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }

    private Label centeredLabel(String text, double y, double size, Color fill, boolean bold) {
        Label label = new Label(text);
        label.setFont(bold ? Font.font("System", FontWeight.BOLD, size) : Font.font("System", size));
        label.setTextFill(fill);
        label.setPrefWidth(WIDTH);
        label.setAlignment(Pos.CENTER);
        label.setLayoutX(0);
        label.setLayoutY(y);
        return label;
    }

    private void buildOverlay() {
        Rectangle shade = new Rectangle(WIDTH, HEIGHT, Color.web("#05050fdd"));
        overlayTitle = centeredLabel("", HEIGHT / 2 - 50, 34, Color.WHITE, true);
        overlaySubtitle = centeredLabel("", HEIGHT / 2 + 6, 15, Color.web("#c9d1e8"), false);
        Label restart = centeredLabel("Press R to play again", HEIGHT / 2 + 46, 14, ACTIVE_GLOW, true);
        overlay = new Group(shade, overlayTitle, overlaySubtitle, restart);
        overlay.setVisible(false);
        root.getChildren().add(overlay);
    }

    // =====================================================================================
    // Game setup / reset
    // =====================================================================================

    private void newGame() {
        pieces.clear();
        pieceLayer.getChildren().clear();

        p1.reset();
        p2.reset();
        queenOnBoard = true;
        queenOwner = null;
        queenPendingCoverBy = null;
        player1Turn = true;
        phase = Phase.READY;
        dragMode = DragMode.NONE;
        pocketedThisShot.clear();
        strikerPocketedThisShot = false;
        strikerTouchedCoin = false;
        shotOwnedByMe = false;
        awaitingRemoteState = false;
        overlay.setVisible(false);
        hideAimVisuals();

        layoutCoins();

        striker = new CaromPiece(centerX(), baselineY(true), STRIKER_RADIUS, Kind.STRIKER);
        addPiece(striker);

        updateHud();

        if (connected) {
            // A reset leaves the break with Player 1, so both clients must recompute who that is.
            updateTurnText();
        } else {
            statusLabel.setText(p1.name + " to break");
            hintLabel.setText("Drag the striker sideways to place it, then pull back and release to shoot");
        }
    }

    /**
     * Classic flower layout: queen in the middle, a ring of 6, then a ring of 12.
     */
    private void layoutCoins() {
        addPiece(new CaromPiece(centerX(), centerY(), COIN_RADIUS, Kind.QUEEN));

        addRing(6, 2 * COIN_RADIUS, 0);
        addRing(12, 4 * COIN_RADIUS, Math.PI / 12);
    }

    private void addRing(int count, double radius, double angleOffset) {
        for (int i = 0; i < count; i++) {
            double angle = angleOffset + 2 * Math.PI * i / count;
            double x = centerX() + radius * Math.cos(angle);
            double y = centerY() + radius * Math.sin(angle);
            addPiece(new CaromPiece(x, y, COIN_RADIUS, i % 2 == 0 ? Kind.LIGHT : Kind.DARK));
        }
    }

    private void addPiece(CaromPiece piece) {
        pieces.add(piece);
        pieceLayer.getChildren().add(piece.node);
    }

    // =====================================================================================
    // Input
    // =====================================================================================

    private void onMousePressed(MouseEvent e) {
        hideAimVisuals();

        if (!connected) {
            statusLabel.setText("Connecting to server...");
            dragMode = DragMode.NONE;
            return;
        }

        if (!isMyTurn) {
            statusLabel.setText("Wait for your opponent's turn");
            dragMode = DragMode.NONE;
            return;
        }

        if (phase != Phase.READY) {
            dragMode = DragMode.NONE;
            return;
        }

        double mouseX = boardMouseX(e);
        double mouseY = boardMouseY(e);

        pressX = mouseX;
        pressY = mouseY;

        double baseline = baselineY(player1Turn);
        boolean onStriker = Math.hypot(mouseX - striker.x, mouseY - striker.y) <= striker.radius + 14;
        boolean inBand = Math.abs(mouseY - baseline) <= STRIKER_RADIUS + 12
                && mouseX >= strikerMinX() - STRIKER_RADIUS
                && mouseX <= strikerMaxX() + STRIKER_RADIUS;

        if (inBand && !onStriker) {
            placeStrikerAt(mouseX);
            sendMessage("PLACE|" + striker.x + "|" + striker.y);
        }

        dragMode = (onStriker || inBand) ? DragMode.UNDECIDED : DragMode.NONE;
    }

    private void applyRemoteStrikerPlacement(double strikerX, double strikerY) {
        if (phase != Phase.READY) {
            return;
        }

        if (!pieces.contains(striker)) {
            striker = new CaromPiece(strikerX, strikerY, STRIKER_RADIUS, Kind.STRIKER);
            addPiece(striker);
        }

        striker.x = nearestLegalStrikerX(strikerX, strikerY);
        striker.y = strikerY;
        striker.vx = 0;
        striker.vy = 0;
        striker.updateNodePosition();
    }

    private void showRemoteAimVisuals(double strikerX, double strikerY, double dirX, double dirY, double power) {
        double guideLen = 55 + power * 190;

        aimLine.setStartX(strikerX);
        aimLine.setStartY(strikerY);
        aimLine.setEndX(strikerX + dirX * guideLen);
        aimLine.setEndY(strikerY + dirY * guideLen);
        aimLine.setVisible(true);

        aimTip.setCenterX(strikerX + dirX * guideLen);
        aimTip.setCenterY(strikerY + dirY * guideLen);
        aimTip.setVisible(true);

        double pull = power * MAX_DRAG;
        dragLine.setStartX(strikerX);
        dragLine.setStartY(strikerY);
        dragLine.setEndX(strikerX - dirX * pull);
        dragLine.setEndY(strikerY - dirY * pull);
        dragLine.setVisible(true);

        powerFill.setWidth(220 * power);
    }

    private void onMouseDragged(MouseEvent e) {
        if (!connected || !isMyTurn || phase != Phase.READY || dragMode == DragMode.NONE) {
            return;
        }

        double mouseX = boardMouseX(e);
        double mouseY = boardMouseY(e);

        if (dragMode == DragMode.UNDECIDED) {
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (Math.hypot(dx, dy) < 6) {
                return;
            }

            boolean stayedOnBaseline = Math.abs(mouseY - baselineY(player1Turn)) <= STRIKER_RADIUS + 10;
            dragMode = (stayedOnBaseline && Math.abs(dx) > Math.abs(dy)) ? DragMode.MOVE : DragMode.AIM;
        }

        if (dragMode == DragMode.MOVE) {
            placeStrikerAt(mouseX);
            hideAimVisuals();

            sendMessage("PLACE|" + striker.x + "|" + striker.y);
        } else {
            computeAim(mouseX, mouseY);
            showAimVisuals();

            sendMessage("AIM|" + striker.x + "|" + striker.y + "|" + aimDirX + "|" + aimDirY + "|" + aimPower);
        }
    }

    private void onMouseReleased(MouseEvent e) {
        DragMode mode = dragMode;
        dragMode = DragMode.NONE;
        hideAimVisuals();

        if (!connected || !isMyTurn || mode != DragMode.AIM || phase != Phase.READY) {
            return;
        }

        double mouseX = boardMouseX(e);
        double mouseY = boardMouseY(e);
        computeAim(mouseX, mouseY);

        double speed = aimPower * MAX_SPEED;
        if (speed < MIN_SPEED) {
            statusLabel.setText("Pull back further to shoot");
            return;
        }

        sendMessage("CLEAR_AIM");
        sendMessage("SHOT|" + striker.x + "|" + striker.y + "|" + aimDirX + "|" + aimDirY + "|" + speed);

        striker.vx = aimDirX * speed;
        striker.vy = aimDirY * speed;

        pocketedThisShot.clear();
        strikerPocketedThisShot = false;
        strikerTouchedCoin = false;
        phase = Phase.SHOOTING;
        isMyTurn = false;
        shotOwnedByMe = true;
        awaitingRemoteState = false;
        hintLabel.setText("");
    }

    /**
     * Slide the striker along the active baseline, to the closest spot clear of any coin.
     */
    private void placeStrikerAt(double x) {
        double y = baselineY(player1Turn);
        striker.x = nearestLegalStrikerX(x, y);
        striker.y = y;
        striker.vx = 0;
        striker.vy = 0;
        striker.updateNodePosition();
    }

    /**
     * Closest x on the baseline where the striker fits without touching a coin.
     */
    private double nearestLegalStrikerX(double desired, double y) {
        double target = Math.clamp(desired, strikerMinX(), strikerMaxX());
        if (!overlapsAnyCoin(target, y, STRIKER_RADIUS)) return target;

        double span = strikerMaxX() - strikerMinX();
        for (double offset = 2; offset <= span; offset += 2) {
            for (int side = -1; side <= 1; side += 2) {
                double candidate = target + side * offset;
                if (candidate < strikerMinX() || candidate > strikerMaxX()) continue;
                if (!overlapsAnyCoin(candidate, y, STRIKER_RADIUS)) return candidate;
            }
        }
        return target; // baseline fully blocked - very unlikely, but keep the striker somewhere
    }

    /**
     * Pull-back aiming: the shot travels opposite the drag. The direction is clamped to the
     * forward half of the board so a player cannot shoot backwards off their own baseline.
     */
    private void computeAim(double mouseX, double mouseY) {
        double dragX = mouseX - striker.x;
        double dragY = mouseY - striker.y;
        double dragLen = Math.hypot(dragX, dragY);
        if (dragLen < 1e-6) {
            aimPower = 0;
            return;
        }

        double shotX = -dragX / dragLen;
        double shotY = -dragY / dragLen;

        double forward = player1Turn ? -1 : 1;
        double maxAngle = Math.toRadians(80);
        if (shotY * forward < Math.cos(maxAngle)) {
            double side = shotX >= 0 ? 1 : -1;
            shotX = side * Math.sin(maxAngle);
            shotY = forward * Math.cos(maxAngle);
        }

        aimDirX = shotX;
        aimDirY = shotY;
        aimPower = Math.min(1, dragLen / MAX_DRAG);
    }

    private void showAimVisuals() {
        double guideLen = 55 + aimPower * 190;
        aimLine.setStartX(striker.x);
        aimLine.setStartY(striker.y);
        aimLine.setEndX(striker.x + aimDirX * guideLen);
        aimLine.setEndY(striker.y + aimDirY * guideLen);
        aimLine.setVisible(true);

        aimTip.setCenterX(striker.x + aimDirX * guideLen);
        aimTip.setCenterY(striker.y + aimDirY * guideLen);
        aimTip.setVisible(true);

        double pull = aimPower * MAX_DRAG;
        dragLine.setStartX(striker.x);
        dragLine.setStartY(striker.y);
        dragLine.setEndX(striker.x - aimDirX * pull);
        dragLine.setEndY(striker.y - aimDirY * pull);
        dragLine.setVisible(true);

        powerFill.setWidth(220 * aimPower);
    }

    private void hideAimVisuals() {
        aimLine.setVisible(false);
        aimTip.setVisible(false);
        dragLine.setVisible(false);
        powerFill.setWidth(0);
    }

    // =====================================================================================
    // Physics
    // =====================================================================================

    private boolean anythingMoving() {
        for (CaromPiece p : pieces) {
            if (Math.hypot(p.vx, p.vy) > REST_SPEED) return true;
        }
        return false;
    }

    /**
     * Advances the board by {@code frames} 60fps-equivalent steps (1.0 on a 60Hz display,
     * 0.5 on a 120Hz one).
     */
    private void stepPhysics(double frames) {
        double fastest = 0;
        for (CaromPiece p : pieces) fastest = Math.max(fastest, Math.hypot(p.vx, p.vy));

        // Sub-step so fast pieces cannot tunnel through coins or cushions.
        int steps = Math.max(1, (int) Math.ceil(fastest * frames / 2.0));
        double dt = frames / steps;

        for (int s = 0; s < steps; s++) {
            for (CaromPiece p : pieces) {
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                bounceOffCushions(p);
            }
            resolveCollisions();
        }

        double slowdown = DECELERATION * frames;
        for (CaromPiece p : pieces) {
            // Sliding friction: shave a fixed amount off the speed, keeping the direction.
            double speed = Math.hypot(p.vx, p.vy);
            if (speed <= slowdown) {
                p.vx = 0;
                p.vy = 0;
            } else {
                double slowed = (speed - slowdown) / speed;
                p.vx *= slowed;
                p.vy *= slowed;
            }
            p.updateNodePosition();
        }

        collectPocketedPieces();
    }

    /**
     * Cushions stay active right into the corners. A piece pressed against the cushion beside
     * a pocket is already inside the capture radius, so it drops instead of rattling out -
     * and nothing can drift off the playing surface.
     */
    private void bounceOffCushions(CaromPiece p) {
        double minX = boardLeft() + p.radius;
        double maxX = boardRight() - p.radius;
        double minY = boardTop() + p.radius;
        double maxY = boardBottom() - p.radius;

        if (p.x < minX) {
            p.x = minX;
            p.vx = Math.abs(p.vx) * CUSHION_BOUNCE;
        } else if (p.x > maxX) {
            p.x = maxX;
            p.vx = -Math.abs(p.vx) * CUSHION_BOUNCE;
        }
        if (p.y < minY) {
            p.y = minY;
            p.vy = Math.abs(p.vy) * CUSHION_BOUNCE;
        } else if (p.y > maxY) {
            p.y = maxY;
            p.vy = -Math.abs(p.vy) * CUSHION_BOUNCE;
        }
    }

    private void resolveCollisions() {
        for (int i = 0; i < pieces.size(); i++) {
            for (int j = i + 1; j < pieces.size(); j++) {
                collide(pieces.get(i), pieces.get(j));
            }
        }
    }

    private void collide(CaromPiece a, CaromPiece b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dist = Math.hypot(dx, dy);
        double minDist = a.radius + b.radius;
        if (dist >= minDist) return;

        if (dist < 1e-6) {
            // Perfectly stacked - nudge apart along a fixed axis to keep the maths finite.
            dx = minDist;
            dy = 0;
            dist = minDist;
        }

        double nx = dx / dist;
        double ny = dy / dist;

        double invA = 1 / a.mass;
        double invB = 1 / b.mass;

        // Positional correction, split by inverse mass.
        double overlap = minDist - dist;
        a.x -= nx * overlap * (invA / (invA + invB));
        a.y -= ny * overlap * (invA / (invA + invB));
        b.x += nx * overlap * (invB / (invA + invB));
        b.y += ny * overlap * (invB / (invA + invB));

        double relN = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny;
        if (relN <= 0) return; // already separating

        if (a.kind == Kind.STRIKER || b.kind == Kind.STRIKER) strikerTouchedCoin = true;

        double impulse = (1 + COIN_BOUNCE) * relN / (invA + invB);
        a.vx -= impulse * invA * nx;
        a.vy -= impulse * invA * ny;
        b.vx += impulse * invB * nx;
        b.vy += impulse * invB * ny;
    }

    private void collectPocketedPieces() {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            CaromPiece p = pieces.get(i);
            if (!isPocketed(p)) continue;

            pieces.remove(i);
            pieceLayer.getChildren().remove(p.node);

            if (p.kind == Kind.STRIKER) {
                strikerPocketedThisShot = true;
            } else {
                pocketedThisShot.add(p.kind);
                if (p.kind == Kind.QUEEN) queenOnBoard = false;
            }
        }
    }

    private boolean isPocketed(CaromPiece p) {
        for (double[] c : pocketCenters()) {
            if (Math.hypot(p.x - c[0], p.y - c[1]) < POCKET_RADIUS - 2) return true;
        }
        // Safety net: anything that somehow escaped the surface counts as pocketed.
        return p.x < boardLeft() - p.radius || p.x > boardRight() + p.radius
                || p.y < boardTop() - p.radius || p.y > boardBottom() + p.radius;
    }

    // =====================================================================================
    // Rules
    // =====================================================================================

    private Player current() {
        return player1Turn ? p1 : p2;
    }

    private Player opponent() {
        return player1Turn ? p2 : p1;
    }

    private void resolveShot() {
        Player shooter = current();
        Player rival = opponent();

        int ownPocketed = 0;
        int rivalPocketed = 0;
        boolean queenPocketed = false;
        for (Kind kind : pocketedThisShot) {
            if (kind == Kind.QUEEN) queenPocketed = true;
            else if (kind == shooter.coin) ownPocketed++;
            else rivalPocketed++;
        }

        shooter.pocketed += ownPocketed;
        rival.pocketed += rivalPocketed;

        boolean shootAgain;
        String message;

        if (strikerPocketedThisShot) {
            // Foul: coins stay down, but the shooter owes one back - only if they have one.
            boolean returned = shooter.pocketed > 0 && returnCoinToBoard(shooter.coin);
            if (returned) shooter.pocketed--;
            restoreQueenIfUnclaimed();
            shootAgain = false;
            message = shooter.name + " pocketed the striker - foul"
                    + (returned ? ", one coin returned" : "");
        } else if (pocketedThisShot.isEmpty() && !strikerTouchedCoin) {
            restoreQueenIfUnclaimed();
            shootAgain = false;
            message = shooter.name + " missed everything - foul";
        } else if (queenPocketed) {
            if (ownPocketed > 0) {
                queenOwner = shooter;
                queenPendingCoverBy = null;
                shootAgain = true;
                message = shooter.name + " pocketed and covered the Queen (+" + QUEEN_BONUS + ")";
            } else if (rivalPocketed > 0) {
                // Potting a rival coin ends the turn, so there is no chance to cover.
                restoreQueenIfUnclaimed();
                shootAgain = false;
                message = shooter.name + " potted a rival coin - Queen returns to the board";
            } else {
                queenPendingCoverBy = shooter;
                shootAgain = true;
                message = shooter.name + " has the Queen - cover it with your own coin";
            }
        } else if (queenPendingCoverBy == shooter) {
            if (ownPocketed > 0) {
                queenOwner = shooter;
                queenPendingCoverBy = null;
                shootAgain = true;
                message = "Queen covered by " + shooter.name + " (+" + QUEEN_BONUS + ")";
            } else {
                restoreQueenIfUnclaimed();
                shootAgain = false;
                message = shooter.name + " failed to cover - Queen returns to the board";
            }
        } else if (ownPocketed > 0 && rivalPocketed == 0) {
            shootAgain = true;
            message = shooter.name + " pockets " + ownPocketed + " - shoot again";
        } else if (rivalPocketed > 0) {
            shootAgain = false;
            message = shooter.name + " potted " + rivalPocketed + " of " + rival.name + "'s coins";
        } else {
            shootAgain = false;
            message = "No coin pocketed - turn passes";
        }

        updateHud();

        Player winner = findWinner();
        if (winner != null) {
            endGame(winner);
            broadcastState();
            return;
        }

        if (!shotOwnedByMe) {
            // Somebody else's shot: the turn is theirs to hand over, not ours to take.
            awaitingRemoteState = true;
        } else if (!shootAgain) {
            player1Turn = !player1Turn;
        }

        phase = Phase.READY;
        placeStrikerForTurn();
        statusLabel.setText(message);

        updateHud();

        if (connected) {
            updateTurnText();
        } else {
            hintLabel.setText(current().name + " to shoot from the "
                    + (player1Turn ? "bottom" : "top") + " baseline");
        }

        broadcastState();
    }

    /**
     * The shooter's client owns the outcome of its own shot and publishes it. Both clients
     * simulate the shot for smooth visuals, but only this message decides what actually happened.
     */
    private void broadcastState() {
        if (connected && shotOwnedByMe) {
            sendMessage(createStateMessage());
        }
    }

    private String createStateMessage() {
        StringBuilder message = new StringBuilder("STATE");
        message.append("|").append(player1Turn);
        message.append("|").append(p1.pocketed);
        message.append("|").append(p2.pocketed);
        message.append("|").append(queenOnBoard);
        message.append("|").append(playerIndex(queenOwner));
        message.append("|").append(playerIndex(queenPendingCoverBy));
        message.append("|").append(phase);

        int nonStrikerCount = 0;
        for (CaromPiece piece : pieces) {
            if (piece.kind != Kind.STRIKER) {
                nonStrikerCount++;
            }
        }

        message.append("|").append(nonStrikerCount);

        for (CaromPiece piece : pieces) {
            if (piece.kind == Kind.STRIKER) {
                continue;
            }

            message.append("|")
                    .append(piece.kind)
                    .append(",")
                    .append(piece.x)
                    .append(",")
                    .append(piece.y)
                    .append(",")
                    .append(piece.vx)
                    .append(",")
                    .append(piece.vy);
        }

        message.append("|STRIKER")
                .append(",")
                .append(striker.x)
                .append(",")
                .append(striker.y)
                .append(",")
                .append(striker.vx)
                .append(",")
                .append(striker.vy);

        return message.toString();
    }

    private int playerIndex(Player player) {
        if (player == p1) {
            return 1;
        }

        if (player == p2) {
            return 2;
        }

        return 0;
    }

    /**
     * An uncovered Queen goes back to the middle as soon as the turn ends.
     */
    private void restoreQueenIfUnclaimed() {
        queenPendingCoverBy = null;
        if (!queenOnBoard && queenOwner == null && spawnPiece(Kind.QUEEN)) queenOnBoard = true;
    }

    private boolean returnCoinToBoard(Kind coin) {
        return spawnPiece(coin);
    }

    private boolean spawnPiece(Kind kind) {
        double[] spot = findFreeSpot(COIN_RADIUS);
        if (spot == null) return false;
        addPiece(new CaromPiece(spot[0], spot[1], COIN_RADIUS, kind));
        return true;
    }

    /**
     * Spiral outward from the centre spot until a gap wide enough for a coin appears.
     */
    private double[] findFreeSpot(double radius) {
        for (double r = 0; r <= BOARD_SIZE / 2 - radius; r += radius) {
            int samples = r < 1 ? 1 : (int) Math.max(8, 2 * Math.PI * r / radius);
            for (int i = 0; i < samples; i++) {
                double angle = 2 * Math.PI * i / samples;
                double x = centerX() + r * Math.cos(angle);
                double y = centerY() + r * Math.sin(angle);
                if (isSpotFree(x, y, radius)) return new double[]{x, y};
            }
        }
        return null;
    }

    private boolean isSpotFree(double x, double y, double radius) {
        if (x - radius < boardLeft() + 4 || x + radius > boardRight() - 4) return false;
        if (y - radius < boardTop() + 4 || y + radius > boardBottom() - 4) return false;
        for (double[] c : pocketCenters()) {
            if (Math.hypot(x - c[0], y - c[1]) < POCKET_RADIUS + radius + 8) return false;
        }
        for (CaromPiece p : pieces) {
            if (Math.hypot(x - p.x, y - p.y) < radius + p.radius + 1.5) return false;
        }
        return true;
    }

    private boolean overlapsAnyCoin(double x, double y, double radius) {
        for (CaromPiece p : pieces) {
            if (p == striker) continue;
            if (Math.hypot(x - p.x, y - p.y) < radius + p.radius + 0.5) return true;
        }
        return false;
    }

    private void placeStrikerForTurn() {
        double y = baselineY(player1Turn);
        if (!pieces.contains(striker)) {
            striker = new CaromPiece(centerX(), y, STRIKER_RADIUS, Kind.STRIKER);
            addPiece(striker);
        }
        striker.vx = 0;
        striker.vy = 0;
        striker.y = y;
        striker.x = nearestLegalStrikerX(centerX(), y);
        striker.updateNodePosition();

        strikerZone.setY(y - STRIKER_RADIUS);
    }

    /**
     * The board is won by the player whose nine coins are all off the table - note that
     * potting your rival's last coin for them hands them the board.
     */
    private Player findWinner() {
        Player finished = null;
        if (p1.pocketed >= COINS_PER_PLAYER) finished = p1;
        else if (p2.pocketed >= COINS_PER_PLAYER) finished = p2;
        if (finished == null) return null;

        // An unclaimed Queen goes to whoever finishes the board.
        if (queenOwner == null) {
            queenOwner = finished;
            queenPendingCoverBy = null;
            queenOnBoard = false;
            for (int i = pieces.size() - 1; i >= 0; i--) {
                if (pieces.get(i).kind == Kind.QUEEN) {
                    pieceLayer.getChildren().remove(pieces.remove(i).node);
                }
            }
        }
        return finished;
    }

    private void endGame(Player winner) {
        phase = Phase.GAME_OVER;
        dragMode = DragMode.NONE;
        hideAimVisuals();
        updateHud();

        overlayTitle.setText(winner.name + " wins!");
        overlaySubtitle.setText("Final score - " + p1.name + " " + p1.score()
                + ", " + p2.name + " " + p2.score());
        overlay.setVisible(true);
        statusLabel.setText("Game over");
        hintLabel.setText("");
    }

    // =====================================================================================
    // HUD
    // =====================================================================================

    private void updateHud() {
        p1.nameLabel.setText(p1.name + " - light coins");
        p2.nameLabel.setText(p2.name + " - dark coins");

        for (Player p : new Player[]{p1, p2}) {
            p.scoreLabel.setText("Score " + p.score());
            p.coinsLabel.setText("Coins left " + Math.max(0, COINS_PER_PLAYER - p.pocketed)
                    + (p.hasQueen() ? "   Queen" : ""));
        }

        boolean p1Active = player1Turn && phase != Phase.GAME_OVER;
        p1.card.setStroke(p1Active ? ACTIVE_GLOW : IDLE_STROKE);
        p2.card.setStroke(!p1Active && phase != Phase.GAME_OVER ? ACTIVE_GLOW : IDLE_STROKE);
        p1.card.setStrokeWidth(p1Active ? 3 : 2);
        p2.card.setStrokeWidth(!p1Active && phase != Phase.GAME_OVER ? 3 : 2);

        strikerZone.setY(baselineY(player1Turn) - STRIKER_RADIUS);
        strikerZone.setVisible(phase != Phase.GAME_OVER);

        if (queenOwner != null) {
            queenLabel.setText("Queen claimed by " + queenOwner.name);
        } else if (queenPendingCoverBy != null) {
            queenLabel.setText(queenPendingCoverBy.name + " must cover the Queen");
        } else {
            queenLabel.setText("Queen on board");
        }
    }

    // =====================================================================================
    // Entities
    // =====================================================================================

    private class Player {
        String name;
        final Kind coin;
        final Color color;
        int pocketed;

        Rectangle card;
        Circle dot;
        Label nameLabel, scoreLabel, coinsLabel;

        Player(String name, Kind coin, Color color) {
            this.name = name;
            this.coin = coin;
            this.color = color;
        }

        boolean hasQueen() {
            return queenOwner == this;
        }

        int score() {
            return pocketed + (hasQueen() ? QUEEN_BONUS : 0);
        }

        void reset() {
            pocketed = 0;
        }
    }

    private static class CaromPiece {
        final Kind kind;
        final double radius;
        final double mass;
        final Circle node;
        double x, y, vx, vy;

        CaromPiece(double x, double y, double radius, Kind kind) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.kind = kind;
            this.mass = kind == Kind.STRIKER ? STRIKER_MASS : COIN_MASS;

            Color base = switch (kind) {
                case STRIKER -> Color.web("#fdfdfd");
                case LIGHT -> LIGHT_COIN;
                case DARK -> DARK_COIN;
                case QUEEN -> QUEEN_COIN;
            };

            node = new Circle(x, y, radius);
            node.setFill(new RadialGradient(0, 0, 0.35, 0.3, 0.9, true, CycleMethod.NO_CYCLE,
                    new Stop(0, base.interpolate(Color.WHITE, 0.45)),
                    new Stop(0.7, base),
                    new Stop(1, base.interpolate(Color.BLACK, 0.35))));
            node.setStroke(base.interpolate(Color.BLACK, 0.55));
            node.setStrokeWidth(kind == Kind.STRIKER ? 2 : 1.2);
            node.setEffect(new DropShadow(5, 1, 2, Color.web("#00000066")));
        }

        void updateNodePosition() {
            node.setCenterX(x);
            node.setCenterY(y);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
