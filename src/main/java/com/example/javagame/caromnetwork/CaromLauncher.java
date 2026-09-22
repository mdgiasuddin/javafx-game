package com.example.javagame.caromnetwork;

import java.util.Arrays;

/**
 * Entry point for the standalone carom jar.
 * <p>
 * {@code java -jar carom.jar server} starts the relay server; anything else starts a client,
 * with the first argument taken as the server host.
 * <p>
 * The launcher deliberately does not extend {@link javafx.application.Application}. When the
 * JavaFX classes live on the classpath rather than the module path - which is what a shaded jar
 * gives you - the JVM refuses to start a main class that does, with "JavaFX runtime components
 * are missing". Going through a plain class sidesteps that check.
 */
public final class CaromLauncher {
    private CaromLauncher() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            CaromServer.main(Arrays.copyOfRange(args, 1, args.length));
        } else {
            CaromNetworkGame.main(args);
        }
    }
}
