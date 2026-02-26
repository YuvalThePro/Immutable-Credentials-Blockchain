package com.immutable.credentials.gui;

import com.immutable.credentials.auth.NodeConfig;
import com.immutable.credentials.service.AdminService;
import com.immutable.credentials.crypto.CryptoUtils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * JavaFX admin panel accessible only to institution nodes (VALIDATOR and UNIVERSITY).
 * Students and other READ_ONLY nodes cannot see this tab.
 *
 * Two sections are provided:
 *  1. Register New User: all institution admins can register a staff member
 *     (UNIVERSITY, can issue credentials) or a student (READ_ONLY, verify only).
 *  2. Register New Institution: VALIDATOR admins only can register a new
 *     non-validator institution (creates a UNIVERSITY node account).
 *
 * Each registration generates a random password that is shown once to the admin
 * to pass on to the new user. The password is stored in the database as a
 * SHA-256 digest via CryptoUtils.sha256.
 */
public class AdminPanel extends VBox {

    private final AdminService adminService;
    private final boolean isValidator;

    // ---- Register User section ----
    private TextField userIdField;
    private TextField userNameField;
    private ComboBox<String> userRoleBox;
    private TextField userPortField;
    private TextField userDataDirField;
    private Label userResultLabel;
    private TextField userPasswordDisplay;

    // ---- Register Institution section (VALIDATOR only) ----
    private TextField instNameField;
    private TextField instAdminIdField;
    private TextField instAdminNameField;
    private TextField instPortField;
    private TextField instDataDirField;
    private Label instResultLabel;
    private TextField instPasswordDisplay;

    /**
     * Create the admin panel for the given admin service.
     *
     * @param adminService the AdminService bound to the logged-in institution admin
     */
    public AdminPanel(AdminService adminService) {
        this.adminService = adminService;
        NodeConfig cfg = adminService.getAdminConfig();
        this.isValidator = "VALIDATOR".equals(cfg.getNodeType());

        setSpacing(0);
        setPadding(new Insets(20));

        Text heading = new Text("Admin Panel");
        heading.setFont(Font.font("System", FontWeight.BOLD, 18));

        Text subheading = new Text(cfg.getInstitution()
                + " \u2014 " + cfg.getNodeType());
        subheading.setFont(Font.font("System", 13));
        subheading.setFill(Color.GRAY);

        VBox header = new VBox(4, heading, subheading);
        header.setPadding(new Insets(0, 0, 16, 0));

        getChildren().addAll(header, buildRegisterUserSection());

        if (isValidator) {
            getChildren().addAll(buildSectionDivider(), buildRegisterInstitutionSection());
        }
    }

    /**
     * Build the Register New User section.
     * Available to all institution admins (VALIDATOR and UNIVERSITY).
     *
     * @return the VBox containing all user-registration controls
     */
    private VBox buildRegisterUserSection() {
        Text title = new Text("Register New User");
        title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        Label subtitle = new Label(
                "Staff accounts can issue credentials. Student accounts are read-only.");
        subtitle.setTextFill(Color.DARKGRAY);
        subtitle.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(12, 0, 8, 0));

        userIdField = new TextField();
        userIdField.setPromptText("9-digit Israeli ID");
        userIdField.setPrefWidth(160);

        userNameField = new TextField();
        userNameField.setPromptText("Full name");
        userNameField.setPrefWidth(200);

        userRoleBox = new ComboBox<>();
        userRoleBox.getItems().addAll(
                "Staff (can issue credentials)",
                "Student (read-only)");
        userRoleBox.setValue("Staff (can issue credentials)");
        userRoleBox.setPrefWidth(220);

        userPortField = new TextField(String.valueOf(adminService.getAdminConfig().getPort()));
        userPortField.setPrefWidth(80);

        userDataDirField = new TextField("./data/user");
        userDataDirField.setPrefWidth(200);

        form.add(new Label("Israeli ID:"), 0, 0);
        form.add(userIdField, 1, 0);
        form.add(new Label("Display Name:"), 2, 0);
        form.add(userNameField, 3, 0);
        form.add(new Label("Role:"), 0, 1);
        form.add(userRoleBox, 1, 1, 3, 1);
        form.add(new Label("Port:"), 0, 2);
        form.add(userPortField, 1, 2);
        form.add(new Label("Data Dir:"), 2, 2);
        form.add(userDataDirField, 3, 2);

        Button registerBtn = new Button("Generate Password & Register");
        registerBtn.setOnAction(e -> onRegisterUser());

        userResultLabel = new Label();
        userResultLabel.setWrapText(true);
        userResultLabel.setVisible(false);
        userResultLabel.setManaged(false);

