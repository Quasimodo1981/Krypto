package common;

public enum PacketType {
    CONNECT,              // Client meldet sich an
    CHAT_MESSAGE,         // Reine Textnachricht (verschlüsselt)
    FILE_HEADER,          // Metadaten einer Datei vorab (Name, Größe)
    FILE_CHUNK,           // Ein einzelnes Datenpaket einer Datei (verschlüsselt)
    USER_LIST_UPDATE      // Server schickt aktuelle Benutzerliste an alle
}