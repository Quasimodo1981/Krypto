package client;

import javafx.application.Application;

import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        /*
        System.out.println("Krypto-Chat Client wird gestartet...");

        // Wir nutzen den soeben korrigierten KryptoController
        KryptoController controller = new KryptoController();
        controller.starteClient();

        // JETZT NEU: Verhindert, dass der Main-Thread stirbt
        System.out.println("=== Client aktiv. Drücke ENTER zum Beenden ===");
        Scanner scanner = new Scanner(scannerInput());
        scanner.nextLine(); // Blockiert das Programm hier, bis du ENTER drückst

        System.out.println("Client wird heruntergefahren.");
    */
        Application.launch(KryptoGui.class, args);
    }

    // Kleine Hilfsmethode, um den Scanner sauber zu füttern
    private static java.io.InputStream scannerInput() {
        return System.in;
    }
}