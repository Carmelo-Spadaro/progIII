package uni.proj.model;

import com.google.gson.JsonSyntaxException;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import uni.proj.model.protocol.MessageType;
import uni.proj.model.protocol.ProtocolMessage;
import uni.proj.model.protocol.data.*;
import uni.proj.model.status.Info;
import uni.proj.model.status.Error;
import uni.proj.model.status.Message;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {

    private final Server server;
    private final Socket clientSocket;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private boolean isLogged = false;

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
                        try {
                            // Prova a decodificare usando il protocol handler
                            ProtocolMessage<?> message = server.getProtocolHandler().decode(line);

                            handleMessage(message);

                        } catch (JsonSyntaxException e) {
                            // Messaggio non valido secondo il nostro protocollo
                            server.getLogger().log(new Error("Messaggio malformato da " + clientSocket.getRemoteSocketAddress() + ": " + line));
                        }
                    }
                } catch (IOException e) {
                    server.getLogger().log(new Error("Errore in lettura dal client: " + e.getMessage()));
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

    private void handleMessage(ProtocolMessage<?> message) {
        switch (message.type()) {
            case CHAT -> {
                ChatData data = (ChatData) message.data();
                server.getLogger().log(new Message("Messaggio da " + clientSocket.getRemoteSocketAddress() + ": " + data.message()));
            }
            case RESPONSE -> {
                ResponseData data = (ResponseData) message.data();
                server.getLogger().log(new Message("Risposta da " + clientSocket.getRemoteSocketAddress() + ": " + data.message()));
            }
            case LOGIN -> {
                LoginData data = (LoginData) message.data();
                server.getLogger().log(new Message("Richiesta di Login da "+ clientSocket.getRemoteSocketAddress()));
                if(isLogged) {
                    server.send(new ProtocolMessage<>(MessageType.ERROR, new ErrorData("Client gia' loggato")), List.of(this));
                    server.getLogger().log(new Info("richiesta di Login fallita, client gia' loggato"));
                    return;
                }
                if (!isValidEmail(data.email())) {
                    server.send(new ProtocolMessage<>(MessageType.ERROR, new ErrorData("Email non valida")), List.of(this));
                    server.getLogger().log(new Info("richiesta di Login fallita, formato email non valido"));
                    return;
                }

                Platform.runLater(() -> {
                    ObservableList<RegisterData> emails = server.getEmails();

                    boolean isRegistered = emails.stream()
                            .anyMatch(r -> r.email().equalsIgnoreCase(data.email()));

                    if (isRegistered) {
                        server.send(new ProtocolMessage<>(MessageType.RESPONSE, new ResponseData("Login eseguito")), List.of(this));
                        server.getLogger().log(new Info("richiesta di login eseguita"));
                        isLogged = true;
                    } else {
                        server.send(new ProtocolMessage<>(MessageType.ERROR, new ErrorData("Mail non registrata")), List.of(this));
                        server.getLogger().log(new Info("richiesta di login fallita"));
                    }
                });
            }
            case LOGOUT -> {
                server.getLogger().log(new Message("Richiesta di Logout da " + clientSocket.getRemoteSocketAddress()));
                if(!isLogged) {
                    server.send(new ProtocolMessage<>(MessageType.ERROR, new ErrorData("Client gia' disconnesso")), List.of(this));
                    server.getLogger().log(new Info("richiesta di Logout fallita, client gia' disconnesso"));
                } else {
                    server.send(new ProtocolMessage<>(MessageType.RESPONSE, new ResponseData("Logout eseguito")), List.of(this));
                    server.getLogger().log(new Info("richiesta di Logout completata"));
                    isLogged = false;
                }
            }
            case REGISTER -> {
                RegisterData data = (RegisterData) message.data();
                server.getLogger().log(new Message("Richiesta di Register da " + clientSocket.getRemoteSocketAddress()));
                if (!isValidEmail(data.email())) {
                    server.send(new ProtocolMessage<>(MessageType.ERROR, new ErrorData("Email non valida")), List.of(this));
                    server.getLogger().log(new Info("richiesta di Register fallita, formato email non valido"));
                    return;
                }

                Platform.runLater(() -> {
                    ObservableList<RegisterData> emails = server.getEmails();

                    boolean isRegistered = emails.stream()
                            .anyMatch(r -> r.email().equalsIgnoreCase(data.email()));

                    if (isRegistered) {
                        server.send(new ProtocolMessage<>(MessageType.ERROR, new ErrorData("questa email e' gia' registrata")), List.of(this));
                        server.getLogger().log(new Info("richiesta di Register fallita, mail gia' registrata"));
                    } else {
                        emails.add(data);
                        server.send(new ProtocolMessage<>(MessageType.RESPONSE, new ResponseData("mail registrata")), List.of(this));
                        server.getLogger().log(new Info("richiesta di Register completata"));
                        new Thread(() -> server.saveRegister(data)).start();
                    }
                });
            }
            case SEND_MAIL -> {
                server.getLogger().log(new Message("Richiesta di Send Mail da " + clientSocket.getRemoteSocketAddress()));
            }
            case ERROR -> {
                ErrorData data = (ErrorData) message.data();
                server.getLogger().log(new Error("Errore da " + clientSocket.getRemoteSocketAddress() + ": " + data.message()));
            }
            default -> {
                server.getLogger().log(new Error("Tipo di richiesta da " + clientSocket.getRemoteSocketAddress() + " non riconosciuta"));
                server.send(new ProtocolMessage<>(MessageType.ERROR, new ErrorData("tipo di richiesta non riconosciuta")), List.of(this));
            }
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
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
