package common;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

public class KryptoPacket {
    private static final Gson gson = new Gson();

    private PacketType type;
    private String sender;
    private List<String> recipients; // <-- JETZT NEU: Eine Liste von Empfängern!
    private String payload;
    private String fileName;

    // Konstruktor für Broadcast (Standard)
    public KryptoPacket(PacketType type, String sender, String payload) {
        this.type = type;
        this.sender = sender;
        this.recipients = new ArrayList<>();
        this.recipients.add("ALL");
        this.payload = payload;
    }

    // Konstruktor mit einer Liste von Empfängern
    public KryptoPacket(PacketType type, String sender, List<String> recipients, String payload) {
        this.type = type;
        this.sender = sender;
        this.recipients = recipients == null ? new ArrayList<>() : recipients;
        if (this.recipients.isEmpty()) this.recipients.add("ALL");
        this.payload = payload;
    }

    // Konstruktor mit Liste und Dateiname
    public KryptoPacket(PacketType type, String sender, List<String> recipients, String payload, String fileName) {
        this.type = type;
        this.sender = sender;
        this.recipients = recipients == null ? new ArrayList<>() : recipients;
        if (this.recipients.isEmpty()) this.recipients.add("ALL");
        this.payload = payload;
        this.fileName = fileName;
    }

    public String toJson() { return gson.toJson(this); }
    public static KryptoPacket fromJson(String json) { return gson.fromJson(json, KryptoPacket.class); }

    public PacketType getType() { return type; }
    public String getSender() { return sender; }
    public List<String> getRecipients() { return recipients; }
    public String getPayload() { return payload; }
    public String getFileName() { return fileName; }
}