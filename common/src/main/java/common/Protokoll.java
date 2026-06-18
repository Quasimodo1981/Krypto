package common;

public interface Protokoll {
    // Steuerbefehle für die Chat-/Steuerleitung
    String MSG_PREFIX = "MSG:";              // Reine Textnachricht
    String FILE_ANNOUNCE = "FILE_ANNOUNCE:";  // Ankündigung: Dateiname|Größe
    String FILE_ACCEPT = "FILE_ACCEPT";      // Empfänger akzeptiert (Start ab Byte 0)
    String FILE_RESUME = "FILE_RESUME:";      // Empfänger will fortsetzen ab ByteX: OFFSET|Bytes
    String FILE_DECLINE = "FILE_DECLINE";    // Empfänger lehnt Datei ab
    String TRANSFER_CANCEL = "CANCEL";       // Abbruch durch eine der Parteien
}