package client;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ClientGUI extends JFrame {

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final Color BG_DEEP    = new Color(18, 24, 38);
    private static final Color BG_PANEL   = new Color(28, 36, 54);
    private static final Color BG_CARD    = new Color(36, 46, 68);
    private static final Color ACCENT     = new Color(0, 210, 170);
    private static final Color ACCENT2    = new Color(72, 149, 255);
    private static final Color WARN       = new Color(255, 199, 80);
    private static final Color DANGER     = new Color(239, 71, 111);
    private static final Color FG_TEXT    = new Color(220, 228, 245);
    private static final Color FG_DIM     = new Color(130, 148, 180);
    private static final Color BORDER_CLR = new Color(48, 62, 90);

    private ServerConnection connection;
    private String username;
    private String currentRoom = "";
    private boolean authenticated = false;

    /**
     * FIX 2 — Per-room chat store.
     * Each room's messages are kept in its own StringBuilder so
     * switching rooms never mixes history from different rooms.
     */
    private final Map<String, StringBuilder> roomChats = new HashMap<>();

    // login dialog controls
    private JDialog        loginDialog;
    private JTextField     loginHostField;
    private JTextField     loginPortField;
    private JTextField     loginUserField;
    private JPasswordField loginPassField;
    private JButton        loginBtnConnect;
    private JLabel         loginStatusLabel;

    // main UI controls
    private JLabel     lblStatus;

    private DefaultListModel<String> roomListModel;
    private JList<String>            roomList;

    private DefaultListModel<String> userListModel;
    private JList<String>            userList;

    private JTextArea  chatArea;
    private JTextField msgInput;
    private JButton    btnSend;

    private JComboBox<String> pmUserCombo;
    private JTextArea  pmInput;
    private JButton    btnSendPM;

    private JTextField searchField;
    private JLabel     lblLog;

    // ── Construction ─────────────────────────────────────────────────────────
    public ClientGUI() {
        connection = new ServerConnection(this);
        buildGUI();
        showLoginDialog();
    }

    // ── Main layout ──────────────────────────────────────────────────────────
    private void buildGUI() {
        setTitle("ChatLite Client");
        setSize(960, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DEEP);
        setLayout(new BorderLayout(4, 4));

        add(buildTopBar(), BorderLayout.NORTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildCenterPanel());
        mainSplit.setDividerLocation(200);
        mainSplit.setBackground(BG_DEEP);
        mainSplit.setBorder(null);

        JSplitPane fullSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                mainSplit, buildRightPanel());
        fullSplit.setDividerLocation(660);
        fullSplit.setBackground(BG_DEEP);
        fullSplit.setBorder(null);
        add(fullSplit, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Top bar ──────────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new MatteBorder(0, 0, 2, 0, ACCENT));

        JLabel dot = new JLabel("⬤");
        dot.setForeground(ACCENT);
        dot.setFont(new Font("SansSerif", Font.BOLD, 14));

        lblStatus = new JLabel("CONNECTED TO: --- | STATUS: OFFLINE");
        lblStatus.setForeground(FG_DIM);
        lblStatus.setFont(new Font("Monospaced", Font.PLAIN, 12));

        panel.add(dot);
        panel.add(lblStatus);
        return panel;
    }

    // ── LEFT panel → Private Messages ────────────────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(BG_PANEL);
        panel.setBorder(styledBorder("PRIVATE MSG", ACCENT2));
        panel.setPreferredSize(new Dimension(200, 0));

        JPanel toPanel = new JPanel(new BorderLayout(4, 4));
        toPanel.setBackground(BG_PANEL);
        JLabel toLbl = new JLabel("  To:");
        toLbl.setForeground(FG_DIM);
        toLbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        toPanel.add(toLbl, BorderLayout.WEST);

        pmUserCombo = new JComboBox<>();
        styleCombo(pmUserCombo);
        toPanel.add(pmUserCombo, BorderLayout.CENTER);

        pmInput = new JTextArea(5, 1);
        pmInput.setLineWrap(true);
        pmInput.setWrapStyleWord(true);
        pmInput.setBackground(BG_CARD);
        pmInput.setForeground(FG_TEXT);
        pmInput.setCaretColor(ACCENT2);
        pmInput.setFont(new Font("SansSerif", Font.PLAIN, 13));
        pmInput.setBorder(new LineBorder(BORDER_CLR, 1));

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 4, 4));
        btnPanel.setBackground(BG_PANEL);
        btnSendPM = styledButton("SEND", ACCENT2);
        JButton btnClearPM = styledButton("CLEAR", new Color(60, 75, 105));
        btnSendPM.addActionListener(e -> sendPrivateMessage());
        btnClearPM.addActionListener(e -> pmInput.setText(""));
        btnPanel.add(btnSendPM);
        btnPanel.add(btnClearPM);

        JScrollPane pmScroll = new JScrollPane(pmInput);
        pmScroll.setBorder(new LineBorder(BORDER_CLR, 1));
        pmScroll.getViewport().setBackground(BG_CARD);

        panel.add(toPanel,  BorderLayout.NORTH);
        panel.add(pmScroll, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ── CENTER panel → Chat ───────────────────────────────────────────────────
    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(BG_PANEL);
        panel.setBorder(styledBorder("CHAT MESSAGES", ACCENT));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(BG_DEEP);
        chatArea.setForeground(FG_TEXT);
        chatArea.setCaretColor(ACCENT);

        JScrollPane scrollChat = new JScrollPane(chatArea);
        scrollChat.setBorder(new LineBorder(BORDER_CLR, 1));
        scrollChat.getViewport().setBackground(BG_DEEP);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
        inputPanel.setBackground(BG_PANEL);
        inputPanel.setBorder(styledBorder("MESSAGE INPUT", BORDER_CLR));

        msgInput = new JTextField();
        msgInput.setFont(new Font("SansSerif", Font.PLAIN, 13));
        msgInput.setBackground(BG_CARD);
        msgInput.setForeground(FG_TEXT);
        msgInput.setCaretColor(ACCENT);
        msgInput.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1), new EmptyBorder(4, 8, 4, 8)));
        msgInput.setToolTipText("Type message here...");
        msgInput.addActionListener(e -> sendMessage());

        btnSend = styledButton("SEND ▶", ACCENT);
        btnSend.addActionListener(e -> sendMessage());

        inputPanel.add(msgInput, BorderLayout.CENTER);
        inputPanel.add(btnSend,  BorderLayout.EAST);

        JPanel searchPanel = new JPanel(new BorderLayout(4, 4));
        searchPanel.setBackground(BG_PANEL);
        JLabel searchLbl = new JLabel("  SEARCH ");
        searchLbl.setForeground(FG_DIM);
        searchLbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        searchField = new JTextField();
        searchField.setBackground(BG_CARD);
        searchField.setForeground(FG_TEXT);
        searchField.setCaretColor(ACCENT);
        searchField.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1), new EmptyBorder(3, 6, 3, 6)));
        searchField.setToolTipText("Find by user/message...");
        JButton btnSearch = styledButton("🔍", new Color(60, 75, 105));
        btnSearch.addActionListener(e -> searchMessages());
        searchPanel.add(searchLbl,   BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(btnSearch,   BorderLayout.EAST);

        JPanel bottomCenter = new JPanel(new BorderLayout(4, 4));
        bottomCenter.setBackground(BG_PANEL);
        bottomCenter.add(inputPanel,  BorderLayout.CENTER);
        bottomCenter.add(searchPanel, BorderLayout.SOUTH);

        panel.add(scrollChat,   BorderLayout.CENTER);
        panel.add(bottomCenter, BorderLayout.SOUTH);
        return panel;
    }

    // ── RIGHT panel → Rooms + Users ──────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setPreferredSize(new Dimension(190, 0));

        JPanel roomPanel = new JPanel(new BorderLayout(4, 4));
        roomPanel.setBackground(BG_PANEL);
        roomPanel.setBorder(styledBorder("CHAT ROOMS", WARN));
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        styleList(roomList, WARN);
        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = roomList.getSelectedValue();
                    if (selected != null) joinRoom(selected.split(" ")[0]);
                }
            }
        });
        JScrollPane roomScroll = new JScrollPane(roomList);
        roomScroll.setBorder(new LineBorder(BORDER_CLR, 1));
        roomScroll.getViewport().setBackground(BG_DEEP);
        JButton btnRefreshRooms = styledButton("⟳ Refresh Rooms", WARN);
        btnRefreshRooms.setForeground(BG_DEEP);
        btnRefreshRooms.addActionListener(e -> connection.send("ROOMS"));
        roomPanel.add(roomScroll,      BorderLayout.CENTER);
        roomPanel.add(btnRefreshRooms, BorderLayout.SOUTH);

        JPanel userPanel = new JPanel(new BorderLayout(4, 4));
        userPanel.setBackground(BG_PANEL);
        userPanel.setBorder(styledBorder("ONLINE USERS", ACCENT));
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        styleList(userList, ACCENT);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(new LineBorder(BORDER_CLR, 1));
        userScroll.getViewport().setBackground(BG_DEEP);
        JButton btnRefreshUsers = styledButton("⟳ Refresh Users", ACCENT);
        btnRefreshUsers.addActionListener(e -> connection.send("USERS"));
        userPanel.add(userScroll,      BorderLayout.CENTER);
        userPanel.add(btnRefreshUsers, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roomPanel, userPanel);
        split.setDividerLocation(260);
        split.setBackground(BG_PANEL);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // ── Bottom bar ───────────────────────────────────────────────────────────
    private JPanel buildBottomBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DEEP);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER_CLR),
                new EmptyBorder(4, 10, 4, 10)));
        lblLog = new JLabel("[--:--:--] Ready");
        lblLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lblLog.setForeground(FG_DIM);
        panel.add(lblLog, BorderLayout.CENTER);
        return panel;
    }

    // ── Login dialog ─────────────────────────────────────────────────────────
    private void showLoginDialog() {
        loginDialog = new JDialog(this, "ChatLite — Login", Dialog.ModalityType.APPLICATION_MODAL);
        loginDialog.getContentPane().setBackground(BG_PANEL);

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBackground(BG_PANEL);
        form.setBorder(new EmptyBorder(16, 16, 8, 16));

        loginHostField = styledField("localhost");
        loginPortField = styledField("5000");
        loginUserField = styledField("");
        loginPassField = new JPasswordField();
        stylePasswordField(loginPassField);

        form.add(dimLabel("Server IP:"));  form.add(loginHostField);
        form.add(dimLabel("Port:"));       form.add(loginPortField);
        form.add(dimLabel("Username:"));   form.add(loginUserField);
        form.add(dimLabel("Password:"));   form.add(loginPassField);

        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setForeground(WARN);
        loginStatusLabel.setFont(new Font("Monospaced", Font.ITALIC, 11));
        loginStatusLabel.setBorder(new EmptyBorder(6, 16, 2, 16));

        loginBtnConnect = styledButton("  Connect  ", ACCENT);
        loginBtnConnect.setForeground(BG_DEEP);
        loginBtnConnect.setFont(new Font("Monospaced", Font.BOLD, 13));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 8));
        btns.setBackground(BG_PANEL);
        btns.add(loginBtnConnect);

        loginDialog.getContentPane().setLayout(new BorderLayout(6, 6));
        loginDialog.getContentPane().add(loginStatusLabel, BorderLayout.NORTH);
        loginDialog.getContentPane().add(form,             BorderLayout.CENTER);
        loginDialog.getContentPane().add(btns,             BorderLayout.SOUTH);
        loginDialog.setSize(380, 240);
        loginDialog.setLocationRelativeTo(this);

        loginBtnConnect.addActionListener(e -> {
            String host = loginHostField.getText().trim();
            int port;
            try { port = Integer.parseInt(loginPortField.getText().trim()); }
            catch (NumberFormatException ex) { JOptionPane.showMessageDialog(loginDialog, "Invalid port"); return; }
            String user = loginUserField.getText().trim();
            String pass = new String(loginPassField.getPassword()).trim();
            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(loginDialog, "Username and password required");
                return;
            }
            loginBtnConnect.setEnabled(false);
            loginStatusLabel.setText("Connecting...");
            new Thread(() -> {
                connection.disconnect();
                boolean ok = connection.connect(host, port);
                if (!ok) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(loginDialog, "Cannot connect to server");
                        loginBtnConnect.setEnabled(true);
                        loginStatusLabel.setText("Connection failed");
                    });
                    return;
                }
                connection.send("HELLO " + user + " " + pass);
                SwingUtilities.invokeLater(() -> loginStatusLabel.setText("Authenticating..."));
            }).start();
        });

        loginDialog.setVisible(true);
    }

    // ── Room logic ────────────────────────────────────────────────────────────

    /**
     * FIX 2 — Room switching with isolated chat history.
     * 1. Save whatever is currently in chatArea back to the old room's buffer.
     * 2. Switch currentRoom.
     * 3. Clear chatArea and restore the new room's saved buffer (may be empty
     *    on first visit; the server will then send HIST to populate it).
     */
    private void joinRoom(String roomName) {
        if (roomName.equals(currentRoom)) return; // already in this room

        // 1. Save current room's chat before leaving
        if (!currentRoom.isEmpty()) {
            roomChats.put(currentRoom, new StringBuilder(chatArea.getText()));
        }

        // 2. Switch
        currentRoom = roomName;

        // 3. Restore this room's local buffer (or start empty)
        StringBuilder saved = roomChats.get(roomName);
        chatArea.setText(saved != null ? saved.toString() : "");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());

        // Ask server to JOIN (which also triggers HIST replay from server)
        connection.send("JOIN " + roomName);
        addLog("Joined room: " + roomName);

        // Show a small header so the user knows which room they are in
        if (saved == null) {
            // first visit — the HIST separators will appear shortly
            appendToCurrentRoom("*** Joined " + roomName + " ***\n");
        }
    }

    // ── Message sending ───────────────────────────────────────────────────────
    private void sendMessage() {
        String msg = msgInput.getText().trim();
        if (msg.isEmpty()) return;

        if (currentRoom.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please join a room first!\nDouble-click on a room from the list.");
            return;
        }

        connection.send("MSG " + currentRoom + " " + msg);
        appendToCurrentRoom("[" + getTime() + "] " + username + ": " + msg + "\n");
        msgInput.setText("");
    }

    private void sendPrivateMessage() {
        String targetUser = (String) pmUserCombo.getSelectedItem();
        String msg = pmInput.getText().trim();
        if (targetUser == null || msg.isEmpty()) return;
        connection.send("PM " + targetUser + " " + msg);
        appendToCurrentRoom("[" + getTime() + "] [PM→" + targetUser + "] " + msg + "\n");
        pmInput.setText("");
        addLog("Private message sent to " + targetUser);
    }

    private void searchMessages() {
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) return;
        String[] lines = chatArea.getText().split("\n");
        StringBuilder results = new StringBuilder();
        for (String line : lines) {
            if (line.toLowerCase().contains(query)) results.append(line).append("\n");
        }
        if (results.length() == 0) {
            JOptionPane.showMessageDialog(this, "No results found for: " + query);
        } else {
            JOptionPane.showMessageDialog(this,
                    new JScrollPane(new JTextArea(results.toString(), 10, 40)),
                    "Search Results", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ── Append helper (always writes to current room's buffer + chatArea) ─────
    private void appendToCurrentRoom(String text) {
        // keep the in-memory buffer in sync
        roomChats.computeIfAbsent(currentRoom, k -> new StringBuilder()).append(text);
        chatArea.append(text);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // ── Server message handler ────────────────────────────────────────────────
    public void handleServerMessage(String msg) {
        SwingUtilities.invokeLater(() -> {

            // ── Authentication ─────────────────────────────────────────────
            if (msg.startsWith("200 WELCOME")) {
                authenticated = true;
                if (loginDialog != null && loginDialog.isShowing()) loginDialog.dispose();
                username = (loginUserField != null) ? loginUserField.getText().trim() : username;
                String host = (loginHostField != null) ? loginHostField.getText().trim() : "localhost";
                String port = (loginPortField != null) ? loginPortField.getText().trim() : "5000";
                setTitle("ChatLite Client - [" + username + "]");
                lblStatus.setText("CONNECTED TO: " + host + ":" + port + " | STATUS: ONLINE");
                lblStatus.setForeground(ACCENT);
                // re-enable all inputs on fresh login
                setInputsEnabled(true);
                connection.send("ROOMS");
                connection.send("USERS");
                addLog("Connected to " + host + ":" + port);

            } else if (msg.startsWith("401")) {
                if (loginDialog != null && loginDialog.isShowing()) {
                    JOptionPane.showMessageDialog(loginDialog, "Password incorrect. Please try again.");
                    if (loginPassField  != null) loginPassField.setText("");
                    if (loginBtnConnect != null) loginBtnConnect.setEnabled(true);
                    if (loginStatusLabel!= null) loginStatusLabel.setText("Password incorrect");
                } else {
                    JPasswordField pf = new JPasswordField();
                    int res = JOptionPane.showConfirmDialog(this, pf,
                            "Password incorrect. Enter password:", JOptionPane.OK_CANCEL_OPTION);
                    if (res == JOptionPane.OK_OPTION) {
                        String newPass = new String(pf.getPassword()).trim();
                        if (!newPass.isEmpty() && loginHostField != null && loginUserField != null) {
                            connection.disconnect();
                            connection.connect(loginHostField.getText().trim(),
                                    Integer.parseInt(loginPortField.getText().trim()));
                            connection.send("HELLO " + loginUserField.getText().trim() + " " + newPass);
                        }
                    }
                }

                // ── FIX 1 — Kicked / deleted by server ────────────────────────
            } else if (msg.startsWith("KICKED")) {
                String reason = msg.contains("deleted")
                        ? "Your account has been deleted by the administrator."
                        : "You have been kicked by the administrator.";

                // 1. Lock all chat inputs immediately so no more messages can be sent
                setInputsEnabled(false);
                authenticated = false;
                currentRoom   = "";
                roomChats.clear();

                // 2. Inform the user
                JOptionPane.showMessageDialog(this, reason,
                        "Disconnected", JOptionPane.WARNING_MESSAGE);

                // 3. Re-open the login dialog so they can reconnect (or give up)
                lblStatus.setText("DISCONNECTED — " + reason);
                lblStatus.setForeground(DANGER);
                connection.disconnect();
                addLog("KICKED: " + reason);

                // Reset state and show login again
                username      = null;
                userListModel.clear();
                roomListModel.clear();
                pmUserCombo.removeAllItems();
                chatArea.setText("");
                showLoginDialog();

                // ── Room/user lists ────────────────────────────────────────────
            } else if (msg.startsWith("210 JOINED")) {
                addLog("Joined: " + msg.substring(7));

            } else if (msg.startsWith("211 SENT")) {
                // acknowledged

            } else if (msg.startsWith("212 PRIVATE SENT")) {
                addLog("Private message delivered");

            } else if (msg.startsWith("213U")) {
                String user = msg.substring(5).trim();
                if (!userListModel.contains(user)) userListModel.addElement(user);
                boolean found = false;
                for (int i = 0; i < pmUserCombo.getItemCount(); i++) {
                    if (pmUserCombo.getItemAt(i).equals(user)) { found = true; break; }
                }
                if (!found && !user.equals(username)) pmUserCombo.addItem(user);

            } else if (msg.startsWith("213 END")) {
                addLog("Users list updated");

            } else if (msg.startsWith("214")) {
                String room = msg.substring(4).trim();
                if (!roomListModel.contains(room)) roomListModel.addElement(room);

                // ── FIX 2 — Chat history (scoped to the room being joined) ─────
            } else if (msg.startsWith("HIST_START")) {
                // HIST_START <roomName> — only render if we're still in that room
                String histRoom = msg.length() > 11 ? msg.substring(11).trim() : currentRoom;
                if (histRoom.equals(currentRoom)) {
                    appendToCurrentRoom("─────────── chat history ───────────\n");
                }

            } else if (msg.startsWith("HIST ")) {
                // strip leading "<roomName> " prefix saved by ChatHistory
                String content  = msg.substring(5);
                int    spaceIdx = content.indexOf(' ');
                String body     = spaceIdx >= 0 ? content.substring(spaceIdx + 1) : content;
                // determine which room this history belongs to
                String histRoom = spaceIdx >= 0 ? content.substring(0, spaceIdx) : currentRoom;
                if (histRoom.equals(currentRoom)) {
                    appendToCurrentRoom("[hist] " + body + "\n");
                } else {
                    // history for a room we've already left — store silently
                    roomChats.computeIfAbsent(histRoom, k -> new StringBuilder())
                            .append("[hist] ").append(body).append("\n");
                }

            } else if (msg.equals("HIST_END")) {
                appendToCurrentRoom("────────────────────────────────────\n");
                addLog("Chat history loaded for: " + currentRoom);

                // ── Live messages ──────────────────────────────────────────────
            } else if (msg.startsWith("ROOM")) {
                // Format: ROOM <roomName> <username>: <message>
                String content  = msg.substring(5);              // "<roomName> <user>: <text>"
                int    spaceIdx = content.indexOf(' ');
                String msgRoom  = spaceIdx >= 0 ? content.substring(0, spaceIdx) : "";
                String body     = spaceIdx >= 0 ? content.substring(spaceIdx + 1) : content;

                String line = "[" + getTime() + "] " + body + "\n";
                if (msgRoom.equals(currentRoom)) {
                    // currently viewing this room — show immediately
                    appendToCurrentRoom(line);
                } else {
                    // message for a different room — buffer it silently
                    roomChats.computeIfAbsent(msgRoom, k -> new StringBuilder()).append(line);
                }

            } else if (msg.startsWith("PM")) {
                String[] parts = msg.split(" ", 3);
                String sender  = parts[1];
                String pmMsg   = parts.length > 2 ? parts[2] : "";
                appendToCurrentRoom("[" + getTime() + "] [PM from " + sender + "] " + pmMsg + "\n");
                addLog("Private message from " + sender);

            } else if (msg.startsWith("221 BYE")) {
                addLog("Disconnected from server");

            } else if (msg.equals("DISCONNECTED")) {
                lblStatus.setText("DISCONNECTED");
                lblStatus.setForeground(DANGER);
                addLog("Connection lost!");
            }
        });
    }

    // ── FIX 1 helper — enable/disable all chat inputs at once ────────────────
    private void setInputsEnabled(boolean enabled) {
        msgInput.setEnabled(enabled);
        btnSend.setEnabled(enabled);
        btnSendPM.setEnabled(enabled);
        pmInput.setEnabled(enabled);
        pmUserCombo.setEnabled(enabled);
    }

    // ── Utility ──────────────────────────────────────────────────────────────
    private void addLog(String event) {
        lblLog.setText("[" + getTime() + "] " + event);
    }

    private String getTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    // ── Style helpers ─────────────────────────────────────────────────────────
    private TitledBorder styledBorder(String title, Color color) {
        TitledBorder tb = new TitledBorder(new LineBorder(color, 1), " " + title + " ");
        tb.setTitleColor(color);
        tb.setTitleFont(new Font("Monospaced", Font.BOLD, 11));
        return tb;
    }

    private JButton styledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Monospaced", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private <E> void styleList(JList<E> list, Color selColor) {
        list.setBackground(BG_DEEP);
        list.setForeground(FG_TEXT);
        list.setFont(new Font("Monospaced", Font.PLAIN, 12));
        list.setSelectionBackground(selColor.darker().darker());
        list.setSelectionForeground(selColor);
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setBackground(BG_CARD);
        combo.setForeground(FG_TEXT);
        combo.setFont(new Font("SansSerif", Font.PLAIN, 12));
    }

    private JTextField styledField(String text) {
        JTextField tf = new JTextField(text);
        tf.setBackground(BG_CARD);
        tf.setForeground(FG_TEXT);
        tf.setCaretColor(ACCENT);
        tf.setFont(new Font("Monospaced", Font.PLAIN, 13));
        tf.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1), new EmptyBorder(4, 8, 4, 8)));
        return tf;
    }

    private void stylePasswordField(JPasswordField pf) {
        pf.setBackground(BG_CARD);
        pf.setForeground(FG_TEXT);
        pf.setCaretColor(ACCENT);
        pf.setFont(new Font("Monospaced", Font.PLAIN, 13));
        pf.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1), new EmptyBorder(4, 8, 4, 8)));
    }

    private JLabel dimLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(FG_DIM);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        return lbl;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}