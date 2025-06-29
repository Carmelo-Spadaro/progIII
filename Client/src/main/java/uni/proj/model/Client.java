package uni.proj.model;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uni.proj.Config;
import uni.proj.model.protocol.MessageType;
import uni.proj.model.protocol.ProtocolHandler;
import uni.proj.model.protocol.ProtocolMessage;
import uni.proj.model.protocol.data.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Client implements Runnable {

    public enum State {
        STARTED,
        LOGGED,
        STOPPED,
        INITIALIZED,
        OFFLINE
    }

    private Socket socket;
    private ClientListener listener;
    private BufferedReader in;
    private PrintWriter out;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private final ProtocolHandler protocolHandler;
    private Thread readerThread;
    private String loggedMail = null;
    private final ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(State.OFFLINE);

    private final ObservableList<SendMailData> mails = FXCollections.observableArrayList();

    private volatile boolean running = true;

    public Client() {
        protocolHandler = new ProtocolHandler();
        setState(State.INITIALIZED);
    }

    @Override
    public void run() {
        while (running) {
            System.out.println(getState());
            if (!(getState() == State.INITIALIZED || getState() == State.STOPPED)) {
                break;
            }
            if (getState() == State.STOPPED) {
                setState(State.INITIALIZED);
            }

            try {
                System.out.println("Tentativo di connessione al server...");
                connect();
                setState(State.STARTED);
                System.out.println("Connesso al server.");

                // Scrittura
                while (running && !socket.isClosed()) {
                    try {
                        String msg = outgoingMessages.take();
                        if(msg.isEmpty())
                            System.out.println("messaggio vuoto");
                        out.println(msg);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

            } catch (IOException e) {
                // ignora
            } finally {
                closeResources();
                setState(State.STOPPED);

                if (running) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        setState(State.OFFLINE);
    }

    private void connect() throws IOException {
        socket = new Socket(Config.SERVER_ADDRESS, Config.SERVER_PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Thread di lettura
        readerThread = new Thread(this::readMessages);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readMessages() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                handleMessage(protocolHandler.decode(line));
            }
        } catch (IOException e) {
            System.err.println("Errore durante la lettura: " + e.getMessage());
        } finally {
            closeResources();
        }
    }

    private void handleMessage(ProtocolMessage<?> message) {
        switch (message.type()) {
            case CHAT -> {
                ChatData data = (ChatData) message.data();
                System.out.println("messaggio dal server: "+data.message());
            }
            case RESPONSE -> {
                listener.onResponse((ResponseData) message.data());
                ResponseData data = (ResponseData) message.data();
                switch (data.responseTo()) {
                    case LOGIN -> setState(State.LOGGED);
                    case LOGOUT -> {
                        setState(State.STARTED);
                        loggedMail = null;
                    }
                    case SEND_MAIL -> {
                        System.out.println(data.message());
                    }
                    case CHAT, ERROR, RESPONSE, REGISTER -> {
                        // ignora
                    }
                    default -> {
                        System.out.println("Risposta non riconosciuta");
                    }
                }
            }
            case ERROR -> {
                listener.onError((ErrorData) message.data());
                ErrorData data = (ErrorData) message.data();
                switch (data.responseTo()) {
                    case LOGOUT, CHAT, ERROR, RESPONSE, REGISTER, SEND_MAIL -> {
                        System.out.println(data.message());
                    }
                    case LOGIN -> {
                        System.out.println(data.message());
                        loggedMail = null;
                    }
                    default -> {
                        System.out.println("Risposta non riconosciuta");
                    }
                }
            }
            case SEND_MAIL -> {
                SendMailData data = (SendMailData) message.data();
                System.out.println("Titolo: "+data.title());
                System.out.println("mittente: "+ data.senderEmail());
                System.out.println(data.body());
                System.out.println("destinatari: " + Arrays.toString(data.receiversEmail()));
                System.out.flush();

                Platform.runLater(() -> mails.add(data));
            }
            case FORWARD -> {
                ForwardData data = (ForwardData) message.data();
                System.out.println("Titolo: "+data.mail().title());
                System.out.println("mittente: "+ data.mail().senderEmail());
                System.out.println(data.mail().body());
                System.out.println("destinatari: " + Arrays.toString(Stream.concat(Arrays.stream(data.mail().receiversEmail()), Arrays.stream(data.forwardTo())).toArray(size -> Arrays.copyOf(data.mail().receiversEmail(), size))));
                System.out.flush();

            }
            case LOGIN, REGISTER, LOGOUT -> {
                // ignora
            }
            default -> {
                System.out.println("tipo di richiesta non riconosciuta");
            }
        }
    }

    public synchronized void send(ProtocolMessage<?> message) {
        Class<?> dataClass = protocolHandler.getDataClassForType(message.type());

       outgoingMessages.offer(protocolHandler.encode(message, dataClass));
    }

    public boolean execute(String query) {
        query = query.strip();
        if(query.startsWith("/")) {
            List<String> commandQuery = parseCommandArguments(query);
            String command = commandQuery.getFirst().toLowerCase();
            switch (command) {
                case "/login" -> {
                    if(commandQuery.size() == 2 && isValidEmail(commandQuery.get(1))) {
                        send(new ProtocolMessage<>(MessageType.LOGIN, new LoginData(commandQuery.get(1))));
                        loggedMail = commandQuery.get(1);
                        return true;
                    } else {
                        System.out.println("comando non valido");
                        return false;
                    }
                }
                case "/logout" -> {
                    send(new ProtocolMessage<>(MessageType.LOGOUT, new LogoutData("logout")));
                    return true;
                }
                case "/register" -> {
                    if(commandQuery.size() == 2 && isValidEmail(commandQuery.get(1))) {
                        send(new ProtocolMessage<>(MessageType.REGISTER, new RegisterData(commandQuery.get(1))));
                        return true;
                    } else {
                        System.out.println("comando non valido");
                        return false;
                    }
                }
                case "/sendmail" -> {
                    if(loggedMail != null) {
                        if(commandQuery.size() >= 4) {
                            for(String email : commandQuery.subList(3, commandQuery.size()-1)) {
                                if(!isValidEmail(email)) {
                                    System.out.println("destinatario non valido " + email);
                                    return false;
                                }
                            }
                            String[] recipients = commandQuery.subList(3, commandQuery.size()).toArray(new String[0]);
                            send(new ProtocolMessage<>(MessageType.SEND_MAIL, new SendMailData(loggedMail, commandQuery.get(1), commandQuery.get(2), recipients)));
                            return true;
                        } else {
                            System.out.println("comando non valido");
                            return false;
                        }
                    } else {
                        System.out.println("comando non valido, devi essere loggato");
                        return false;
                    }
                }
                case "/getinbox" -> {
                    if(loggedMail == null) {
                        System.out.println("comando non valido, devi essere loggato");
                       return false;
                    }
                    send(new ProtocolMessage<>(MessageType.GET_INBOX, new GetInboxData(loggedMail)));
                    return true;
                }
                case "/forward" -> {
                    if(loggedMail == null) {
                        System.out.println("comando non valido, devi essere loggato");
                        return false;
                    }
                    if(commandQuery.size() >= 7) {
                        int forwardIndex = commandQuery.indexOf("forwardto");
                        if (forwardIndex == -1 || forwardIndex < 5 || forwardIndex == commandQuery.size() - 1) {
                            System.out.println("comando non valido, uso scorretto di 'forwardto'");
                            return false;
                        }

                        String sender = commandQuery.get(1);
                        String subject = commandQuery.get(2);
                        String body = commandQuery.get(3);

                        List<String> originalRecipients = commandQuery.subList(4, forwardIndex);
                        List<String> newRecipients = commandQuery.subList(forwardIndex + 1, commandQuery.size());

                        for (String email : originalRecipients) {
                            if (!isValidEmail(email)) {
                                System.out.println("destinatario originale non valido: " + email);
                                return false;
                            }
                        }

                        for (String email : newRecipients) {
                            if (!isValidEmail(email)) {
                                System.out.println("nuovo destinatario non valido: " + email);
                                return false;
                            }
                        }

                        SendMailData mail = new SendMailData(sender, subject, body, originalRecipients.toArray(new String[0]));

                        boolean exists = mails.contains(mail);

                        if (exists) {
                            send(new ProtocolMessage<>(MessageType.FORWARD, new ForwardData(mail, newRecipients.toArray(new String[0]))));
                            return true;
                        } else {
                            System.out.println("La mail da inoltrare non esiste nella tua inbox.");
                            return false;
                        }
                    } else {
                        System.out.println("comando non valido");
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        } else {
            send(new ProtocolMessage<>(MessageType.CHAT, new ChatData(query)));
        }
        return true;
    }

    public void stop() {
        running = false;
        if (readerThread != null) readerThread.interrupt(); // interrompe il thread lettore se necessario
        Thread.currentThread().interrupt(); // interrompe anche questo thread se è in attesa
        outgoingMessages.offer(""); // sblocca il take() nel caso non arrivi mai un messaggio
        closeResources();
        setState(State.OFFLINE);
        System.out.println("stop eseguito");
    }

    private void closeResources() {
        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            // Ignora
        }
    }

    public void setListener(ClientListener listener) {
        this.listener = listener;
    }

    private void setState(State newState) {
        Platform.runLater(() -> stateProperty.set(newState));
    }

    private State getState() {
        AtomicReference<State> state = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            state.set(stateProperty.get());
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            stop();
            System.err.println("Thread interrotto mentre attendeva il valore dello stato.");
            return null;
        }

        return state.get();
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public ObjectProperty<State> getStateProperty() {
        return stateProperty;
    }


    public ObservableList<SendMailData> getMails() {
        return mails;
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private List<String> parseCommandArguments(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(input);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                tokens.add(matcher.group(1)); // Contenuto tra virgolette
            } else {
                tokens.add(matcher.group(2)); // Parola singola
            }
        }
        return tokens;
    }
}
