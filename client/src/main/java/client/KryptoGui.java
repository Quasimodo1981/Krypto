package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;

public class KryptoGui extends Application implements CoreController {

    private NetzwerkManager netzwerkManager;
    private KonfigManager konfigManager;

    // UI-Elemente, auf die wir aus den Interface-Methoden zugreifen müssen
    private TextArea chatHistorie;
    private TextField eingabeFeld;
    private Button sendeButton;
    private Label statusLabel;

    // Neue UI-Elemente als Klassenvariablen oben in KryptoGui deklarieren:
    private ListView<String> teilnehmerListe;
    private Button dateiButton;

    @Override
    public void start(Stage primaryStage) {
        // 1. Manager initialisieren
        this.konfigManager = new KonfigManager();
        zeigeLoginDialog(); // Erst Name abfragen!

        this.netzwerkManager = new NetzwerkManager(this);

        // 2. ALLE UI-Komponenten initialisieren (Wichtig: Keine Komponente darf null sein!)
        chatHistorie = new TextArea();
        chatHistorie.setEditable(false);
        chatHistorie.setWrapText(true);

        eingabeFeld = new TextField();
        eingabeFeld.setPromptText("Deine verschlüsselte Nachricht...");
        eingabeFeld.setDisable(true);

        sendeButton = new Button("Senden");
        sendeButton.setDisable(true);

        dateiButton = new Button("📎"); // <-- Jetzt garantiert VOR der HBox initialisiert!
        dateiButton.setDisable(true);

        statusLabel = new Label("Verbindung wird initialisiert...");

        teilnehmerListe = new ListView<>();
        teilnehmerListe.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        teilnehmerListe.getItems().add("📢 Alle (Broadcast)");
        teilnehmerListe.getSelectionModel().selectFirst();
        teilnehmerListe.setPrefWidth(150);

        // Context-Menü für die Teilnehmerliste (Rechtsklick zum lokalen Umbenennen)
        ContextMenu kontextMenue = new ContextMenu();
        MenuItem umbenennenItem = new MenuItem("Lokal umbenennen");
        umbenennenItem.setOnAction(e -> {
            String ausgewaehlterUser = teilnehmerListe.getSelectionModel().getSelectedItem();
            if (ausgewaehlterUser != null && !ausgewaehlterUser.startsWith("📢")) {
                TextInputDialog renameDialog = new TextInputDialog(ausgewaehlterUser);
                renameDialog.setTitle("Spitzname vergeben");
                renameDialog.setHeaderText("Wie möchtest du " + ausgewaehlterUser + " für dich nennen?");
                renameDialog.showAndWait().ifPresent(neuerName -> {
                    netzwerkManager.setztLokalenSpitznamen(neuerName);
                });
            }
        });
        kontextMenue.getItems().add(umbenennenItem);
        teilnehmerListe.setContextMenu(kontextMenue);

        // Event-Handling für Eingabe und Buttons
        sendeButton.setOnAction(e -> nachrichtAbsenden());
        eingabeFeld.setOnAction(e -> nachrichtAbsenden());
        dateiButton.setOnAction(e -> dateiAuswaehlenUndSenden());

        // 3. Erst JETZT das Layout zusammenbauen, da alle Kinder existieren
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Unten: Datei-Button, Eingabefeld, Sende-Button
        HBox untenKombi = new HBox(10, dateiButton, eingabeFeld, sendeButton);
        HBox.setHgrow(eingabeFeld, javafx.scene.layout.Priority.ALWAYS);

        root.setLeft(teilnehmerListe);
        root.setCenter(chatHistorie);
        root.setBottom(untenKombi);
        root.setTop(statusLabel);

        BorderPane.setMargin(chatHistorie, new Insets(0, 0, 0, 10));
        BorderPane.setMargin(untenKombi, new Insets(10, 0, 0, 0));

        // 4. Szene anzeigen
        Scene scene = new Scene(root, 650, 450); // Etwas breiter für die Liste
        primaryStage.setTitle("KryptoChat - Sicher verbunden");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 5. Netzwerk im Hintergrund zünden
        netzwerkManager.starten("client", "localhost", 10000);
    }

    private java.util.List<String> getAusgewaehlteEmpfaenger() {
        javafx.collections.ObservableList<String> selectedItems = teilnehmerListe.getSelectionModel().getSelectedItems();
        java.util.List<String> empfaenger = new java.util.ArrayList<>();

        for (String item : selectedItems) {
            if (item.startsWith("📢")) {
                // Wenn "Alle" mit in der Auswahl ist, erzwingen wir sofort einen reinen Broadcast
                empfaenger.clear();
                empfaenger.add("ALL");
                return empfaenger;
            }
            empfaenger.add(item);
        }

        if (empfaenger.isEmpty()) {
            empfaenger.add("ALL");
        }
        return empfaenger;
    }

    private void nachrichtAbsenden() {
        String text = eingabeFeld.getText().trim();
        if (!text.isEmpty()) {
            java.util.List<String> empfaenger = getAusgewaehlteEmpfaenger();

            // Ab an den NetzwerkManager
            netzwerkManager.sendeText(text, empfaenger);

            // Lokal im Chatfenster anzeigen
            String ziel = empfaenger.contains("ALL") ? "alle" : String.join(", ", empfaenger);
            chatHistorie.appendText("[An " + ziel + "] Du: " + text + "\n");
            eingabeFeld.clear();
        }
    }

    private void dateiAuswaehlenUndSenden() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Datei zum Verschlüsseln auswählen");
        java.io.File datei = fileChooser.showOpenDialog(chatHistorie.getScene().getWindow());

