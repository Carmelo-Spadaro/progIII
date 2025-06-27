package uni.proj.model;

import uni.proj.model.status.Info;
import uni.proj.model.status.Error;
import uni.proj.model.status.Message;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientHandler implements Runnable {

    private final Server server;
    private final Socket clientSocket;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    public ClientHandler(Server server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                PrintWriter writer = new PrintWriter(output, true)
        ) {
            server.getLogger().log(new Info("handler avviato per " + clientSocket.getRemoteSocketAddress()));

            // Lettura dal client
            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        server.getLogger().log(new Message("ricevuto: " + line + " da " + clientSocket.getRemoteSocketAddress()));
                    }
                } catch (IOException e) {
                    server.getLogger().log(new Error("errore in lettura dal client: " + e.getMessage()));
                } finally {
                    running = false;
                    shutdown();
                }
            });

            readerThread.start();

            // Scrittura verso il client
            while (running) {
                try {
                    String msgToSend = outgoingMessages.take();  // attende un messaggio
                    if (msgToSend.isEmpty()) {  // messaggio di shutdown
                        running = false;
                    } else {
                        writer.println(msgToSend);
                    }
                } catch (InterruptedException e) {
                    server.getLogger().log(new Error("errore in scrittura in client: " + e.getMessage()));
                    running = false;
                }
            }

            readerThread.join(); // aspetta la fine della lettura prima di chiudere

        } catch (IOException | InterruptedException e) {
            server.getLogger().log(new Error("errore nel client handler: " + e.getMessage()));
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                server.getLogger().log(new Error("errore chiudendo il socket: " + e.getMessage()));
            }
            javafx.application.Platform.runLater(() -> {
                boolean removed = server.getClients().remove(this);
                server.getLogger().log(removed ? new Info("rimosso handler: "+ this) : new Error("errore durante la rimozione di handler: "+ this));
            });
        }
    }

    public void shutdown() {
        running = false;                  // ferma il ciclo di scrittura
        // Sblocca il take() inserendo un messaggio "finto"
        outgoingMessages.offer("");       // o meglio un messaggio di shutdown, serve solo per sbloccare il take()
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();     // chiude socket -> fa terminare la lettura (readerThread)
            }
        } catch (IOException e) {
            server.getLogger().log(new Error("errore chiudendo il socket in shutdown: " + e.getMessage()));
        }
    }

    public void send(String message) {
        outgoingMessages.add(message);
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public String toString() {
        return clientSocket.getRemoteSocketAddress().toString();
    }
}
