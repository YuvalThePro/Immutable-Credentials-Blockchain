package com.immutable.credentials.gui;

import com.immutable.credentials.network.Peer;
import com.immutable.credentials.service.NetworkService;
import com.immutable.credentials.service.NodeService;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.List;
import java.util.Optional;

/**
 * JavaFX panel that provides a live overview of the node's P2P network status.
 *
 * <p>
 * All data is retrieved through {@link NetworkService} and {@link NodeService};
 * this panel never accesses the backend {@code Node} or {@code P2PNetwork}
 * directly.
 * </p>
 *
 * <p>
 * The panel is divided into three collapsible sections plus a toolbar:
 * </p>
 * <ul>
 * <li><b>Toolbar</b> – <em>Refresh</em> and <em>Connect to Peer</em>
 * actions.</li>
 * <li><b>Node Information</b> – node ID, listening address, node type
 * (Validator / University / Read-Only), validator ID (validators only),
 * and whether the node is currently running.</li>
 * <li><b>Connected Peers</b> – live peer count and a scrollable list of
 * formatted peer descriptors.</li>
 * <li><b>Network Statistics</b> – P2P listener state and sync status.</li>
 * </ul>
 *
 * <p>
 * Call {@link #onRefresh()} at any time to pull fresh data from the services
 * and repopulate every section of the panel.
 * </p>
 */
public class NetworkStatusPanel extends VBox {

    // ===== Services =====
    private final NetworkService networkService;
    private final NodeService nodeService;
    private final String displayNodeId;

    // ===== Toolbar =====
    private Button refreshButton;
    private Button connectButton;

    // ===== Node Info Pane =====
    private Label nodeIdLabel;
    private Label nodeAddressLabel;
    private Label nodeTypeLabel;
    private Label validatorIdLabel;
    private Label nodeRunningLabel;

    // ===== Peers List =====
    private ListView<String> peerListView;
    private Label peerCountLabel;

    // ===== Stats Pane =====
    private Label syncStatusLabel;
    private Label networkRunningLabel;

    /**
     * Construct the panel, validate dependencies, build all child sections,
     * and perform an initial data refresh.
     *
     * @param networkService middleware service for all peer and network queries;
     *                       must not be {@code null}
     * @param nodeService    middleware service for node identity queries;
     *                       must not be {@code null}
     * @param displayNodeId  stable UI node identifier to display
     * @throws IllegalArgumentException if either service is {@code null}
     */
    public NetworkStatusPanel(NetworkService networkService, NodeService nodeService, String displayNodeId) {
        if (networkService == null)
            throw new IllegalArgumentException("networkService must not be null.");
        if (nodeService == null)
            throw new IllegalArgumentException("nodeService must not be null.");
        this.networkService = networkService;
        this.nodeService = nodeService;
        this.displayNodeId = displayNodeId;

        setSpacing(10);
        setPadding(new Insets(16));

        Text heading = new Text("Network Status");
        heading.setFont(Font.font("System", FontWeight.BOLD, 18));

        getChildren().addAll(
                heading,
                buildToolbar(),
                new Separator(),
                buildNodeInfoPane(),
                buildPeerListSection(),
                buildStatsPane());

        onRefresh();
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Build the toolbar containing a Refresh button wired to {@link #onRefresh()}
     * and a Connect to Peer button that opens the peer-connection dialog via
     * {@link #onConnectToPeer()}.
     *
     * @return a fully configured {@link ToolBar}
     */
    private ToolBar buildToolbar() {
        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> onRefresh());

        connectButton = new Button("Connect to Peer…");
        connectButton.setOnAction(e -> onConnectToPeer());

        return new ToolBar(refreshButton, connectButton);
    }

    /**
     * Build the Node Information section as a collapsible {@link TitledPane}
     * wrapping a {@link GridPane} of label–value rows.
     * The rows show the node ID, address in host:port format, node type
     * (Validator / University / Read-Only), validator ID when
     * {@link NodeService#isValidator()} is {@code true} or "N/A" otherwise,
     * and the running state as "Yes" or "No".
     *
     * @return a {@link TitledPane} labelled "Node Information"
     */
    private TitledPane buildNodeInfoPane() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));

        int row = 0;

