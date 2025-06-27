package uni.proj.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uni.proj.Config;
import uni.proj.model.protocol.MessageType;
import uni.proj.model.protocol.ProtocolHandler;
import uni.proj.model.status.Error;
import uni.proj.model.status.Info;
import uni.proj.model.status.Warning;
import uni.proj.model.status.Command;
import uni.proj.model.protocol.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Server implements Runnable {

    protected ServerSocket server;
    private final ObservableList<ClientHandler> clients = FXCollections.observableArrayList();
    private final Logger logger = new Logger();
    private ProtocolHandler protocolHandler = new ProtocolHandler();
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
        logger.log(new Info("server in ascolto sulla porta: " + server.getLocalPort()));
        isRunning = true;
        while (isRunning) {
            try {
                Socket socket = server.accept();
                logger.log(new Info("connessione accettata da " + socket.getRemoteSocketAddress()));

                // Aggiorna la lista dei client nella UI
                ClientHandler handler = new ClientHandler(this, socket);

                javafx.application.Platform.runLater(() -> clients.add(handler));

                new Thread(handler).start();
            } catch (IOException e) {
                if (server.isClosed()) {
                    logger.log(new Info("server chiuso"));
                } else {
                    logger.log(new Error("errore durante la connessione " + e.getMessage()));
                }
            }
        }
        isRunning = false;
    }

    public void stopServer() {
        isRunning = false;
        if(server.isClosed()) {
            logger.log(new Warning("Il server e' gia' chiuso"));
            return;
        }
        try {
            // Chiudi tutti i client
            for (ClientHandler client : clients) {
                client.shutdown();
            }
            clients.clear();

            // Chiudi il ServerSocket
            server.close();

            logger.log(new Info("Server chiuso correttamente"));
        } catch (IOException e) {
            if (!server.isClosed()) {
                logger.log(new Error("Errore durante la chiusura del server: " + e.getMessage()));
            }
        }
    }

    public void broadcast(Message message, ClientHandler except) {
        for (ClientHandler client : clients) {
            if(client.equals(except))
                continue;
            if (client.isRunning())
                client.send(protocolHandler.encode(message));
        }
    }

    public void send(Message message, List<ClientHandler> clients) {
        for (ClientHandler client : clients) {
            if(client.isRunning()) {
                client.send(protocolHandler.encode(message));
            }
        }
    }

    public boolean execute(String command) {
        if(command.startsWith("/")) {
            logger.log(new Command("Comando ricevuto: "+command));
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
            Message message = new Message(MessageType.CHAT, command);
            broadcast(message, null);
            logger.log(new Info("broadcast eseguito con successo: " + protocolHandler.encode(message)));
        }
        return true;
    }

    public Logger getLogger() {
        return logger;
    }

    public ObservableList<ClientHandler> getClients() {
        return clients;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
