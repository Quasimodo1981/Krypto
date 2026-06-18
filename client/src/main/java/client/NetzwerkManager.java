package client;

import common.KryptoPacket;
import common.PacketType;
import javax.crypto.Cipher;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import common.KryptoEngine;
import common.Protokoll;

public class NetzwerkManager {
    private final CoreController controller;
    private WebSocket webSocketTunnel = null;
    private Thread steuerThread;

    private ScheduledExecutorService heartbeatScheduler;
    private FileOutputStream dateiTargetStream = null;
    private Cipher entschluesselungsCipher = null;

    private volatile boolean laeuft = true;
    private volatile boolean transferAbgebrochen = false;
    private volatile boolean dateiModusAktiv = false;
    private long aktuelleDateiGroesse = 0;
    private long bereitsEmpfangen = 0;

    // --- IDENTITÄTS-MANAGEMENT ---
    private final HashMap<String, String> spitznamenTabelle = new HashMap<>();
    private String partnerUuid = "unbekannt";
    private String partnerWunschName = "Gegenstelle";

    public NetzwerkManager(CoreController controller) {
        this.controller = controller;
    }

    public void starten(String rolle, String zielIp, int port) {
        steuerThread = new Thread(() -> {
            while (laeuft) {
                try {
                    controller.zeigeLiveStatus(false, "Verbindung zum WebSocket-Relay wird aufgebaut...");

                    // Absolut saubere URL mit abschließendem Schrägstrich
                    String relayUri = "wss://kryptochat-relay.onrender.com/";

                    logAnleihe("Verbinde mit Cloud-WebSocket-Relay: " + relayUri + " als " + rolle);

                    // Erstelle den HTTP/1.1 Client mit erhöhtem Timeout für Render-Aufwachzeiten
                    HttpClient client = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(Duration.ofSeconds(60))
                            .build();

                    this.webSocketTunnel = client.newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(60))
                            .buildAsync(URI.create(relayUri), new WebSocket.Listener() {

                                private final StringBuilder messageBuilder = new StringBuilder();

                                @Override
                                public void onOpen(WebSocket webSocket) {
                                    logAnleihe("WebSocket-Handshake erfolgreich! Leitung steht.");

                                    // Wir schicken ein CONNECT-Paket mit unserem Namen an den Server
                                    KryptoPacket loginPacket = new KryptoPacket(PacketType.CONNECT, controller.getEigenerName(), "Möchte chatten");
                                    webSocket.sendText(loginPacket.toJson(), true);

                                    controller.zeigeLiveStatus(true, "VERBUNDEN – Cloud-WebSocket aktiv");
                                    controller.setEingabeAktiv(true);

                                    // Heartbeat starten
                                    startHeartbeatSender();

                                    WebSocket.Listener.super.onOpen(webSocket);
                                }

                                @Override
                                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                    messageBuilder.append(data);
                                    if (last) {
                                        String kompletteZeile = messageBuilder.toString();
                                        messageBuilder.setLength(0);

                                        if ("_NEW_CLIENT_CONNECTED_".equals(kompletteZeile)) {
                                            logAnleihe("Relay meldet: Ein neuer Chat-Teilnehmer hat angedockt!");
                                        } else if ("START_READY".equals(kompletteZeile)) {
                                            // JETZT: Die Gegenseite hat das Signal gegeben, wir starten den Versand!
                                            logAnleihe("Gegenseite ist bereit. Starte echten Datenversand.");
                                            starteEchtenDateiDatenversand();
                                        } else {
                                            verarbeiteEinzelneZeile(kompletteZeile);
                                        }
                                    }
                                    return WebSocket.Listener.super.onText(webSocket, data, last);
                                }

                                @Override
                                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                                    logAnleihe("WebSocket-Verbindung durch Cloud geschlossen. Grund: " + reason);
                                    teardownVerbindung();
                                    return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                                }

                                @Override
                                public void onError(WebSocket webSocket, Throwable error) {
                                    logAnleihe("WebSocket-Fehler aufgetreten: " + error.getMessage());
                                }
                            }).join();

                    // Hält den Thread am Leben, solange der Tunnel offen ist
                    while (laeuft && webSocketTunnel != null && !webSocketTunnel.isOutputClosed()) {
                        Thread.sleep(1000);
                    }

                } catch (Exception e) {
                    logAnleihe("Verbindungsfehler zum WebSocket-Relay: " + e.getMessage());
                } finally {
                    teardownVerbindung();
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { break; }
                }
            }
        });
        steuerThread.setDaemon(true);
        steuerThread.start();
    }

    private void verarbeiteEinzelneZeile(String zeile) {
        try {
            // --- HEARTBEAT FILTER (Können wir als Strings belassen, um Overhead zu sparen) ---
            if ("_HEARTBEAT_PING_".equals(zeile)) {
                if (webSocketTunnel != null) {
                    webSocketTunnel.sendText("_HEARTBEAT_PONG_", true);
                }
                return;
            }
            if ("_HEARTBEAT_PONG_".equals(zeile)) {
                return;
            }

            // --- JETZT PARSEN WIR DAS JSON-PAKET ---
            KryptoPacket packet = KryptoPacket.fromJson(zeile);

            switch (packet.getType()) {
                case CONNECT:
                    // Wenn der Server uns mitteilt, dass sich wer verbunden hat (oder für den Identity-Handshake)
                    this.partnerWunschName = packet.getSender();
                    String anzuzeigenderName = controller.getKonfigManager().getSpitzname(packet.getSender(), packet.getSender());
                    controller.partnerIdentifiziert(anzuzeigenderName, true);
                    logAnleihe("Partner angedockt: " + packet.getSender());
                    break;

                case CHAT_MESSAGE:
                    if ("DATA_END".equals(packet.getPayload()) && "SYSTEM".equals(packet.getSender())) {
                        if (dateiTargetStream != null) {
                            byte[] finaleBytes = entschluesselungsCipher.doFinal();
                            if (finaleBytes != null) dateiTargetStream.write(finaleBytes);
                            dateiTargetStream.flush();
                        }
                        controller.nachrichtEmpfangen("[System] Datei erfolgreich empfangen und entschlüsselt!");
                        schliesseDateiStreamSicher();
                        controller.setSendeUndAbbruchZustand(false);
                    } else {
                        controller.nachrichtEmpfangen(packet.getSender() + ": " + packet.getPayload());
                    }
                    break;

                case FILE_HEADER:
                    // Eine Datei wird angekündigt
                    String dateiName = packet.getFileName();
                    aktuelleDateiGroesse = Long.parseLong(packet.getPayload());
                    bereitsEmpfangen = 0;
                    controller.dateiAnkündigungEmpfangen(dateiName, aktuelleDateiGroesse);
                    break;

                case FILE_CHUNK:
                    // Ein verschlüsseltes Dateihäppchen kommt an
                    if (dateiTargetStream != null && !transferAbgebrochen) {
                        byte[] kryptoBytes = Base64.getDecoder().decode(packet.getPayload());
                        byte[] klarTextBytes = entschluesselungsCipher.update(kryptoBytes);
                        if (klarTextBytes != null) {
                            dateiTargetStream.write(klarTextBytes);
                            bereitsEmpfangen += klarTextBytes.length;
                        }
                        controller.updateFortschritt((double) bereitsEmpfangen / aktuelleDateiGroesse);
                    }
                    break;

                case USER_LIST_UPDATE:
                    // Der Server schickt uns die Liste aller Räume/User (Nützlich für Multichat!)
                    // Hier könntest du später deine UI updaten, wer alles online ist (packet.getPayload())
                    break;
            }

        } catch (Exception e) {
            logAnleihe("Fehler bei der JSON-Paketverarbeitung: " + e.getMessage());
        }
    }

    private void startHeartbeatSender() {
        stopHeartbeatSender();
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // Schickt alle 3 Sekunden ein Lebenszeichen über den WebSocket-Kanal
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (webSocketTunnel != null && !webSocketTunnel.isOutputClosed()) {
                webSocketTunnel.sendText("_HEARTBEAT_PING_", true);
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void stopHeartbeatSender() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
        heartbeatScheduler = null;
    }

    private void schliesseDateiStreamSicher() {
        try { if (dateiTargetStream != null) dateiTargetStream.close(); } catch (IOException e) {}
        dateiTargetStream = null;
        entschluesselungsCipher = null;
    }

    // --- TEXT SENDEN ---
    public void sendeText(String text) {
        if (webSocketTunnel != null && !dateiModusAktiv) {
            // Wir packen den Text in unser neues JSON-Paket
            KryptoPacket packet = new KryptoPacket(PacketType.CHAT_MESSAGE, controller.getEigenerName(), text);
            webSocketTunnel.sendText(packet.toJson(), true);
        }
    }

    // --- DATEIVERSAND VIA WEBSOCKET ---
    private File wartendeDateiZumVersand = null;

    public void sendeDatei(File datei) {
        if (webSocketTunnel == null) return;

        transferAbgebrochen = false;
        dateiModusAktiv = true;
        this.wartendeDateiZumVersand = datei;
        controller.setSendeUndAbbruchZustand(true);

        // Wir senden NUR den Header. Wir warten mit dem Senden der Daten,
        // bis die Gegenseite "START_READY" zurückschickt!
        KryptoPacket packet = new KryptoPacket(PacketType.FILE_HEADER, controller.getEigenerName(), String.valueOf(datei.length()), datei.getName());
        webSocketTunnel.sendText(packet.toJson(), true);
    }

    private void starteEchtenDateiDatenversand() {
        if (wartendeDateiZumVersand == null || webSocketTunnel == null) return;

        new Thread(() -> {
            File datei = wartendeDateiZumVersand;
            try (FileInputStream fis = new FileInputStream(datei)) {
                Cipher cipher = KryptoEngine.getCipher(Cipher.ENCRYPT_MODE);
                byte[] puffer = new byte[12288];
                int gelesen;
                long gesamtGesendet = 0;
                long dateiGroesse = datei.length();

                logAnleihe("Starte Binär-Datenübertragung für: " + datei.getName());

                // Suchen in starteEchtenDateiDatenversand():
                while ((gelesen = fis.read(puffer)) != -1 && !transferAbgebrochen) {
                    byte[] kryptoBytes;
                    if (gesamtGesendet + gelesen >= dateiGroesse) {
                        kryptoBytes = cipher.doFinal(puffer, 0, gelesen);
                    } else {
                        kryptoBytes = cipher.update(puffer, 0, gelesen);
                    }

                    if (kryptoBytes != null && kryptoBytes.length > 0) {
                        String base64Zeile = Base64.getEncoder().encodeToString(kryptoBytes);

                        // HIER GEÄNDERT: Statt "DATA_CHUNK:" senden wir nun ein echtes FILE_CHUNK-Paket
                        KryptoPacket chunkPacket = new KryptoPacket(PacketType.FILE_CHUNK, controller.getEigenerName(), base64Zeile, datei.getName());
                        webSocketTunnel.sendText(chunkPacket.toJson(), true);
                    }
                    gesamtGesendet += gelesen;
                    controller.updateFortschritt((double) gesamtGesendet / dateiGroesse);
                }

                if (transferAbgebrochen) {
                    controller.nachrichtEmpfangen("[System] Dateiübertragung abgebrochen.");
                } else {
                    // HIER GEÄNDERT: Das DATA_END verpacken wir in eine normale Textnachricht oder ein Abbruchsignal –
                    // am einfachsten senden wir ein leeres FILE_CHUNK oder nutzen ein separates Signal.
                    // Wir nutzen einfach eine System-Chatnachricht, die dem Server signalisiert: Fertig!
                    // Oder wir senden ein "Leeres Häppchen" als End-Signal.
                    KryptoPacket endPacket = new KryptoPacket(PacketType.CHAT_MESSAGE, "SYSTEM", "DATA_END");
                    webSocketTunnel.sendText(endPacket.toJson(), true);
                    controller.nachrichtEmpfangen("[System] Datei '" + datei.getName() + "' erfolgreich gesendet!");
                }
            } catch (Exception e) {
                logAnleihe("Fehler beim Dateitransfer: " + e.getMessage());
                controller.nachrichtEmpfangen("[System] Fehler beim Senden: " + e.getMessage());
            } finally {
                wartendeDateiZumVersand = null;
                dateiModusAktiv = false;
                controller.setSendeUndAbbruchZustand(false);
            }
        }).start();
    }

    public void starteDateiEmpfang(File speicherZiel, long dateiGroesse) {
        if (webSocketTunnel == null) return;
        try {
            transferAbgebrochen = false;
            controller.setSendeUndAbbruchZustand(true);
            dateiTargetStream = new FileOutputStream(speicherZiel);
            entschluesselungsCipher = KryptoEngine.getCipher(Cipher.DECRYPT_MODE);
            webSocketTunnel.sendText("START_READY", true);
        } catch (Exception e) {
            logAnleihe("Fehler beim Initialisieren des Empfangs: " + e.getMessage());
            schliesseDateiStreamSicher();
            controller.setSendeUndAbbruchZustand(false);
        }
    }

    public void cancelTransferRequested() {
        if (transferAbgebrochen) return;
        transferAbgebrochen = true;
        controller.nachrichtEmpfangen("[System] Übertragung gestoppt.");
        if (webSocketTunnel != null) {
            webSocketTunnel.sendText(Protokoll.TRANSFER_CANCEL, true);
        }
        controller.setSendeUndAbbruchZustand(false);
        dateiModusAktiv = false;
    }

    private void logAnleihe(String eintrag) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("krypto_transfer.log", true))) {
            pw.println("[" + java.time.LocalDateTime.now() + "] " + eintrag);
        } catch (IOException e) {
            System.err.println("Logdatei konnte nicht geschrieben werden: " + e.getMessage());
        }
    }

    private void teardownVerbindung() {
        stopHeartbeatSender();
        controller.setEingabeAktiv(false);
        webSocketTunnel = null;
        dateiModusAktiv = false;

        this.partnerUuid = "unbekannt";
        this.partnerWunschName = "Gegenstelle";
        controller.partnerIdentifiziert("Kein Partner verbunden", false);
    }

    public void setztLokalenSpitznamen(String neuerSpitzname) {
        if (!this.partnerUuid.equals("unbekannt")) {
            this.spitznamenTabelle.put(this.partnerUuid, neuerSpitzname);
            controller.getKonfigManager().speichereSpitzname(this.partnerUuid, neuerSpitzname);
            logAnleihe("Spitzname fuer UUID " + this.partnerUuid + " dauerhaft gespeichert: " + neuerSpitzname);
        }
    }
}