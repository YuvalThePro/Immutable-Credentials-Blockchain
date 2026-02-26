package com.immutable.credentials.gui;

import com.immutable.credentials.model.Credential;
import com.immutable.credentials.service.CredentialService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.List;

/**
 * JavaFX panel that allows any node (validator or read-only) to search for
 * and verify academic credentials stored on the blockchain.
 * The panel never queries the backend directly; all lookups are performed
 * through CredentialService, which acts as the middleware layer.
 * The UI contains a search bar with a search-type selector and text field at the top,
 * and a scrollable results area below that displays credential cards or a Not Found message.
 */
public class VerifyCredentialPanel extends VBox {

    // ===== Service =====
    private final CredentialService credentialService;

    // ===== Search Controls =====
    private ComboBox<String> searchTypeCombo;
    private TextField searchField;
    private Button searchButton;
    private Button clearButton;

    // ===== Results Area =====
    private VBox resultsContainer;
    private ScrollPane resultsScrollPane;
    private Label statusLabel;

    /**
     * Construct the panel with its required service dependency.
     *
     * @param credentialService middleware service for all credential lookups;
     *                          must not be {@code null}
     * @throws IllegalArgumentException if {@code credentialService} is {@code null}
     */
    public VerifyCredentialPanel(CredentialService credentialService) {
        if (credentialService == null)
            throw new IllegalArgumentException("CredentialService must not be null");
        this.credentialService = credentialService;

        setSpacing(10);
        setPadding(new Insets(15));
        getStyleClass().add("verify-panel");

        statusLabel = new Label("Enter a Student ID or Credential ID to search.");
        statusLabel.getStyleClass().add("muted-text");

        getChildren().addAll(
                buildSearchBar(),
                new Separator(),
                statusLabel,
                buildResultsArea());
        VBox.setVgrow(resultsScrollPane, Priority.ALWAYS);
    }

    /**
     * Build the top search bar containing the search-type selector,
     * the search text field, and the action buttons.
     * The Student ID option delegates to CredentialService.searchByStudentId.
     * The Credential ID option delegates to CredentialService.getCredentialById.
     * 
     * @return an HBox containing all search bar controls
     */
    private HBox buildSearchBar() {
        ObservableList<String> options = FXCollections.observableArrayList(
                "Student ID", "Credential ID");
        searchTypeCombo = new ComboBox<>(options);
        searchTypeCombo.getSelectionModel().selectFirst();
        searchTypeCombo.setPromptText("Search by…");

        searchField = new TextField();
        searchField.setPromptText("Enter ID");
        searchField.setPrefWidth(250);
        searchField.setOnAction(e -> onSearch());

        searchButton = new Button("Search");
        searchButton.setDefaultButton(true);
        searchButton.setOnAction(e -> onSearch());

        clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearResults());

        HBox hbox = new HBox(10, searchTypeCombo, searchField, searchButton, clearButton);
        hbox.setPadding(new Insets(10, 0, 10, 0));
        hbox.setAlignment(Pos.CENTER_LEFT);
        return hbox;
    }

    /**
     * Build the scrollable results area where found credential cards are rendered.
     * 
     * @return a ScrollPane wrapping the results VBox
     */
    private ScrollPane buildResultsArea() {
        resultsContainer = new VBox(10);
        resultsContainer.setPadding(new Insets(10));

        resultsScrollPane = new ScrollPane(resultsContainer);
        resultsScrollPane.setFitToWidth(true);
        resultsScrollPane.getStyleClass().add("transparent-scroll");
        return resultsScrollPane;
    }

    /**
     * Handler invoked when the user clicks Search or presses Enter in the search field.
     * Reads the selected search type and query string, validates that the query is not blank,
     * delegates to the appropriate CredentialService method, and renders results
     * or shows a Not Found message if the result is empty.
     */
    private void onSearch() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (query.isEmpty()) {
            updateStatus("Please enter an ID to search.");
            return;
        }

        String searchType = searchTypeCombo.getValue();
        if (searchType == null) {
            updateStatus("Please select a search type.");
            return;
        }

        try {
            List<Credential> results;
            if ("Credential ID".equals(searchType)) {
                Credential c = credentialService.getCredentialById(query);
                results = (c != null) ? Collections.singletonList(c) : Collections.emptyList();
            } else {
                // Student ID
                results = credentialService.searchByStudentId(query);
            }

            if (results == null || results.isEmpty()) {
                showNotFound();
            } else {
                displayCredentials(results);
                updateStatus("Found " + results.size() + " result(s).");
            }
        } catch (Exception e) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Search Error");
            alert.setHeaderText(null);
            alert.setContentText("Search failed: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Render a list of found credentials as individual visual cards inside the results container.
     * Each card displays the student name, student ID, credential ID, degree, institution,
     * date awarded, and a coloured Verified on Blockchain badge.
     * 
     * @param credentials the non-null, non-empty list of credentials to display
     */
    private void displayCredentials(List<Credential> credentials) {
        resultsContainer.getChildren().clear();
        for (Credential c : credentials) {
            resultsContainer.getChildren().add(buildCredentialCard(c));
        }
    }

    /**
     * Build and return a single credential card node for the given credential.
     * 
     * @param credential the credential whose details should be rendered
     * @return a GridPane formatted as a self-contained credential card
     */
    private GridPane buildCredentialCard(Credential credential) {
        GridPane card = new GridPane();
        card.setHgap(15);
        card.setVgap(6);
        card.setPadding(new Insets(12));
        card.getStyleClass().add("credential-card");

        int row = 0;
        card.add(boldLabel("Student Name:"), 0, row);
        card.add(new Label(credential.getStudentName()), 1, row++);
        card.add(boldLabel("Student ID:"), 0, row);
        card.add(new Label(credential.getStudentId()), 1, row++);
        card.add(boldLabel("Credential ID:"), 0, row);
        card.add(new Label(credential.getCredentialId()), 1, row++);
        card.add(boldLabel("Degree:"), 0, row);
        card.add(new Label(credential.getDegree()), 1, row++);
        card.add(boldLabel("Institution:"), 0, row);
        card.add(new Label(credential.getInstitution()), 1, row++);
        card.add(boldLabel("Date Awarded:"), 0, row);
        card.add(new Label(
                credential.getDateAwarded() != null ? credential.getDateAwarded().toString() : "N/A"), 1, row++);

        Label badge = new Label("✓  Verified on Blockchain");
        badge.getStyleClass().add("verified-badge");
        card.add(badge, 1, row);

        return card;
    }

    /** Small helper to create a right-aligned bold label. */
    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    /**
     * Clear the results container and display a <em>"Not Found"</em> message.
     * Called when the service returns a null or empty result.
     */
    private void showNotFound() {
        resultsContainer.getChildren().clear();
        Label msg = new Label("No credentials found for the given ID.");
        msg.getStyleClass().add("error-text");
        resultsContainer.getChildren().add(msg);
        updateStatus("No results found.");
    }

    /**
     * Clear all search results, reset the search-type combo to its default,
     * and empty the text field.
     */
    private void clearResults() {
        searchField.clear();
        searchTypeCombo.getSelectionModel().selectFirst();
        resultsContainer.getChildren().clear();
        updateStatus("Enter a Student ID or Credential ID to search.");
    }

    /**
     * Update the status label with a short informational message.
     *
     * @param message the message to display (e.g. "Found 3 result(s)")
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
}
