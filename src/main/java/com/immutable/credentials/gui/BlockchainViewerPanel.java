package com.immutable.credentials.gui;

import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;
import com.immutable.credentials.service.BlockchainService;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * JavaFX panel that provides a read-only explorer for the entire blockchain.
 * All data is fetched through {@link BlockchainService} so the panel remains
 * fully decoupled from the backend {@code Node}.
 *
 * The panel consists of a toolbar at the top containing a Refresh button and
 * chain statistics labels, and a {@link SplitPane} below it. The left side of
 * the split shows a {@link TableView} of all blocks (index, truncated hash,
 * timestamp, validator ID, credential count). The right side shows a detail
 * pane with the full header fields and a scrollable text area listing the
 * credentials of the selected block.
 *
 * Call {@link #onRefresh()} at any time to reload the chain from the service
 * and repopulate every section.
 */
public class BlockchainViewerPanel extends VBox {

    // ===== Service =====
    private final BlockchainService blockchainService;

    // ===== Toolbar Controls =====
    private Button refreshButton;
    private Label totalBlocksLabel;
    private Label lastBlockTimeLabel;
    private Label chainValidLabel;

    // ===== Block Table =====
    private TableView<Block> blockTable;
    private TableColumn<Block, Integer> indexColumn;
    private TableColumn<Block, String> hashColumn;
    private TableColumn<Block, String> timestampColumn;
    private TableColumn<Block, String> validatorColumn;
    private TableColumn<Block, Integer> txCountColumn;

    // ===== Detail Pane =====
    private VBox detailPane;
    private GridPane headerGrid;
    private TextArea credentialTextArea;

    /**
     * Construct the panel, validate the service dependency, build all child
     * sections, and perform an initial data load.
     *
     * @param blockchainService middleware service for all blockchain queries;
     *                          must not be {@code null}
     * @throws IllegalArgumentException if {@code blockchainService} is {@code null}
     */
    public BlockchainViewerPanel(BlockchainService blockchainService) {
        if (blockchainService == null)
            throw new IllegalArgumentException("blockchainService must not be null.");
        this.blockchainService = blockchainService;

        setSpacing(8);
        setPadding(new Insets(16));

        Text heading = new Text("Blockchain Explorer");
        heading.setFont(Font.font("System", FontWeight.BOLD, 18));

        getChildren().addAll(heading, buildToolbar(), buildSplitPane());
        VBox.setVgrow(getChildren().get(2), Priority.ALWAYS);

        onRefresh();
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Build the toolbar containing a Refresh button wired to {@link #onRefresh()}
     * and three read-only statistics labels showing total block count, timestamp of
     * the last block, and whether the chain hash-links are currently valid.
     *
     * @return a configured {@link ToolBar}
     */
    private ToolBar buildToolbar() {
        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> onRefresh());

        totalBlocksLabel = new Label("Blocks: 0");
        lastBlockTimeLabel = new Label("Last: –");
        chainValidLabel = new Label("Chain: –");

        return new ToolBar(refreshButton,
                new Label("  "), totalBlocksLabel,
                new Label(" | "), lastBlockTimeLabel,
                new Label(" | "), chainValidLabel);
    }

    /**
     * Build the block {@link TableView} with five columns: index, hash (truncated
     * to the first 16 characters followed by "…"), timestamp formatted as
     * {@code yyyy-MM-dd HH:mm:ss}, validator ID, and credential count.
     * Selecting a row automatically calls {@link #onBlockSelected(Block)}.
     *
     * @return the configured {@link TableView}
     */
    private TableView<Block> buildBlockTable() {
        blockTable = new TableView<>();
        blockTable.setPlaceholder(new Label("No blocks on chain."));

        indexColumn = new TableColumn<>("#");
        indexColumn.setPrefWidth(50);
        indexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getIndex()).asObject());

        hashColumn = new TableColumn<>("Hash");
        hashColumn.setPrefWidth(140);
        hashColumn.setCellValueFactory(data -> {
            String h = data.getValue().getHash();
            return new SimpleStringProperty(h.length() > 16 ? h.substring(0, 16) + "…" : h);
        });

        timestampColumn = new TableColumn<>("Timestamp");
        timestampColumn.setPrefWidth(150);
        timestampColumn
                .setCellValueFactory(data -> new SimpleStringProperty(formatTimestamp(data.getValue().getTimestamp())));

        validatorColumn = new TableColumn<>("Validator");
        validatorColumn.setPrefWidth(120);
        validatorColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValidatorId()));

        txCountColumn = new TableColumn<>("Credentials");
        txCountColumn.setPrefWidth(80);
        txCountColumn.setCellValueFactory(data -> {
            List<Credential> creds = data.getValue().getCredentials();
            return new SimpleIntegerProperty(creds != null ? creds.size() : 0).asObject();
        });

        blockTable.getColumns().add(indexColumn);
        blockTable.getColumns().add(hashColumn);
        blockTable.getColumns().add(timestampColumn);
        blockTable.getColumns().add(validatorColumn);
        blockTable.getColumns().add(txCountColumn);

        blockTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null)
                        onBlockSelected(newVal);
                    else
                        clearDetailPane();
                });

        return blockTable;
    }

    /**
     * Build the right-hand detail pane containing a {@link GridPane} for block
     * header fields (index, full hash, previous hash, timestamp, validator ID,
     * credential count) and a non-editable {@link TextArea} below it that lists
     * every credential in the selected block.
     * The pane starts in its cleared placeholder state.
     *
     * @return a {@link VBox} acting as the detail pane
     */
    private VBox buildDetailPane() {
        Text title = new Text("Block Details");
        title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        headerGrid = new GridPane();
        headerGrid.setHgap(12);
        headerGrid.setVgap(6);
        headerGrid.setPadding(new Insets(8, 8, 4, 8));

        Label credHeading = new Label("Credentials:");
        credHeading.setFont(Font.font("System", FontWeight.BOLD, 12));
        credHeading.setPadding(new Insets(4, 8, 0, 8));

        credentialTextArea = new TextArea("Select a block to view details.");
        credentialTextArea.setEditable(false);
        credentialTextArea.setWrapText(true);
        credentialTextArea.setPadding(new Insets(4));
        VBox.setVgrow(credentialTextArea, Priority.ALWAYS);

        detailPane = new VBox(6, title, headerGrid, credHeading, credentialTextArea);
        detailPane.setPadding(new Insets(8));
        return detailPane;
    }

    /**
     * Assemble a {@link SplitPane} placing the block table on the left at 40%
     * of the available width and the detail pane on the right at 60%.
     * The split pane grows vertically to fill all remaining panel space.
     *
     * @return the configured {@link SplitPane}
     */
    private SplitPane buildSplitPane() {
        buildBlockTable();
        buildDetailPane();

        SplitPane splitPane = new SplitPane(blockTable, detailPane);
        splitPane.setDividerPositions(0.4);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        return splitPane;
    }

    // -------------------------------------------------------------------------
    // Refresh logic
    // -------------------------------------------------------------------------

    /**
     * Reload all blocks from {@link BlockchainService#getAllBlocks()}, repopulate
     * the table via {@link #populateTable(List)}, refresh the statistics labels via
     * {@link #refreshStats()}, and clear the detail pane.
     * Called by the Refresh button or programmatically after a new block is
     * finalised.
     */
    public void onRefresh() {
        List<Block> blocks = blockchainService.getAllBlocks();
        populateTable(blocks);
        refreshStats();
        clearDetailPane();
    }

    /**
     * Populate the detail pane with the header fields and credentials of the
     * selected block. Rebuilds the {@link #headerGrid} rows and sets the
     * {@link #credentialTextArea} text via {@link #formatCredentials(List)}.
     *
     * @param block the block selected in the table; must not be {@code null}
     */
    private void onBlockSelected(Block block) {
        headerGrid.getChildren().clear();
        headerGrid.getRowConstraints().clear();

        int row = 0;
        addDetailRow(row++, "Index:", String.valueOf(block.getIndex()));
        addDetailRow(row++, "Hash:", block.getHash());
        addDetailRow(row++, "Previous Hash:", block.getPreviousHash());
        addDetailRow(row++, "Timestamp:", formatTimestamp(block.getTimestamp()));
        addDetailRow(row++, "Validator:", block.getValidatorId());
        List<Credential> creds = block.getCredentials();
        addDetailRow(row, "Credentials:", String.valueOf(creds != null ? creds.size() : 0));

        credentialTextArea.setText(formatCredentials(creds));
    }

    /**
     * Replace the contents of the block {@link TableView} with the given list.
     * Passing {@code null} or an empty list clears the table and shows its
     * placeholder label.
     *
     * @param blocks the list of blocks to display; may be {@code null} or empty
     */
    private void populateTable(List<Block> blocks) {
        blockTable.getItems().clear();
        if (blocks != null && !blocks.isEmpty()) {
            blockTable.getItems().addAll(blocks);
        }
    }

    /**
     * Refresh the three toolbar statistics labels using data from
     * {@link BlockchainService}: total block count, formatted timestamp of the
     * latest block (or "–" when the chain is empty), and a green "Valid" or red
     * "INVALID" chain-integrity indicator.
     */
    private void refreshStats() {
        int height = blockchainService.getChainHeight();
        totalBlocksLabel.setText("Blocks: " + height);

        long lastTs = blockchainService.getLastBlockTimestamp();
        lastBlockTimeLabel.setText(lastTs < 0 ? "Last: –" : "Last: " + formatTimestamp(lastTs));

        boolean valid = blockchainService.isChainValid();
        chainValidLabel.setText("Chain: " + (valid ? "Valid" : "INVALID"));
        chainValidLabel.setTextFill(valid ? Color.GREEN : Color.RED);

    }

    /**
     * Reset the detail pane to its initial placeholder state by clearing the
     * {@link #headerGrid} rows and replacing the {@link #credentialTextArea}
     * text with "Select a block to view details."
     */
    private void clearDetailPane() {
        headerGrid.getChildren().clear();
        headerGrid.getRowConstraints().clear();
        credentialTextArea.setText("Select a block to view details.");
    }

    /**
     * Format the credentials of a block as a numbered, human-readable multi-line
     * string for display in the {@link #credentialTextArea}.
     * Each entry shows the student name, degree, institution, credential ID,
     * student ID, and date awarded.
     * Returns "(no credentials)" if the list is {@code null} or empty.
     *
     * @param credentials the credential list belonging to the selected block;
     *                    may be {@code null} or empty
     * @return a non-null formatted string; never empty
     */
    private String formatCredentials(List<Credential> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return "(no credentials)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Credential c : credentials) {
            sb.append("[").append(i++).append("] ")
                    .append(c.getStudentName()).append(" — ")
                    .append(c.getDegree()).append(", ")
                    .append(c.getInstitution()).append("\n");
            sb.append("    Credential ID : ").append(c.getCredentialId()).append("\n");
            sb.append("    Student ID    : ").append(c.getStudentId()).append("\n");
            if (c.getDateAwarded() != null) {
                sb.append("    Awarded       : ").append(c.getDateAwarded()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Add a single label–value row to {@link #headerGrid} at the given row index.
     * The label is rendered bold; the value is a plain {@link Label} that wraps.
     *
     * @param row   the zero-based row index in the grid
     * @param label the field name to display on the left column
     * @param value the field value to display on the right column
     */
    private void addDetailRow(int row, String label, String value) {
        Label key = new Label(label);
        key.setFont(Font.font("System", FontWeight.BOLD, 12));
        Label val = new Label(value != null ? value : "–");
        val.setWrapText(true);
        headerGrid.add(key, 0, row);
        headerGrid.add(val, 1, row);
    }

    /**
     * Format a Unix epoch millisecond timestamp as a human-readable local
     * date-time string in the pattern {@code yyyy-MM-dd HH:mm:ss}.
     *
     * @param epochMillis the timestamp in milliseconds since the Unix epoch
     * @return a formatted date-time string in the system default time zone
     */
    private String formatTimestamp(long epochMillis) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(epochMillis));
    }
}
