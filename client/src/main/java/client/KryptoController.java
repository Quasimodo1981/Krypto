package client;

import java.io.File;

public class KryptoController implements CoreController {

    private final NetzwerkManager netzwerkManager;
    private final KonfigManager konfigManager;

    @Override
    public void updateTeilnehmerListe(java.util.List<String> userListe) {
        System.out.println("[Terminal-Controller] Aktuelle User im Netz: " + userListe);
    }

    public KryptoController() {
        this.konfigManager = new KonfigManager();
        // Fehler 2 Fix: Nur 'this' übergeben, da der Manager nur den Controller braucht
        this.netzwerkManager = new NetzwerkManager(this);
    }

    public void starteClient() {
        System.out.println("Verbinde zum Render-Server...");
        // Fehler 3 Fix: Die richtige Methode 'starten' mit den passenden Parametern aufrufen
        // Da die URL im Manager fest verdrahtet ist, sind IP und Port hier Dummy-Werte
        netzwerkManager.starten("client", "localhost", 10000);
    }

    // --- HIER SIND NUN ALLE METHODEN AUS DEM INTERFACE IMPLEMENTIERT ---

    @Override
    public void partnerIdentifiziert(String anzuzeigenderName, boolean verbunden) {
        // Fehler 1 Fix: Diese Methode MUSS exakt so hier drin stehen
        System.out.println("[Partner-Update] " + anzuzeigenderName + " ist nun " + (verbunden ? "online" : "offline"));
    }

    @Override
    public void logStatus(String status) {
        System.out.println("[Status] " + status);
    }

    @Override
    public void nachrichtEmpfangen(String msg) {
        System.out.println("[Chat] " + msg);
    }

    @Override
    public File dateiAnkündigungEmpfangen(String sender, String dateiName, long größe) {
        System.out.println("\n[Terminal-Controller] 📎 Eingehende Datei von " + sender + ":");
        System.out.println("  -> Name: " + dateiName);
        System.out.println("  -> Größe: " + größe + " Bytes");
        System.out.println("[Terminal-Controller] Akzeptiere Datei automatisch und speichere im Projektordner.");

        // Automatische Bestimmung des Speicherorts im aktuellen Verzeichnis
        return new java.io.File("terminal_download_" + dateiName);
    }

    @Override
    public void setEingabeAktiv(boolean aktiv) {
        System.out.println("[UI] Eingabe aktiv: " + aktiv);
    }

    @Override
    public void setSendeUndAbbruchZustand(boolean transferiert) {
        System.out.println("[UI] Transfer-Zustand geändert: " + transferiert);
    }

    @Override
    public void updateFortschritt(double wert) {
        System.out.println("[Fortschritt] " + (wert * 100) + "%");
    }

    @Override
    public void zeigeEigeneTunnelIp(String ip) {
        System.out.println("[Netzwerk] Eigene IP: " + ip);
    }

    @Override
    public void zeigeLiveStatus(boolean verbunden, String text) {
        System.out.println("[Live-Status] " + text);
    }

    @Override
    public KonfigManager getKonfigManager() {
        return this.konfigManager;
    }

    @Override
    public String getEigenerName() {
        return konfigManager.getName().isEmpty() ? "User-" + konfigManager.getUuid().substring(0, 4) : konfigManager.getName();
    }
}