        grid.add(bold("Node ID:"), 0, row);
        nodeIdLabel = new Label();
        grid.add(nodeIdLabel, 1, row++);

        grid.add(bold("Address:"), 0, row);
        nodeAddressLabel = new Label();
        grid.add(nodeAddressLabel, 1, row++);

        grid.add(bold("Node Type:"), 0, row);
        nodeTypeLabel = new Label();
        grid.add(nodeTypeLabel, 1, row++);

        grid.add(bold("Validator ID:"), 0, row);
        validatorIdLabel = new Label();
        grid.add(validatorIdLabel, 1, row++);

        grid.add(bold("Running:"), 0, row);
        nodeRunningLabel = new Label();
        grid.add(nodeRunningLabel, 1, row);

        TitledPane pane = new TitledPane("Node Information", grid);
        pane.setCollapsible(true);
        return pane;
    }

    /**
     * Build the Connected Peers section containing a heading, a peer-count
     * label, and a scrollable {@link ListView} of formatted peer descriptors.
     * Each list entry is formatted by {@link #formatPeer(Peer)} as
     * {@code "<nodeId>  <address>:<port>  [Validator]"} where the
     * {@code [Validator]} suffix is only appended for validator peers.
     *
     * @return a {@link VBox} containing the section heading, count label, and list
     *         view
     */
    private VBox buildPeerListSection() {
        Text title = new Text("Connected Peers");
        title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        peerCountLabel = new Label("Peers: 0");

        peerListView = new ListView<>();
        peerListView.setPrefHeight(150);
        VBox.setVgrow(peerListView, Priority.ALWAYS);

        VBox section = new VBox(6, title, peerCountLabel, peerListView);
        section.setPadding(new Insets(4, 0, 4, 0));
        return section;
    }

    /**
     * Build the <em>Network Statistics</em> section as a collapsible
     * {@link TitledPane} showing whether the P2P listener is active and the
     * current synchronisation status string returned by
     * {@link NetworkService#getSyncStatusDescription()}.
     *
     * @return a {@link TitledPane} labelled "Network Statistics"
     */
    private TitledPane buildStatsPane() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));

        grid.add(bold("Network Listener:"), 0, 0);
        networkRunningLabel = new Label();
        grid.add(networkRunningLabel, 1, 0);

        grid.add(bold("Sync Status:"), 0, 1);
        syncStatusLabel = new Label();
        grid.add(syncStatusLabel, 1, 1);

        TitledPane pane = new TitledPane("Network Statistics", grid);
        pane.setCollapsible(true);
        return pane;
    }

    // -------------------------------------------------------------------------
    // Refresh logic
    // -------------------------------------------------------------------------

    /**
     * Reload all dynamic values from the service layer and repopulate every
     * section of the panel.
     * Called by the Refresh button and may also be invoked programmatically
     * after network events such as a peer connecting or a block being finalised.
     */
    public void onRefresh() {
        refreshNodeInfo();
        refreshPeerList();
        refreshNetworkStats();
    }

    /**
     * Repopulate the {@link #peerListView} with the latest connected peers
     * returned by {@link NetworkService#getConnectedPeers()} and update
     * the {@link #peerCountLabel} to reflect the current count.
     * If the peer list is empty a single placeholder entry reading
     * "No peers connected" is shown instead.
     */
    private void refreshPeerList() {
        List<Peer> peers = networkService.getConnectedPeers();
        peerListView.getItems().clear();

        if (peers == null || peers.isEmpty()) {
            peerListView.getItems().add("No peers connected");
            peerCountLabel.setText("Peers: 0");
        } else {
            for (Peer p : peers) {
                peerListView.getItems().add(formatPeer(p));
            }
            peerCountLabel.setText("Peers: " + peers.size());
        }
    }

    /**
     * Refresh the Node Information labels using the latest data from
     * {@link NodeService}.
     * The Validator ID row is set to "N/A" when {@link NodeService#isValidator()}
     * returns {@code false}.
     * The node type string is derived from {@link NodeService#isValidator()} and
     * {@link NodeService#isUniversity()}.
     */
    private void refreshNodeInfo() {
        nodeIdLabel.setText(displayNodeId != null && !displayNodeId.trim().isEmpty()
                ? displayNodeId
                : nodeService.getNodeId());

        String address = nodeService.getNodeAddress() + ":" + nodeService.getNodePort();
        nodeAddressLabel.setText(address);

        String type;
        if (nodeService.isValidator()) {
            type = "Validator";
        } else if (nodeService.isUniversity()) {
            type = "University";
        } else {
            type = "Read-Only";
        }
        nodeTypeLabel.setText(type);

        String validatorId = nodeService.getValidatorId();
        validatorIdLabel.setText(validatorId != null ? validatorId : "N/A");

        boolean running = nodeService.isRunning();
        nodeRunningLabel.setText(running ? "Yes" : "No");
        nodeRunningLabel.setTextFill(running ? Color.GREEN : Color.RED);
    }

    /**
     * Refresh the Network Statistics labels using the latest data from
     * {@link NetworkService}.
     * The {@link #networkRunningLabel} is coloured green when the listener is
     * active
     * and red when it is stopped.
     * The {@link #syncStatusLabel} displays the string returned by
     * {@link NetworkService#getSyncStatusDescription()}.
     */
    private void refreshNetworkStats() {
        boolean netRunning = networkService.isNetworkRunning();
        networkRunningLabel.setText(netRunning ? "Active" : "Stopped");
        networkRunningLabel.setTextFill(netRunning ? Color.GREEN : Color.RED);

        syncStatusLabel.setText(networkService.getSyncStatusDescription());
    }

    // -------------------------------------------------------------------------
    // Peer connection dialog
    // -------------------------------------------------------------------------

    /**
     * Open a two-field input dialog prompting for a peer's host name/IP and port
     * number.
     * On confirmation the values are validated and forwarded to
     * {@link NetworkService#connectToPeer(String, int)}.
     * An {@link Alert} is shown afterwards to report success or the specific error
     * message thrown by the service.
     * On success {@link #onRefresh()} is called automatically so the new peer
     * appears in the list immediately.
     */
    private void onConnectToPeer() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Connect to Peer");
        dialog.setHeaderText("Enter the address of the peer to connect to:");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField hostField = new TextField();
        hostField.setPromptText("e.g. 192.168.1.10");

        TextField portField = new TextField();
        portField.setPromptText("e.g. 5001");

        grid.add(new Label("Host:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Port:"), 0, 1);
        grid.add(portField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == connectButtonType) {
            String host = hostField.getText().trim();
            String portText = portField.getText().trim();

            int port;
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException ex) {
                showAlert(AlertType.ERROR, "Invalid Input", "Port must be a valid integer.");
                return;
            }

            try {
                networkService.connectToPeer(host, port);
                onRefresh();
                showAlert(AlertType.INFORMATION, "Connected",
                        "Successfully connected to " + host + ":" + port + ".");
            } catch (Exception ex) {
                showAlert(AlertType.ERROR, "Connection Failed", ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Format a single {@link Peer} as a human-readable list-item string.
     * The pattern is {@code "<nodeId>  <address>:<port>"} with a
     * {@code [Validator]}
     * suffix appended when {@link Peer#isValidator()} is {@code true}, and an
     * {@code [Offline]} suffix appended when the peer is not connected.
     *
     * @param peer the peer to format; must not be {@code null}
     * @return a non-null, human-readable string representation of the peer
     */
    private String formatPeer(Peer peer) {
        StringBuilder sb = new StringBuilder();
        sb.append(peer.getNodeId());
        sb.append("  ");
        sb.append(peer.getAddress()).append(":").append(peer.getPort());
        if (peer.isValidator()) {
            sb.append("  [Validator]");
        }
        if (!peer.isConnected()) {
            sb.append("  [Offline]");
        }
        return sb.toString();
    }

    /**
     * Create a bold {@link Label} suitable for use as a row header inside a
     * {@link GridPane} info section.
     *
     * @param text the label text; must not be {@code null}
     * @return a {@link Label} rendered in bold system font
     */
    private Label bold(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        return label;
    }

    /**
     * Show a simple {@link Alert} dialog with no header text.
     *
     * @param type    the alert type, e.g. {@link AlertType#INFORMATION} or
     *                {@link AlertType#ERROR}
     * @param title   the alert window title
     * @param message the message body to display
     */
    private void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
