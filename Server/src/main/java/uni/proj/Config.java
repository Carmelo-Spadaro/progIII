package uni.proj;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {

    public static final int SERVER_PORT;
    public static final boolean NO_GUI;

    static {
        Dotenv dotenv = Dotenv.configure()
                .filename(".env")
                .load();

        SERVER_PORT = Integer.parseInt(dotenv.get("SERVER_PORT", "25565"));
        NO_GUI = Boolean.parseBoolean(dotenv.get("NO_GUI", "false"));
    }


    // Costruttore privato per evitare istanziazione
    private Config() {}
}