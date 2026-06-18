package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

public class KonfigManager {
    private static final String DATEI_NAME = "config.txt";
    private final Properties props = new Properties();

    private String uuid;
    private String name = "";
    private boolean istServer = false;

    public KonfigManager() {
        ladenOderInitialisieren();
    }

    // Speichert den Spitznamen für eine bestimmte Rechner-UUID
    public void speichereSpitzname(String uuid, String spitzname) {
        props.setProperty("spitzname." + uuid, spitzname.trim());
        speichern();
    }

    // Holt den Spitznamen für eine UUID. Wenn keiner existiert, gibt er den Standardnamen zurück
    public String getSpitzname(String uuid, String standardName) {
        return props.getProperty("spitzname." + uuid, standardName);
    }

    private void ladenOderInitialisieren() {
        File datei = new File(DATEI_NAME);
        
        // 1. Laden falls Datei existiert
        if (datei.exists()) {
            try (FileInputStream fis = new FileInputStream(datei)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("Konfiguration konnte nicht geladen werden: " + e.getMessage());
            }
        }

        // 2. UUID prüfen oder neu generieren
        this.uuid = props.getProperty("uuid");
        if (this.uuid == null || this.uuid.trim().isEmpty()) {
            this.uuid = UUID.randomUUID().toString();
            props.setProperty("uuid", this.uuid);
        }

        // 3. Rolle prüfen (Strenges Opt-In: NUR bei explizitem 'server' ist es ein Server)
        String rolle = props.getProperty("rolle", "client");
        this.istServer = "server".equalsIgnoreCase(rolle.trim());

        // 4. Name laden
        this.name = props.getProperty("name", "").trim();

        // Immer einmal speichern, damit eine fehlende UUID direkt festgeschrieben wird
        speichern();
    }

    public void speichereName(String neuerName) {
        this.name = neuerName.trim();
        props.setProperty("name", this.name);
        speichern();
    }

    private void speichern() {
        try (FileOutputStream fos = new FileOutputStream(DATEI_NAME)) {
            props.store(fos, "KryptoTransfer Konfiguration - Bitte UUID nicht veraendern!");
        } catch (IOException e) {
            System.err.println("Konfiguration konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // --- GETTER ---
    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public boolean istServer() { return istServer; }
}