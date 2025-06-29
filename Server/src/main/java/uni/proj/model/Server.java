package uni.proj.model;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import uni.proj.Config;
import uni.proj.model.protocol.MessageType;
import uni.proj.model.protocol.ProtocolHandler;
import uni.proj.model.protocol.data.ChatData;
import uni.proj.model.protocol.data.ForwardData;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server implements Runnable {

    protected ServerSocket server;
    private final ObservableList<ClientHandler> clients = FXCollections.observableArrayList();
    private final ObservableList<RegisterData> emails = FXCollections.observableArrayList();
    private final Logger logger = new Logger();
    private final ProtocolHandler protocolHandler = new ProtocolHandler();
    private boolean isRunning = false;
    private boolean isInitialized = false;
    private Thread thread;

    public Server() {
        initServer();
    }

    private void initServer() {
        if(isInitialized)
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
        isInitialized = true;
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

    public Path getInboxPathForEmail(String email) {
        String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(email.getBytes(StandardCharsets.UTF_8));
        Path inboxFile = Paths.get("data", "inbox", encodedEmail + ".json");

        if (!Files.exists(inboxFile)) {
            System.out.println("Inbox file not found for: " + email);
            return null;
        }
        return inboxFile;
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

    public synchronized void sendEmail(SendMailData data) {
        for (String email : data.receiversEmail()) {
            saveMailToInbox(data, email);

            Platform.runLater(() -> {
                List<ClientHandler> matchingClients = clients.stream()
                        .filter(client -> {
                            String loggedEmail = client.getLoggedEmail();
                            return loggedEmail != null && loggedEmail.equals(email);
                        })
                        .collect(Collectors.toList());

                if (!matchingClients.isEmpty()) {
                    send(new ProtocolMessage<>(MessageType.SEND_MAIL, data), matchingClients);
                }
            });
            logger.log(new Info("Inviata email a "+ email));
        }
    }

    public synchronized void saveMailToInbox(SendMailData data, String email) {
        try {
            // Codifica l'email in Base64 URL-safe
            String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(email.getBytes());
            Path inboxDir = Paths.get("data", "inbox");
            Path inboxFile = inboxDir.resolve(encodedEmail + ".json");

            // Assicurati che la directory esista
            Files.createDirectories(inboxDir);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonArray inboxArray;

            // Se il file esiste, carica l'array esistente
            if (Files.exists(inboxFile)) {
                String content = Files.readString(inboxFile);
                inboxArray = JsonParser.parseString(content).getAsJsonArray();
            } else {
                // Altrimenti, crea un array vuoto
                inboxArray = new JsonArray();
            }

            // Ottieni il JSON come stringa
            Class<?> dataClass = data.getClass();
            String stringedJson = protocolHandler.encode(new ProtocolMessage<>(MessageType.SEND_MAIL, data), dataClass);

            // Aggiungi il nuovo elemento all'array
            JsonElement newElement = JsonParser.parseString(stringedJson);
            inboxArray.add(newElement);

            // Scrivi l'array aggiornato nel file
            try (BufferedWriter writer = Files.newBufferedWriter(inboxFile)) {
                gson.toJson(inboxArray, writer);
            }

        } catch (IOException | JsonParseException e) {
            e.printStackTrace();
            // Puoi aggiungere logging o gestione errori più robusta se serve
        }
    }

    public synchronized void sendInbox(ClientHandler client) {
        try {
            String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(client.getLoggedEmail().getBytes());
            Path inboxDir = Paths.get("data", "inbox");
            Path inboxFile = inboxDir.resolve(encodedEmail + ".json");

            JsonArray inboxArray;

            // Se il file esiste, carica l'array esistente
            if (Files.exists(inboxFile)) {
                String content = Files.readString(inboxFile);
                inboxArray = JsonParser.parseString(content).getAsJsonArray();
            } else {
                inboxArray = new JsonArray();
            }

            for (int i = 0; i < inboxArray.size(); i++) {
                JsonObject mailData = inboxArray.get(i).getAsJsonObject().getAsJsonObject("data");
                JsonArray receiversArray = mailData.getAsJsonArray("receiversEmail");
                String[] receivers = new String[receiversArray.size()];
                for (int j = 0; j < receiversArray.size(); j++) {
                    receivers[j] = receiversArray.get(j).getAsString();
                }
                send(new ProtocolMessage<>(MessageType.SEND_MAIL, new SendMailData(mailData.get("senderEmail").getAsString(), mailData.get("title").getAsString(), mailData.get("body").getAsString(), receivers)), List.of(client));
            }


        } catch (IOException | JsonParseException e) {
            e.printStackTrace();
            // Puoi aggiungere logging o gestione errori più robusta se serve
        }
    }

    public synchronized void forwardMail(ForwardData data, ClientHandler clientHandler) {
        SendMailData mailToForward = data.mail();
        String[] forwardTo = data.forwardTo();
        String requestFrom = clientHandler.getLoggedEmail();

        // Step 1: otteniamo il path del file inbox
        String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(requestFrom.getBytes(StandardCharsets.UTF_8));
        Path inboxFile = Paths.get("data", "inbox", encodedEmail + ".json");

        if (!Files.exists(inboxFile)) {
            System.out.println("Inbox file not found for: " + requestFrom);
            return;
        }

        try {
            // Step 2: leggiamo il contenuto del file
            String jsonContent = Files.readString(inboxFile);
            JsonArray inboxArray = JsonParser.parseString(jsonContent).getAsJsonArray();

            // Step 3: cerchiamo se c'è una mail identica a quella da inoltrare
            Gson gson = new Gson();
            boolean found = false;

            for (JsonElement element : inboxArray) {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("type") || !obj.get("type").getAsString().equals("SEND_MAIL")) continue;

                JsonObject dataObj = obj.getAsJsonObject("data");
                SendMailData mail = gson.fromJson(dataObj, SendMailData.class);

                if (mail.equals(mailToForward)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println("La mail da inoltrare non è stata trovata nella inbox.");
                return;
            }

            // Se siamo qui, la mail esiste: possiamo continuare col forward
            System.out.println("Mail trovata. Procedo con il forward...");

            AtomicBoolean allDestinationsValid = new AtomicBoolean(true);
            CountDownLatch latch = new CountDownLatch(1);

            Platform.runLater(() -> {
                try {
                    for (String dest : forwardTo) {
                        boolean foundDes = emails.stream()
                                .anyMatch(reg -> reg.email().equalsIgnoreCase(dest));
                        if (!foundDes) {
                            System.out.println("Destinatario non valido: " + dest);
                            allDestinationsValid.set(false);
                            break;
                        }
                    }
                } finally {
                    latch.countDown(); // Sblocca il thread chiamante
                }
            });

            try {
                latch.await(); // Aspetta che la verifica sia completata
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            if (!allDestinationsValid.get()) {
                System.out.println("Forward interrotto: uno o più destinatari non validi.");
                return;
            }

            Set<String> oldReceivers = new HashSet<>(List.of(mailToForward.receiversEmail()));

            Set<String> newReceivers = Arrays.stream(forwardTo)
                    .filter(dest -> !oldReceivers.contains(dest))
                    .collect(Collectors.toSet());

            Set<String> updatedReceivers = new LinkedHashSet<>(oldReceivers);
            updatedReceivers.addAll(newReceivers);

            getForwardRecipientsAsync(forwardTo, recipients ->{
                send(new ProtocolMessage<>(MessageType.FORWARD, new ForwardData(mailToForward, Stream.concat(Arrays.stream(mailToForward.receiversEmail()), Arrays.stream(forwardTo)).toArray(size -> Arrays.copyOf(mailToForward.receiversEmail(), size)))), recipients);
            });

            for (String oldDest : oldReceivers) {
                Path oldInbox = getInboxPathForEmail(oldDest);
                if (!Files.exists(oldInbox)) continue;

                try {
                    JsonArray inbox = JsonParser.parseString(Files.readString(oldInbox)).getAsJsonArray();
                    boolean modified = false;

                    for (JsonElement el : inbox) {
                        JsonObject obj = el.getAsJsonObject();
                        if (!obj.has("type") || !obj.get("type").getAsString().equals("SEND_MAIL")) continue;

                        JsonObject dataObj = obj.getAsJsonObject("data");
                        SendMailData parsed = gson.fromJson(dataObj, SendMailData.class);

                        if (parsed.equals(mailToForward)) {
                            JsonArray newArray = new JsonArray();
                            for (String r : updatedReceivers) newArray.add(r);
                            dataObj.add("receiversEmail", newArray);
                            modified = true;
                            break;
                        }
                    }

                    if (modified) {
                        Files.writeString(oldInbox, gson.toJson(inbox), StandardOpenOption.TRUNCATE_EXISTING);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            JsonObject mailJson = new JsonObject();
            mailJson.addProperty("type", "SEND_MAIL");

            JsonElement dataElement = gson.toJsonTree(mailToForward);
            mailJson.add("data", dataElement);

            JsonArray receiversArray = dataElement.getAsJsonObject().getAsJsonArray("receiversEmail");

            for (String email : forwardTo) {
                receiversArray.add(email);
            }

            for (String newDest : newReceivers) {
                Path newInbox = getInboxPathForEmail(newDest);

                JsonArray inbox;
                try {
                    if (Files.exists(newInbox)) {
                        inbox = JsonParser.parseString(Files.readString(newInbox)).getAsJsonArray();
                    } else {
                        Files.createDirectories(newInbox.getParent());
                        inbox = new JsonArray();
                    }
                    inbox.add(mailJson.deepCopy());

                    Files.writeString(newInbox, gson.toJson(inbox), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException | JsonParseException e) {
            e.printStackTrace();
        }
    }

    public void getForwardRecipientsAsync(String[] forwardTo, java.util.function.Consumer<List<ClientHandler>> callback) {
        // Step 1: copia veloce in JavaFX thread
        Platform.runLater(() -> {
            List<ClientHandler> snapshot = new ArrayList<>(clients); // copia sicura

            // Step 2: esegui il filtraggio in background
            Task<List<ClientHandler>> task = new Task<>() {
                @Override
                protected List<ClientHandler> call() {
                    List<ClientHandler> result = new ArrayList<>();
                    for (ClientHandler ch : snapshot) {
                        String email = ch.getLoggedEmail();
                        if (email != null) {
                            for (String fwd : forwardTo) {
                                if (email.equalsIgnoreCase(fwd)) {
                                    result.add(ch);
                                    break;
                                }
                            }
                        }
                    }
                    return result;
                }

                @Override
                protected void succeeded() {
                    // callback col risultato
                    callback.accept(getValue());
                }

                @Override
                protected void failed() {
                    callback.accept(new ArrayList<>()); // fallback vuoto
                }
            };

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        });
    }
}
