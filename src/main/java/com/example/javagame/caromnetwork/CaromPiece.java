package com.example.javagame.caromnetwork;

import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;

import static com.example.javagame.caromnetwork.CaromNetworkGame.COIN_MASS;
import static com.example.javagame.caromnetwork.CaromNetworkGame.DARK_COIN;
import static com.example.javagame.caromnetwork.CaromNetworkGame.LIGHT_COIN;
import static com.example.javagame.caromnetwork.CaromNetworkGame.QUEEN_COIN;
import static com.example.javagame.caromnetwork.CaromNetworkGame.STRIKER_MASS;
import static com.example.javagame.caromnetwork.Kind.STRIKER;
import static javafx.scene.paint.Color.BLACK;
import static javafx.scene.paint.Color.WHITE;

public class CaromPiece {
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
        this.mass = kind == STRIKER ? STRIKER_MASS : COIN_MASS;

        Color base = switch (kind) {
            case STRIKER -> Color.web("#fdfdfd");
            case LIGHT -> LIGHT_COIN;
            case DARK -> DARK_COIN;
            case QUEEN -> QUEEN_COIN;
        };

        node = new Circle(x, y, radius);
        node.setFill(new RadialGradient(0, 0, 0.35, 0.3, 0.9, true, CycleMethod.NO_CYCLE,
                new Stop(0, base.interpolate(WHITE, 0.45)),
                new Stop(0.7, base),
                new Stop(1, base.interpolate(BLACK, 0.35))));
        node.setStroke(base.interpolate(BLACK, 0.55));
        node.setStrokeWidth(kind == STRIKER ? 2 : 1.2);
        node.setEffect(new DropShadow(5, 1, 2, Color.web("#00000066")));
    }

    void updateNodePosition() {
        node.setCenterX(x);
        node.setCenterY(y);
    }
}
