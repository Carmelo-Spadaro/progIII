package uni.proj;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {

    public static final int SERVER_PORT;
    public static final String SERVER_ADDRESS;

    static {
        Dotenv dotenv = Dotenv.configure()
                .filename(".env")
                .load();

        SERVER_PORT = Integer.parseInt(dotenv.get("SERVER_PORT", "25565"));
        SERVER_ADDRESS = dotenv.get("SERVER_ADDRESS", "localhost");
    }


    // Costruttore privato per evitare istanziazione
    private Config() {}
}