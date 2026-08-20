module com.example.javagame {
    requires javafx.controls;
    requires javafx.graphics;
    opens com.example.javagame.caromnetwork to javafx.graphics;


//    exports com.example.javagame.chess;
    exports com.example.javagame.chessnetwork;
//    exports com.example.javagame.tetris;
    exports com.example.javagame.carom;
    exports com.example.javagame.caromnetwork;

}
