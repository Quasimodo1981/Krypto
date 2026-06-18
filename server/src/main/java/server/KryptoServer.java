package server;

import common.KryptoPacket;
import common.PacketType;
import org.java-websocket.WebSocket;
import org.java-websocket.handshake.ClientHandshake;
import org.java-websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KryptoServer extends WebSocketServer {

    // Speichert alle aktiven Verbindungen und ordnet ihnen (falls bekannt) eine UUID oder einen Namen zu
    private final Map<WebSocket, String> clientMap = new ConcurrentHashMap<>();

    public KryptoServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onStart() {
        System.out.println("Krypto-Zentral-Server erfolgreich auf Port " + getPort() + " gestartet!");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Neuer Client versucht anzudocken: " + conn.getRemoteSocketAddress());
        // Temporär in der Map registrieren, bis er sein IDENTITY-Paket schickt
        clientMap.put(conn, "Unbekannt");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clientMap.remove(conn);
        System.out.println("Verbindung getrennt: " + name + " (" + conn.getRemoteSocketAddress() + ")");
        broadcastUserList();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            // Hier kommt die Magie: Wir parsen das JSON direkt in unser Common-Objekt!
            KryptoPacket packet = KryptoPacket.fromJson(message);

            switch (packet.getType()) {
                case CONNECT:
                    // Client meldet sich mit seinem Namen an
                    clientMap.put(conn, packet.getSender());
                    System.out.println("Client erfolgreich registriert: " + packet.getSender());
                    broadcastUserList();
                    break;

                case CHAT_MESSAGE:
                    System.out.println("Chat von [" + packet.getSender() + "]: Leite an alle weiter.");
                    // Multichat-Logik: Nachricht an ALLE verbundenen Clients weiterleiten
                    broadcastExcept(message, null);
                    break;

                case FILE_HEADER:
                case FILE_CHUNK:
                    // Für den anonymen oder gezielten File-Transfer:
                    // Aktuell leiten wir es an alle weiter (außer den Sender selbst), 
                    // damit die Gegenstelle es abfangen kann.
                    broadcastExcept(message, conn);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Verarbeiten der Nachricht: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Server-Fehler bei Verbindung " + (conn != null ? conn.getRemoteSocketAddress() : "global") + ": " + ex.getMessage());
    }

    // Hilfsmethode: Sendet eine Nachricht an alle außer an den angegebenen Client
    private void broadcastExcept(String rawMessage, WebSocket excludeConn) {
        for (WebSocket client : clientMap.keySet()) {
            if (client != excludeConn && client.isOpen()) {
                client.send(rawMessage);
            }
        }
    }

    // Hilfsmethode: Sendet die Liste aller online User an alle Clients
    private void broadcastUserList() {
        String userList = String.join(",", clientMap.values());
        KryptoPacket updatePacket = new KryptoPacket(PacketType.USER_LIST_UPDATE, "SERVER", userList);
        broadcastExcept(updatePacket.toJson(), null);
    }

    // Haupt-Startmethode für den Render-Server
    public static void main(String[] args) {
        // Render weist uns dynamisch einen Port über die Umgebungsvariable "PORT" zu. 
        // Wenn lokal gestartet wird, nutzen wir standardmäßig 10000.
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 10000;

        KryptoServer server = new KryptoServer(port);
        server.start();
    }
}