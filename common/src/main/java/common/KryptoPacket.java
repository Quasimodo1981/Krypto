package common;

import com.google.gson.Gson;

public class KryptoPacket {
    private static final Gson gson = new Gson();

    private PacketType type;
    private String sender;
    private String payload;    // Verschlüsselter Chat-Text ODER Base64-verschlüsselte Dateihäppchen
    private String fileName;   // Optional: Nur für Dateitransfers wichtig

    // Konstruktor für die einfache Erstellung
    public KryptoPacket(PacketType type, String sender, String payload) {
        this.type = type;
        this.sender = sender;
        this.payload = payload;
    }

    // Konstruktor mit Dateiname (für File-Transfer)
    public KryptoPacket(PacketType type, String sender, String payload, String fileName) {
        this.type = type;
        this.sender = sender;
        this.payload = payload;
        this.fileName = fileName;
    }

    // Hilfsmethoden zur JSON-Umwandlung (Serialisierung)
    public String toJson() {
        return gson.toJson(this);
    }

    public static KryptoPacket fromJson(String json) {
        return gson.fromJson(json, KryptoPacket.class);
    }

    // Getter (und ggf. Setter)
    public PacketType getType() { return type; }
    public String getSender() { return sender; }
    public String getPayload() { return payload; }
    public String getFileName() { return fileName; }
}