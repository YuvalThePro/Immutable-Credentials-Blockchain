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
 *
 * <p>
 * The panel never queries the backend directly; all lookups are performed
 * through {@link CredentialService}, which acts as the middleware layer.
 * </p>
 *
 * <p>
 * UI layout:
 * </p>
 * 
 * <pre>
 *  ┌──────────────────────────────────────────────┐
 *  │  Search type: [Student ID ▾]  [__________]   │
 *  │                                [ Search ]    │
 *  ├──────────────────────────────────────────────┤
 *  │  Results area (scrollable)                   │
 *  │  ┌────────────────────────────────────────┐  │
 *  │  │  Credential card(s) or "Not Found"     │  │
 *  │  └────────────────────────────────────────┘  │
 *  └──────────────────────────────────────────────┘
 * </pre>
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

        statusLabel = new Label("Enter a Student ID or Credential ID to search.");
        statusLabel.setStyle("-fx-text-fill: #555555;");

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
     *
     * <p>
     * Search type options in the {@link ComboBox}:
     * </p>
     * <ul>
     * <li><b>Student ID</b> – delegates to
     * {@link CredentialService#searchByStudentId(String)}</li>
     * <li><b>Credential ID</b> – delegates to
     * {@link CredentialService#getCredentialById(String)}</li>
     * </ul>
     *
     * @return an {@link HBox} containing all search bar controls
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
     * @return a {@link ScrollPane} wrapping the results {@link VBox}
     */
    private ScrollPane buildResultsArea() {
        resultsContainer = new VBox(10);
        resultsContainer.setPadding(new Insets(10));

        resultsScrollPane = new ScrollPane(resultsContainer);
        resultsScrollPane.setFitToWidth(true);
        resultsScrollPane.setStyle("-fx-background-color: transparent;");
        return resultsScrollPane;
    }

    /**
     * Handler invoked when the user clicks <em>Search</em> or presses Enter
     * in the search field.
     *
     * <p>
     * Execution flow:
     * </p>
     * <ol>
     * <li>Read the selected search type and query string.</li>
     * <li>Validate that the query is not blank.</li>
     * <li>Delegate to the appropriate {@link CredentialService} method.</li>
     * <li>Render results via {@link #displayCredentials(List)} or
     * {@link #showNotFound()} if the result is empty.</li>
     * </ol>
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
     * Render a list of found credentials as individual visual cards inside
     * the results container.
     *
     * <p>
     * Each card displays: student name, student ID, credential ID,
     * degree, institution, date awarded, and a coloured
     * <em>"Verified on Blockchain"</em> badge.
     * </p>
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
     * @return a {@link GridPane} formatted as a self-contained credential card
     */
    private GridPane buildCredentialCard(Credential credential) {
        GridPane card = new GridPane();
        card.setHgap(15);
        card.setVgap(6);
        card.setPadding(new Insets(12));
        card.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-border-color: #cccccc;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;");

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
        badge.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-background-color: #2e7d32;" +
                        "-fx-background-radius: 4;" +
                        "-fx-padding: 3 8 3 8;");
        card.add(badge, 1, row);

        return card;
    }

    /** Small helper to create a right-aligned bold label. */
    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    /**
     * Clear the results container and display a <em>"Not Found"</em> message.
     * Called when the service returns a null or empty result.
     */
    private void showNotFound() {
        resultsContainer.getChildren().clear();
        Label msg = new Label("No credentials found for the given ID.");
        msg.setStyle("-fx-text-fill: #c62828; -fx-font-size: 13;");
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
