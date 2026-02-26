package com.immutable.credentials.gui;

import java.time.LocalDate;
import java.util.UUID;

import com.immutable.credentials.service.CredentialService;
import com.immutable.credentials.service.NodeService;

import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.geometry.Insets;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * JavaFX panel that provides a form for issuing new academic credentials onto
 * the blockchain.
 * This panel is intended for university and validator nodes only and is
 * disabled entirely
 * for read-only nodes such as student portals, employer verifiers, and public
 * explorers.
 * Any accredited institution, whether a plain university node or a
 * consensus-validator node,
 * may submit credentials. Validator nodes can both issue credentials and
 * seal/approve blocks
 * via Proof-of-Authority consensus. University nodes may submit credentials;
 * the credential
 * is pooled and finalised once a quorum of validators approves the containing
 * block.
 * NodeService.isUniversity() is used to enable or disable the form.
 * Form fields include Student Name, Student ID, Degree/Certification,
 * Institution, and Date Awarded.
 * On submission the panel delegates entirely to
 * CredentialService.issueCredential
 * and never calls the backend Node directly.
 */
public class IssueCredentialPanel extends VBox {

    // ===== Services =====
    private final CredentialService credentialService;
    private final NodeService nodeService;

    // ===== Form Fields =====
    private TextField studentNameField;
    private TextField studentIdField;
    private TextField degreeField;
    private TextField institutionField;
    private DatePicker dateAwardedPicker;

    // ===== Action Controls =====
    private Button issueButton;
    private Button clearButton;

    // ===== Feedback =====
    private Label feedbackLabel;

    /**
     * Construct the panel with the required service dependencies.
     *
     * @param credentialService middleware service for all credential operations;
     *                          must not be {@code null}
     * @param nodeService       middleware service for querying node identity;
     *                          must not be {@code null}
     * @throws IllegalArgumentException if either service is {@code null}
     */
    public IssueCredentialPanel(CredentialService credentialService, NodeService nodeService) {
        this.credentialService = credentialService;
        this.nodeService = nodeService;
        getChildren().add(buildForm());
    }

    /**
     * Build and layout the credential-entry form using a {@link GridPane}.
     * Adds a label–field pair for each credential attribute and appends the
     * button row and feedback label below the grid.
     *
     * @return the assembled form grid ready to be added to this panel
     */
    private GridPane buildForm() {
        studentNameField = new TextField();
        studentNameField.setPromptText("Enter Student Name");
        studentNameField.setFocusTraversable(false);
        studentIdField = new TextField();
        studentIdField.setPromptText("Enter Student Id");
        studentIdField.setFocusTraversable(false);
        degreeField = new TextField();
        degreeField.setPromptText("Enter degree");
        degreeField.setFocusTraversable(false);
        institutionField = new TextField();
        institutionField.setPromptText("Enter institution");
        institutionField.setFocusTraversable(false);
        dateAwardedPicker = new DatePicker();
        dateAwardedPicker.setPromptText("Enter date awarded");
        dateAwardedPicker.setFocusTraversable(false);

        feedbackLabel = new Label();

        HBox buttonRow = buildButtonRow();

        GridPane gridPane = new GridPane();
        gridPane.setHgap(12);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(16));

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(140);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(labelCol, fieldCol);

        gridPane.add(new Label("Student Name:"), 0, 0);
        gridPane.add(studentNameField, 1, 0);

        gridPane.add(new Label("Student ID:"), 0, 1);
        gridPane.add(studentIdField, 1, 1);

        gridPane.add(new Label("Degree / Certification:"), 0, 2);
        gridPane.add(degreeField, 1, 2);

        gridPane.add(new Label("Institution:"), 0, 3);
        gridPane.add(institutionField, 1, 3);

        gridPane.add(new Label("Date Awarded:"), 0, 4);
        gridPane.add(dateAwardedPicker, 1, 4);

        gridPane.add(buttonRow, 0, 5, 2, 1);

        gridPane.add(feedbackLabel, 0, 6, 2, 1);

