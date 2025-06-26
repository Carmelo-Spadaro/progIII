package uni.proj.model;

import uni.proj.Config;
import uni.proj.events.Error;
import uni.proj.events.Event;
import uni.proj.events.Info;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Server extends Thread{

    protected final boolean noGui;
    protected ServerSocket server;
    private final Logger logger = new Logger();

    public Server(String[] args) {
        logger.log(new Info("inizializzazione server"));
        noGui = List.of(args).contains("nogui");
        try {
            server = new ServerSocket(Config.SERVER_PORT);
        } catch (IOException e) {
            logger.log(new Error("errore durante l'inizializzazione: "+ e.getMessage()));
            throw new RuntimeException(e);
        }
        logger.log(new Info("server inizializzato"));
    }

    @Override
    public void run() {
        logger.log(new Info("server in ascolto sulla porta: " + server.getLocalPort() ));
        while (!server.isClosed()) {
            logger.log(new Info("attendo una connessione"));
            try {
                Socket socket = server.accept();
                logger.log(new Info("connessione accettata da " + socket.getRemoteSocketAddress()));
            } catch (IOException e) {
                if(server.isClosed()) {
                    logger.log(new Info("server chiuso"));
                } else {
                    logger.log(new Error("errore durante la connessione " + e.getMessage()));
                }
            }
        }
    }
}
