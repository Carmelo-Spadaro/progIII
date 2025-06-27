package uni.proj.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uni.proj.Config;
import uni.proj.status.Command;
import uni.proj.status.Error;
import uni.proj.status.Info;
import uni.proj.status.Warning;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server implements Runnable {

    protected ServerSocket server;
    private final ObservableList<Socket> clients = FXCollections.observableArrayList();
    private final Logger logger = new Logger();
    private boolean isRunning = false;
    private Thread thread;

    public Server() {
        logger.log(new Info("inizializzazione server"));
        initServer();
        logger.log(new Info("server inizializzato"));
    }

    private void initServer() {
        try {
            if (server == null || server.isClosed()) {
                server = new ServerSocket(Config.SERVER_PORT);
            }
        } catch (IOException e) {
            logger.log(new Error("errore durante l'inizializzazione: "+ e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        listen();
    }

    public void startServer() {
        if(thread != null && thread.isAlive()) {
            logger.log(new Warning("Il server e' gia' in ascolto"));
            return;
        }
        initServer();
        thread = new Thread(this);
        thread.start();
    }

    public void listen() {
        logger.log(new Info("server in ascolto sulla porta: " + server.getLocalPort() ));
        isRunning=true;
        while (!server.isClosed()) {
            logger.log(new Info("attendo una connessione"));
            try {
                Socket socket = server.accept();
                clients.add(socket);
                logger.log(new Info("connessione accettata da " + socket.getRemoteSocketAddress()));
            } catch (IOException e) {
                if(server.isClosed()) {
                    logger.log(new Info("server chiuso"));
                } else {
                    logger.log(new Error("errore durante la connessione " + e.getMessage()));
                }
            }
        }
        isRunning = false;
    }

    public void stopServer() {
        if(server.isClosed()) {
            logger.log(new Warning("Il server e' gia' chiuso"));
            return;
        }
        try {
            server.close();
            clients.clear();
            logger.log(new Info("Server chiuso correttamente"));
        } catch (IOException e) {
            if(!server.isClosed()) {
                logger.log(new Error("Errore durante la chiusura del server: " + e.getMessage()));
            }
        }
    }

    public void broadcast(String message) {

    }

    public boolean execute(String command) {
        logger.log(new Command("Comando ricevuto: "+command));
        if(command.startsWith("/")) {
            command = command.toLowerCase();
            switch (command) {
                case "/clear" -> {
                    logger.clear();
                    logger.log(new Info("Clear eseguito con successo"));
                }
                case "/stop" -> stopServer();
                case "/start" -> startServer();
                default -> {
                    logger.log(new Error("Comando non riconosciuto"));
                    return false;
                }
            }
        } else {
            broadcast(command);
            logger.log(new Info("broadcast eseguito con successo: " + command));
        }
        return true;
    }

    public Logger getLogger() {
        return logger;
    }

    public ObservableList<Socket> getClients() {
        return clients;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
