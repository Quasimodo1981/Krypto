package client;

public interface CoreController {
    void logStatus(String status);
    void nachrichtEmpfangen(String msg);
    void dateiAnkündigungEmpfangen(String name, long größe);
    void setEingabeAktiv(boolean aktiv);
    void setSendeUndAbbruchZustand(boolean transferiert);
    void updateFortschritt(double wert);
    void zeigeEigeneTunnelIp(String ip);
    void zeigeLiveStatus(boolean verbunden, String text);
    KonfigManager getKonfigManager();
    String getEigenerName();
    void partnerIdentifiziert(String anzuzeigenderName, boolean verbunden);
}