        return gridPane;
    }

    /**
     * Build the action button row containing the <em>Issue Credential</em>
     * button and the <em>Clear</em> button.
     *
     * @return an {@link HBox} containing both buttons with appropriate spacing
     */
    private HBox buildButtonRow() {
        issueButton = new Button("Issue Credential");
        issueButton.setDefaultButton(true);
        issueButton.setOnAction(e -> onIssueCredential());

        clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearForm());

        HBox hbox = new HBox(10, issueButton, clearButton);
        hbox.setPadding(new Insets(8, 0, 4, 0));
        return hbox;
    }

    /**
     * Handler invoked when the user clicks Issue Credential.
     * Reads and validates all form fields, showing inline errors if any are blank.
     * Calls CredentialService.issueCredential with the collected data.
     * Displays a success or failure message and clears the form on success.
     */
    private void onIssueCredential() {
        if (!validateForm()) {
            showFeedback("Please fill in all required fields.", false);
            return;
        }

        String studentName = studentNameField.getText().trim();
        String studentId = studentIdField.getText().trim();
        String degree = degreeField.getText().trim();
        String institution = institutionField.getText().trim();
        LocalDate dateAwarded = dateAwardedPicker.getValue();
        String credentialId = UUID.randomUUID().toString();

        try {
            credentialService.issueCredential(studentName, studentId, degree, institution, dateAwarded, credentialId);
            showFeedback("Credential issued successfully (ID: " + credentialId + ")", true);
            clearForm();
        } catch (Exception ex) {
            showFeedback("Failed to issue credential: " + ex.getMessage(), false);
        }
    }

    /**
     * Clear all form fields and reset the {@link #feedbackLabel} to empty.
     * Typically called after a successful submission or when the user
     * clicks the <em>Clear</em> button.
     */
    private void clearForm() {
        studentNameField.clear();
        studentIdField.clear();
        degreeField.clear();
        institutionField.clear();
        dateAwardedPicker.setValue(null);
        studentNameField.setStyle("");
        studentIdField.setStyle("");
        degreeField.setStyle("");
        institutionField.setStyle("");
        dateAwardedPicker.setStyle("");
        feedbackLabel.setText("");
        feedbackLabel.setStyle("");
    }

    /**
     * Enable or disable all form controls based on whether this node is a
     * university or validator node.
     * Should be set to false for read-only nodes because they are not permitted to
     * issue credentials.
     * Should also be set to false when the node is not yet running, regardless of
     * type.
     * When disabled, a notice must be displayed explaining that only accredited
     * university nodes
     * may issue credentials.
     * 
     * @param enabled true to enable the form when the node is running and is a
     *                university or validator node;
     *                false otherwise
     */
    public void setFormEnabled(boolean enabled) {
        studentNameField.setDisable(!enabled);
        studentIdField.setDisable(!enabled);
        degreeField.setDisable(!enabled);
        institutionField.setDisable(!enabled);
        dateAwardedPicker.setDisable(!enabled);
        issueButton.setDisable(!enabled);
        clearButton.setDisable(!enabled);

        if (!enabled) {
            showFeedback("Only accredited university or validator nodes may issue credentials.", false);
        } else {
            feedbackLabel.setText("");
            feedbackLabel.setStyle("");
        }
    }

    /**
     * Display a feedback message below the form.
     *
     * @param message the text to display (e.g. "Credential issued successfully")
     * @param success {@code true} renders the label in a success style (green);
     *                {@code false} renders it in an error style (red)
     */
    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle(success
                ? "-fx-text-fill: #2e7d32; -fx-font-weight: bold;"
                : "-fx-text-fill: #c62828; -fx-font-weight: bold;");
    }

    /**
     * Validate all form fields before submission.
     * Marks invalid fields with a visual indicator.
     *
     * @return {@code true} if every required field passes validation;
     *         {@code false} if any field is empty or malformed
     */
    private boolean validateForm() {
        boolean valid = true;
        String errorStyle = "-fx-border-color: #c62828; -fx-border-width: 1.5;";

        if (studentNameField.getText().trim().isEmpty()) {
            studentNameField.setStyle(errorStyle);
            valid = false;
        } else {
            studentNameField.setStyle("");
        }

        if (studentIdField.getText().trim().isEmpty()) {
            studentIdField.setStyle(errorStyle);
            valid = false;
        } else {
            studentIdField.setStyle("");
        }

        if (degreeField.getText().trim().isEmpty()) {
            degreeField.setStyle(errorStyle);
            valid = false;
        } else {
            degreeField.setStyle("");
        }

        if (institutionField.getText().trim().isEmpty()) {
            institutionField.setStyle(errorStyle);
            valid = false;
        } else {
            institutionField.setStyle("");
        }

        if (dateAwardedPicker.getValue() == null) {
            dateAwardedPicker.setStyle(errorStyle);
            valid = false;
        } else {
            dateAwardedPicker.setStyle("");
        }

        return valid;
    }
}
