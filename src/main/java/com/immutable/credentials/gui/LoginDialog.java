package com.immutable.credentials.gui;

import com.immutable.credentials.auth.AuthService;
import com.immutable.credentials.auth.NodeConfig;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Modal login window shown before the main application window opens.
 * The user enters their 9-digit Israeli national ID and password.
 * On a successful login the window closes and getResult() returns the
 * NodeConfig fetched from the cloud database. On failure or cancellation
 * getResult() returns null, and the application exits.
 */
public class LoginDialog {

    private Stage stage;
    private NodeConfig result;

    private AuthService authService;
    private Label errorLabel;
    private TextField idField;
    private PasswordField passwordField;
    private Button loginButton;

    /**
     * Create and display the login dialog.
     * Blocks (is modal) until the user logs in or closes the window.
     *
     * @param owner      the parent stage this dialog is owned by
     * @param authService the AuthService used to validate credentials
     */
    public LoginDialog(Stage owner, AuthService authService) {
        this.authService = authService;
        buildAndShow(owner);
    }

    /**
     * Return the NodeConfig populated by the database on a successful login,
     * or null if the user cancelled or the login failed.
     *
     * @return the logged-in node configuration, or null
     */
    public NodeConfig getResult() {
        return result;
    }

    /**
     * Build the login stage, wire event handlers, and show it modally.
     *
     * @param owner the parent application stage
     */
    private void buildAndShow(Stage owner) {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);

        // ---- Title ----
        Text title = new Text("Immutable Credentials");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        Text subtitle = new Text("Node Login");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setFill(Color.GRAY);

        VBox titleBox = new VBox(4, title, subtitle);
        titleBox.setAlignment(Pos.CENTER);

        // ---- Form fields ----
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(20, 0, 10, 0));

        Label idLabel = new Label("Israeli ID:");
        idField = new TextField();
        idField.setPromptText("9-digit national ID");
        idField.setPrefWidth(220);

        Label passLabel = new Label("Password:");
        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(220);

        form.add(idLabel, 0, 0);
        form.add(idField, 1, 0);
        form.add(passLabel, 0, 1);
        form.add(passwordField, 1, 1);

        // ---- Error message ----
        errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);
        errorLabel.setVisible(false);

        // ---- Buttons ----
        loginButton = new Button("Login");
        loginButton.setPrefWidth(100);
        loginButton.setDefaultButton(true);

        Button cancelButton = new Button("Exit");
        cancelButton.setPrefWidth(80);
        cancelButton.setCancelButton(true);

        HBox buttonRow = new HBox(10, loginButton, cancelButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        // ---- Root layout ----
        VBox root = new VBox(14, titleBox, form, errorLabel, buttonRow);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #FFFFFF; "
                + "-fx-border-color: #CCCCCC; "
                + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; "
                + "-fx-background-radius: 6;");
        root.setPrefWidth(370);

        // ---- Event handlers ----
        loginButton.setOnAction(e -> onLogin());
        cancelButton.setOnAction(e -> {
            result = null;
            stage.close();
        });
        passwordField.setOnAction(e -> onLogin());

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait();
    }

    /**
     * Attempt login using the values in the ID and password fields.
     * Shows an inline error on failure; closes the stage on success.
     */
    private void onLogin() {
        String israeliId = idField.getText().trim();
        String password = passwordField.getText();

        if (israeliId.isEmpty() || password.isEmpty()) {
            showError("Please enter your Israeli ID and password.");
            return;
        }
        if (!israeliId.matches("\\d{5,10}")) {
            showError("Israeli ID must be 5 to 10 digits.");
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("Connecting...");
        errorLabel.setVisible(false);

        // Run the blocking DB call off the JavaFX thread
        Thread loginThread = new Thread(() -> {
            try {
                NodeConfig config = authService.login(israeliId, password);
                javafx.application.Platform.runLater(() -> {
                    if (config == null) {
                        showError("Invalid Israeli ID or password.");
                        loginButton.setDisable(false);
                        loginButton.setText("Login");
                    } else {
                        result = config;
                        stage.close();
                    }
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    showError("Database error: " + ex.getMessage());
                    loginButton.setDisable(false);
                    loginButton.setText("Login");
                });
            }
        }, "login-thread");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    /**
     * Show an error message below the form fields.
     *
     * @param message the error text to display
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        stage.sizeToScene();
    }
}
