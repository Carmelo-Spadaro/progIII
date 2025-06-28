package uni.proj.model;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import uni.proj.Config;
import uni.proj.model.protocol.MessageType;
import uni.proj.model.protocol.ProtocolHandler;
import uni.proj.model.protocol.ProtocolMessage;
import uni.proj.model.protocol.data.*;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class Client implements Runnable {

    public enum State {
        STARTED,
        LOGGED,
        STOPPED,
        INITIALIZED,
        OFFLINE
    }

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private final ProtocolHandler protocolHandler;
    private Thread readerThread;
    private final ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(State.OFFLINE);

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
                ResponseData data = (ResponseData) message.data();
                switch (data.responseTo()) {
                    case LOGIN -> setState(State.LOGGED);
                    case LOGOUT -> setState(State.STARTED);
                    case CHAT, ERROR, RESPONSE, REGISTER, SEND_MAIL -> {
                        // ignora
                    }
                    default -> {
                        System.out.println("Risposta non riconosciuta");
                    }
                }
            }
            case ERROR -> {
                ErrorData data = (ErrorData) message.data();
                switch (data.responseTo()) {
                    case LOGIN, LOGOUT, CHAT, ERROR, RESPONSE, REGISTER, SEND_MAIL -> {
                        System.out.println(data.message());
                    }
                    default -> {
                        System.out.println("Risposta non riconosciuta");
                    }
                }
            }
            case LOGIN, SEND_MAIL, REGISTER, LOGOUT -> {
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
            String[] commandQuery = query.split(" ");
            String command = commandQuery[0].toLowerCase();
            switch (command) {
                case "/login" -> {
                    if(commandQuery.length == 2 && isValidEmail(commandQuery[1])) {
                        send(new ProtocolMessage<>(MessageType.LOGIN, new LoginData(commandQuery[1])));
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
                    if(commandQuery.length == 2 && isValidEmail(commandQuery[1])) {
                        send(new ProtocolMessage<>(MessageType.REGISTER, new RegisterData(commandQuery[1])));
                        return true;
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

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
