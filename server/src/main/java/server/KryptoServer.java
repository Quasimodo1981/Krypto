package server;

import common.KryptoPacket;
import common.PacketType;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KryptoServer extends WebSocketServer {

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
            // 1. SCHUTZSCHILD: Wenn es ein reines Text-Signal ist, nicht als JSON parsen!
            if ("START_READY".equals(message)) {
                System.out.println("[Signal] START_READY empfangen. Leite an alle potenziellen Sender weiter.");
                broadcastExcept(message, conn);
                return;
            }

            if (message.startsWith("_HEARTBEAT_")) {
                // Heartbeats filtert der Server direkt ab (falls sie nicht schon im Client beantwortet werden)
                return;
            }

            // 2. Reguläre JSON-Pakete verarbeiten
            KryptoPacket packet = KryptoPacket.fromJson(message);

            switch (packet.getType()) {
                case CONNECT:
                    clientMap.put(conn, packet.getSender());
                    System.out.println("Client erfolgreich registriert: " + packet.getSender());
                    broadcastUserList();
                    break;

                case CHAT_MESSAGE:
                case FILE_HEADER:
                case FILE_CHUNK:
                    java.util.List<String> empfaengerListe = packet.getRecipients();
                    if (empfaengerListe == null || empfaengerListe.contains("ALL")) {
                        broadcastExcept(message, conn);
                    } else {
                        // Das Signal wird nun genau wie Chunks exakt an den Empfänger geroutet!
                        for (String targetName : empfaengerListe) {
                            routeToTarget(message, targetName);
                        }
                    }
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

    // Hilfsmethode: Sendet das Paket gezielt NUR an die Session des Zielnutzers
    private boolean routeToTarget(String rawMessage, String targetName) {
        for (Map.Entry<WebSocket, String> entry : clientMap.entrySet()) {
            if (entry.getValue().equals(targetName)) {
                WebSocket targetConn = entry.getKey();
                if (targetConn.isOpen()) {
                    targetConn.send(rawMessage);
                    return true; // Gefunden und zugestellt
                }
            }
        }
        return false;
    }

    private void broadcastExcept(String rawMessage, WebSocket excludeConn) {
        for (WebSocket client : clientMap.keySet()) {
            if (client != excludeConn && client.isOpen()) {
                client.send(rawMessage);
            }
        }
    }

    private void broadcastUserList() {
        // Wir filtern "Unbekannt" heraus, falls sich jemand gerade erst verbindet
        java.util.List<String> aktiveUser = clientMap.values().stream()
                .filter(name -> !"Unbekannt".equals(name))
                .toList();

        String userList = String.join(",", aktiveUser);
        KryptoPacket updatePacket = new KryptoPacket(PacketType.USER_LIST_UPDATE, "SERVER", userList);
        broadcastExcept(updatePacket.toJson(), null);
    }

    public static void main(String[] args) {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 10000;

        KryptoServer server = new KryptoServer(port);
        server.start();
    }
}