package client;

import java.io.File;

public interface CoreController {
    void logStatus(String status);
    void nachrichtEmpfangen(String msg);
    java.io.File dateiAnkündigungEmpfangen(String sender, String dateiName, long größe);
    void setEingabeAktiv(boolean aktiv);
    void setSendeUndAbbruchZustand(boolean transferiert);
    void updateFortschritt(double wert);
    void zeigeEigeneTunnelIp(String ip);
    void zeigeLiveStatus(boolean verbunden, String text);
    KonfigManager getKonfigManager();
    String getEigenerName();
    void partnerIdentifiziert(String anzuzeigenderName, boolean verbunden);
    void updateTeilnehmerListe(java.util.List<String> userListe);
}