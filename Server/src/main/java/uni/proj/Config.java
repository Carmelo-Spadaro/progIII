package uni.proj;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {

    public static final int SERVER_PORT;

    static {
        Dotenv dotenv = Dotenv.configure()
                .directory("src/main/resources") // path relativo dal root del progetto
                .filename(".env")                // nome file (opzionale, default: .env)
                .load();

        SERVER_PORT = Integer.parseInt(dotenv.get("SERVER_PORT", "25565"));
    }


    // Costruttore privato per evitare istanziazione
    private Config() {}
}