        if (datei != null) {
            java.util.List<String> empfaenger = getAusgewaehlteEmpfaenger();
            String ziel = empfaenger.contains("ALL") ? "alle (Broadcast)" : String.join(", ", empfaenger);

            chatHistorie.appendText("[System] Sende Datei '" + datei.getName() + "' an " + ziel + "...\n");

            // Datei an die Liste senden
            netzwerkManager.sendeDatei(datei, empfaenger);
        }
    }

    @Override
    public void setEingabeAktiv(boolean aktiv) {
        Platform.runLater(() -> {
            eingabeFeld.setDisable(!aktiv);
            sendeButton.setDisable(!aktiv);
            dateiButton.setDisable(!aktiv); // <-- Jetzt auch der Dateibutton aktiv!
        });
    }

    // --- IMPLEMENTIERUNG DES CORE-CONTROLLERS (JavaFX-Thread sicher via Platform.runLater) ---

    @Override
    public void nachrichtEmpfangen(String msg) {
        Platform.runLater(() -> chatHistorie.appendText(msg + "\n"));
    }

    @Override
    public void zeigeLiveStatus(boolean verbunden, String text) {
        Platform.runLater(() -> statusLabel.setText("Status: " + text));
    }

    @Override
    public void logStatus(String status) {
        Platform.runLater(() -> chatHistorie.appendText("[System] " + status + "\n"));
    }

    @Override
    public File dateiAnkündigungEmpfangen(String sender, String dateiName, long größe) {
        // Ein FutureTask, das exakt ein java.io.File zurückliefert
        java.util.concurrent.FutureTask<java.io.File> dialogTask = new java.util.concurrent.FutureTask<>(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Eingehende Datei");
            alert.setHeaderText("Datei-Transfer angefordert");
            alert.setContentText(sender + " möchte dir eine Datei senden:\n\n" +
                    "Name: " + dateiName + "\n" +
                    "Größe: " + (größe / 1024) + " KB\n\n" +
                    "Möchtest du diese Datei empfangen?");

            ButtonType buttonJa = new ButtonType("Annehmen");
            ButtonType buttonNein = new ButtonType("Ablehnen", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(buttonJa, buttonNein);

            java.util.Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == buttonJa) {
                javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
                fileChooser.setTitle("Datei speichern unter...");
                fileChooser.setInitialFileName(dateiName);

                // Reicht das ausgewählte File weiter
                return fileChooser.showSaveDialog(chatHistorie.getScene().getWindow());
            }
            return null; // Abgelehnt
        });

        // Schiebe das Task auf den JavaFX Application Thread
        Platform.runLater(dialogTask);

        try {
            // Blockiert den HINTERGRUND-Thread (NetzwerkManager), NICHT die GUI, bis der User klickt
            return dialogTask.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void abbrechenMeldungSenden() {
        chatHistorie.appendText("[System] Dateiübertragung abgelehnt.\n");
        netzwerkManager.cancelTransferRequested();
    }

    @Override public void setSendeUndAbbruchZustand(boolean transferiert) {}
    @Override public void updateFortschritt(double wert) {}
    @Override public void zeigeEigeneTunnelIp(String ip) {}
    @Override public KonfigManager getKonfigManager() { return this.konfigManager; }
    
    @Override 
    public String getEigenerName() { 
        return konfigManager.getName().isEmpty() ? "User-" + konfigManager.getUuid().substring(0, 4) : konfigManager.getName(); 
    }
    
    @Override public void partnerIdentifiziert(String anzuzeigenderName, boolean verbunden) {
        Platform.runLater(() -> chatHistorie.appendText("[System] Partner " + anzuzeigenderName + " ist jetzt " + (verbunden ? "online" : "offline") + ".\n"));
    }

    private void zeigeLoginDialog() {
        TextInputDialog dialog = new TextInputDialog(konfigManager.getName().isEmpty() ? "User" : konfigManager.getName());
        dialog.setTitle("KryptoChat - Anmeldung");
        dialog.setHeaderText("Bitte wähle deinen Chat-Namen:");
        dialog.setContentText("Name:");

        // Blockiert, bis der Nutzer den Dialog schließt
        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            konfigManager.speichereName(result.get().trim());
        } else if (konfigManager.getName().isEmpty()) {
            // Fallback, falls abgebrochen wurde und kein Name existiert
            konfigManager.speichereName("User-" + konfigManager.getUuid().substring(0, 4));
        }
    }

    @Override
    public void updateTeilnehmerListe(java.util.List<String> userListe) {
        Platform.runLater(() -> {
            // Wir merken uns, wer gerade ausgewählt war, damit die Auswahl nicht springt
            String aktuellAusgewaehlt = teilnehmerListe.getSelectionModel().getSelectedItem();

            teilnehmerListe.getItems().clear();
            teilnehmerListe.getItems().add("📢 Alle (Broadcast)");

            for (String user : userListe) {
                // Wir fügen uns nicht selbst in die Liste ein, wir wollen ja nicht mit uns selbst privat chatten
                if (!user.equals(getEigenerName())) {
                    // Prüfen, ob wir einen lokalen Spitznamen für diese Person in der Config haben
                    String anzuzeigenderName = konfigManager.getSpitzname(user, user);
                    teilnehmerListe.getItems().add(anzuzeigenderName);
                }
            }

            // Auswahl wiederherstellen, falls derjenige noch da ist, sonst "Alle"
            if (teilnehmerListe.getItems().contains(aktuellAusgewaehlt)) {
                teilnehmerListe.getSelectionModel().select(aktuellAusgewaehlt);
            } else {
                teilnehmerListe.getSelectionModel().selectFirst();
            }
        });
    }
}