        userPasswordDisplay = new TextField();
        userPasswordDisplay.setEditable(false);
        userPasswordDisplay.setPromptText("Generated password appears here");
        userPasswordDisplay.setPrefWidth(220);
        userPasswordDisplay.setVisible(false);
        userPasswordDisplay.setManaged(false);
        userPasswordDisplay.setTooltip(new Tooltip("Copy this password and hand it to the user — it will not be shown again."));

        Label pwLabel = new Label("Generated password (shown once):");
        pwLabel.setVisible(false);
        pwLabel.setManaged(false);

        Button copyBtn = new Button("Copy");
        copyBtn.setVisible(false);
        copyBtn.setManaged(false);
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(userPasswordDisplay.getText());
            cb.setContent(cc);
        });

        // Wire visibility and managed together
        userPasswordDisplay.visibleProperty().addListener((obs, o, n) -> {
            pwLabel.setVisible(n);
            pwLabel.setManaged(n);
            copyBtn.setVisible(n);
            copyBtn.setManaged(n);
        });
        userPasswordDisplay.managedProperty().bind(userPasswordDisplay.visibleProperty());

        HBox pwRow = new HBox(8, userPasswordDisplay, copyBtn);
        pwRow.setAlignment(Pos.CENTER_LEFT);
        pwRow.visibleProperty().bind(userPasswordDisplay.visibleProperty());
        pwRow.managedProperty().bind(userPasswordDisplay.visibleProperty());

        return new VBox(6, title, subtitle, form,
                new HBox(registerBtn), userResultLabel, pwLabel, pwRow);
    }

    /**
     * Build the Register New Institution section.
     * Visible only when the logged-in node is a VALIDATOR.
     *
     * @return the VBox containing all institution-registration controls
     */
    private VBox buildRegisterInstitutionSection() {
        Text title = new Text("Register New Institution");
        title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        Label subtitle = new Label(
                "Create a UNIVERSITY account for a new accredited institution. "
                + "The institution will be able to issue credentials but cannot sign blocks.");
        subtitle.setTextFill(Color.DARKGRAY);
        subtitle.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(12, 0, 8, 0));

        instNameField = new TextField();
        instNameField.setPromptText("e.g. Technion");
        instNameField.setPrefWidth(200);

        instAdminIdField = new TextField();
        instAdminIdField.setPromptText("9-digit Israeli ID");
        instAdminIdField.setPrefWidth(160);

        instAdminNameField = new TextField();
        instAdminNameField.setPromptText("Admin display name");
        instAdminNameField.setPrefWidth(200);

        instPortField = new TextField("8081");
        instPortField.setPrefWidth(80);

        instDataDirField = new TextField("./data/institution");
        instDataDirField.setPrefWidth(200);

        form.add(new Label("Institution Name:"), 0, 0);
        form.add(instNameField, 1, 0, 3, 1);
        form.add(new Label("Admin Israeli ID:"), 0, 1);
        form.add(instAdminIdField, 1, 1);
        form.add(new Label("Admin Name:"), 2, 1);
        form.add(instAdminNameField, 3, 1);
        form.add(new Label("Port:"), 0, 2);
        form.add(instPortField, 1, 2);
        form.add(new Label("Data Dir:"), 2, 2);
        form.add(instDataDirField, 3, 2);

        Button registerBtn = new Button("Generate Password & Register Institution");
        registerBtn.setOnAction(e -> onRegisterInstitution());

        instResultLabel = new Label();
        instResultLabel.setWrapText(true);
        instResultLabel.setVisible(false);
        instResultLabel.setManaged(false);

        instPasswordDisplay = new TextField();
        instPasswordDisplay.setEditable(false);
        instPasswordDisplay.setPromptText("Generated password appears here");
        instPasswordDisplay.setPrefWidth(220);
        instPasswordDisplay.setVisible(false);
        instPasswordDisplay.setManaged(false);
        instPasswordDisplay.setTooltip(new Tooltip("Copy this password and give it to the institution admin — it will not be shown again."));

        Label pwLabel = new Label("Generated password (shown once):");
        pwLabel.setVisible(false);
        pwLabel.setManaged(false);

        Button copyBtn = new Button("Copy");
        copyBtn.setVisible(false);
        copyBtn.setManaged(false);
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(instPasswordDisplay.getText());
            cb.setContent(cc);
        });

        instPasswordDisplay.visibleProperty().addListener((obs, o, n) -> {
            pwLabel.setVisible(n);
            pwLabel.setManaged(n);
            copyBtn.setVisible(n);
            copyBtn.setManaged(n);
        });
        instPasswordDisplay.managedProperty().bind(instPasswordDisplay.visibleProperty());

        HBox pwRow = new HBox(8, instPasswordDisplay, copyBtn);
        pwRow.setAlignment(Pos.CENTER_LEFT);
        pwRow.visibleProperty().bind(instPasswordDisplay.visibleProperty());
        pwRow.managedProperty().bind(instPasswordDisplay.visibleProperty());

        return new VBox(6, title, subtitle, form,
                new HBox(registerBtn), instResultLabel, pwLabel, pwRow);
    }

    /**
     * Build a visual divider with padding between the two sections.
     *
     * @return a VBox containing a Separator with vertical padding
     */
    private VBox buildSectionDivider() {
        Separator sep = new Separator();
        VBox box = new VBox(sep);
        box.setPadding(new Insets(16, 0, 16, 0));
        return box;
    }

    /**
     * Handler for the Register User button.
     * Validates the Israeli ID with CryptoUtils.isValidid, generates a
     * password, and calls AdminService.registerStaff or registerStudent depending
     * on the selected role. Shows the plain password once on success.
     */
    private void onRegisterUser() {
        String id = userIdField.getText().trim();
        String displayName = userNameField.getText().trim();
        String role = userRoleBox.getValue();

        if (!CryptoUtils.isValidIsraeliId(id)) {
            showUserResult("Invalid Israeli ID. Must be exactly 9 digits and pass the checksum.", true);
            return;
        }
        if (displayName.isEmpty()) {
            showUserResult("Display name is required.", true);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(userPortField.getText().trim());
        } catch (NumberFormatException e) {
            showUserResult("Port must be a valid number.", true);
            return;
        }

        String dataDir = userDataDirField.getText().trim();
        if (dataDir.isEmpty()) {
            showUserResult("Data directory is required.", true);
            return;
        }

        String plainPw = adminService.generatePassword();

        new Thread(() -> {
            try {
                boolean isStaff = role.startsWith("Staff");
                if (isStaff) {
                    adminService.registerStaff(id, plainPw, displayName, port, dataDir);
                } else {
                    adminService.registerStudent(id, plainPw, displayName, port, dataDir);
                }
                Platform.runLater(() -> {
                    showUserResult("User registered successfully.", false);
                    userPasswordDisplay.setText(plainPw);
                    userPasswordDisplay.setVisible(true);
                    clearUserForm();
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        showUserResult("Registration failed: " + ex.getMessage(), true));
            }
        }, "admin-register-user").start();
    }

    /**
     * Handler for the Register Institution button (VALIDATOR only).
     * Validates inputs, generates a password, and calls AdminService.registerInstitution.
     * Shows the plain password once on success.
     */
    private void onRegisterInstitution() {
        String id = instAdminIdField.getText().trim();
        String institutionName = instNameField.getText().trim();
        String adminName = instAdminNameField.getText().trim();

        if (!CryptoUtils.isValidIsraeliId(id)) {
            showInstResult("Invalid admin Israeli ID. Must be exactly 9 digits and pass the checksum.", true);
            return;
        }
        if (institutionName.isEmpty()) {
            showInstResult("Institution name is required.", true);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(instPortField.getText().trim());
        } catch (NumberFormatException e) {
            showInstResult("Port must be a valid number.", true);
            return;
        }

        String dataDir = instDataDirField.getText().trim();
        if (dataDir.isEmpty()) {
            showInstResult("Data directory is required.", true);
            return;
        }

        String plainPw = adminService.generatePassword();

        new Thread(() -> {
            try {
                adminService.registerInstitution(id, plainPw,
                        institutionName, adminName.isEmpty() ? institutionName + " Admin" : adminName,
                        port, dataDir);
                Platform.runLater(() -> {
                    showInstResult("Institution registered successfully.", false);
                    instPasswordDisplay.setText(plainPw);
                    instPasswordDisplay.setVisible(true);
                    clearInstForm();
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        showInstResult("Registration failed: " + ex.getMessage(), true));
            }
        }, "admin-register-inst").start();
    }

    /** Show a result message for the user-registration section. */
    private void showUserResult(String message, boolean isError) {
        userResultLabel.setText(message);
        userResultLabel.setTextFill(isError ? Color.RED : Color.GREEN);
        userResultLabel.setVisible(true);
        userResultLabel.setManaged(true);
    }

    /** Show a result message for the institution-registration section. */
    private void showInstResult(String message, boolean isError) {
        instResultLabel.setText(message);
        instResultLabel.setTextFill(isError ? Color.RED : Color.GREEN);
        instResultLabel.setVisible(true);
        instResultLabel.setManaged(true);
    }

    /** Clear all fields in the user-registration form. */
    private void clearUserForm() {
        userIdField.clear();
        userNameField.clear();
        userRoleBox.setValue("Staff (can issue credentials)");
        userResultLabel.setVisible(false);
        userResultLabel.setManaged(false);
    }

    /** Clear all fields in the institution-registration form. */
    private void clearInstForm() {
        instNameField.clear();
        instAdminIdField.clear();
        instAdminNameField.clear();
        instResultLabel.setVisible(false);
        instResultLabel.setManaged(false);
    }
}
