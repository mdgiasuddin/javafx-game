package com.example.javagame.caromnetwork;

import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import static com.example.javagame.caromnetwork.CaromNetworkGame.QUEEN_BONUS;

/**
 * One side of the match: a name, a coin colour, and everything the HUD needs to render for them.
 */
public class Player {

    String name;
    final Kind coin;
    final Color color;
    int pocketed;
    private boolean hasQueen;

    Rectangle card;
    Circle dot;
    Label nameLabel, scoreLabel, coinsLabel;

    public Player(String name, Kind coin, Color color) {
        this.name = name;
        this.coin = coin;
        this.color = color;
    }

    public boolean hasQueen() {
        return hasQueen;
    }

    /**
     * Package-visible: only CaromNetworkGame decides queen ownership.
     */
    void setHasQueen(boolean hasQueen) {
        this.hasQueen = hasQueen;
    }

    public int score() {
        return pocketed + (hasQueen ? QUEEN_BONUS : 0);
    }

    public void reset() {
        pocketed = 0;
        hasQueen = false;
    }
}
