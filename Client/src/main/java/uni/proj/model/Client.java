package uni.proj.model;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import uni.proj.Config;
import uni.proj.model.protocol.ProtocolHandler;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client implements Runnable {

    public enum State {
        STARTED,
        LOGGED,
        STOPPED,
        INITIALIZED
    }

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private final ProtocolHandler protocolHandler;
    private Thread readerThread;
    private final ObjectProperty<State> stateProperty = new SimpleObjectProperty<>();

    private volatile boolean running = true;

    public Client() {
        protocolHandler = new ProtocolHandler();
        setState(State.INITIALIZED);
    }

    @Override
    public void run() {
        while (running) {
            if (!(stateProperty.get() == State.INITIALIZED || stateProperty.get() == State.STOPPED)) {
                break;
            }
            if (stateProperty.get() == State.STOPPED) {
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
                        out.println(msg);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // buona pratica
                        break;
                    }
                }

            } catch (IOException e) {
                System.err.println("Errore di connessione: " + e.getMessage());
            } finally {
                closeResources();
                setState(State.STOPPED);

                if (running) {
                    try {
                        System.out.println("Riconnessione tra 5 secondi...");
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
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
                handleMessage(line);
            }
        } catch (IOException e) {
            System.err.println("Errore durante la lettura: " + e.getMessage());
        } finally {
            closeResources();
            setState(State.STOPPED);
        }
    }

    private void handleMessage(String message) {
        // Qui gestisci il messaggio ricevuto dal server
        System.out.println("Ricevuto dal server: " + message);
        // Potresti aggiornare il model, chiamare observer, ecc.
    }

    public void sendMessage(String message) {
        outgoingMessages.offer(message);
    }

    public boolean execute(String command) {
        if(command.startsWith("/")) {
            command = command.toLowerCase();
            switch (command) {
                default -> {
                    return false;
                }
            }
        } else {
            sendMessage(command);
        }
        return true;
    }

    public void stop() {
        running = false;
        if (readerThread != null) readerThread.interrupt(); // interrompe il thread lettore se necessario
        Thread.currentThread().interrupt(); // interrompe anche questo thread se è in attesa
        outgoingMessages.offer(""); // sblocca il take() nel caso non arrivi mai un messaggio
        closeResources();
        setState(State.STOPPED);
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

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public ObjectProperty<State> getStateProperty() {
        return stateProperty;
    }
}
