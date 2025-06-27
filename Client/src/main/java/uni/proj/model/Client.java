package uni.proj.model;

import uni.proj.Config;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private Thread readerThread;

    private volatile boolean running = true;

    public Client() {
    }

    @Override
    public void run() {
        try {
            socket = new Socket(Config.SERVER_ADDRESS, Config.SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Thread di lettura
            readerThread = new Thread(this::readMessages);
            readerThread.setDaemon(true);
            readerThread.start();

            // Scrittura
            while (running) {
                String msg;
                try {
                    msg = outgoingMessages.take();
                } catch (InterruptedException e) {
                    break; // uscita sicura
                }
                out.println(msg);
            }

        } catch (IOException e) {
            System.err.println("Errore di connessione: " + e.getMessage());
        } finally {
            closeResources();
        }
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
            running = false;
            closeResources();
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
}
