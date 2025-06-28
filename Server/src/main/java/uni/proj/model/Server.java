package uni.proj.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uni.proj.Config;
import uni.proj.model.protocol.MessageType;
import uni.proj.model.protocol.ProtocolHandler;
import uni.proj.model.protocol.data.ChatData;
import uni.proj.model.protocol.data.RegisterData;
import uni.proj.model.protocol.data.SendMailData;
import uni.proj.model.status.Error;
import uni.proj.model.status.Info;
import uni.proj.model.status.Warning;
import uni.proj.model.status.Command;
import uni.proj.model.protocol.ProtocolMessage;

import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Server implements Runnable {

    protected ServerSocket server;
    private final ObservableList<ClientHandler> clients = FXCollections.observableArrayList();
    private final ObservableList<RegisterData> emails = FXCollections.observableArrayList();
    private final Logger logger = new Logger();
    private final ProtocolHandler protocolHandler = new ProtocolHandler();
    private boolean isRunning = false;
    private boolean isInitaliazed = false;
    private Thread thread;

    public Server() {
        initServer();
    }

    private void initServer() {
        if(isInitaliazed)
            return;
        logger.log(new Info("inizializzazione server"));
        try {
            if (server == null || server.isClosed()) {
                server = new ServerSocket(Config.SERVER_PORT);
            }
        } catch (IOException e) {
            logger.log(new Error("errore durante l'inizializzazione: "+ e.getMessage()));
            throw new RuntimeException(e);
        }
        loadRegisters();
        logger.log(new Info("server inizializzato"));
        isInitaliazed = true;
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

    public synchronized void broadcast(ProtocolMessage<?> message, ClientHandler except) {
        Class<?> dataClass = protocolHandler.getDataClassForType(message.type());

        String json = protocolHandler.encode(message, dataClass);

        for (ClientHandler client : clients) {
            if (client.equals(except)) continue;
            if (client.isRunning())
                client.send(json);
        }
    }

    public synchronized void send(ProtocolMessage<?> message, List<ClientHandler> clients) {
        Class<?> dataClass = protocolHandler.getDataClassForType(message.type());

        for (ClientHandler client : clients) {
            if(client.isRunning()) {
                client.send(protocolHandler.encode(message, dataClass));
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
            ProtocolMessage<ChatData> message = new ProtocolMessage<>(MessageType.CHAT, new ChatData(command));
            broadcast(message, null);
            logger.log(new Info("broadcast eseguito con successo: " + message.data().message()));
        }
        return true;
    }

    public synchronized Logger getLogger() {
        return logger;
    }

    public synchronized ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public synchronized ObservableList<ClientHandler> getClients() {
        return clients;
    }

    public synchronized ObservableList<RegisterData> getEmails() {
        return emails;
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    private void loadRegisters() {
        Gson gson = new Gson();
        File file = new File("data/emails.json");

        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<RegisterData>>(){}.getType();
                List<RegisterData> emailList = gson.fromJson(reader, listType);

                if (emailList != null) {
                    javafx.application.Platform.runLater(() -> {
                        emails.clear();
                        emails.addAll(emailList);
                    });
                    logger.log(new Info("Caricate " + emailList.size() + " email da file"));
                } else {
                    logger.log(new Warning("Il file delle email esiste ma è vuoto"));
                }
            } catch (IOException e) {
                logger.log(new Error("Errore durante il caricamento delle email: " + e.getMessage()));
            }
        } else {
            logger.log(new Warning("File emails.json non trovato, nessuna email caricata"));
        }
    }

    public synchronized void saveRegister(RegisterData data) {
        Gson gson = new Gson();
        File file = new File("data/emails.json");

        try {
            List<RegisterData> emailList;

            if (file.exists()) {
                // Leggi lista esistente
                try (Reader reader = new FileReader(file)) {
                    Type listType = new TypeToken<List<RegisterData>>(){}.getType();
                    emailList = gson.fromJson(reader, listType);
                }
                if (emailList == null) {
                    emailList = new ArrayList<>();
                }
            } else {
                emailList = new ArrayList<>();
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            // Controlla se l'email è già presente
            boolean exists = emailList.stream()
                    .anyMatch(r -> r.email().equalsIgnoreCase(data.email()));

            if (!exists) {
                emailList.add(data);
                try (Writer writer = new FileWriter(file)) {
                    gson.toJson(emailList, writer);
                }

                String encodedEmail = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(data.email().getBytes(StandardCharsets.UTF_8));

                File inboxFile = new File("data/inbox/" + encodedEmail + ".json");
                inboxFile.getParentFile().mkdirs();

                if (!inboxFile.exists()) {
                    try (Writer inboxWriter = new FileWriter(inboxFile)) {
                        inboxWriter.write("[]"); // inbox vuota
                    }
                }
            } else {
                // Email già presente, gestisci se vuoi
            }

        } catch (IOException e) {
            e.printStackTrace();
            // Gestisci eccezioni/log
        }
    }

    public void sendEmail(SendMailData data) {
        for (String email : data.receiversEmail()) {
            saveMailToInbox(data, email);

            Platform.runLater(() ->
                    clients.stream()
                            .map(ClientHandler::getLoggedEmail)
                            .filter(loggedEmail -> loggedEmail != null && loggedEmail.equals(email))
                            .forEach(_ -> sendLiveEmail(data, email))
            );
        }
    }

    public void saveMailToInbox(SendMailData data, String email) {

    }

    public void sendLiveEmail(SendMailData data, String email) {

    }
}
