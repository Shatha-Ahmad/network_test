package client;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClientGUI extends JFrame {

    // ── Theme palette ────────────────────────────────────────────
    private static final Color BG_DARK      = new Color(18,  26,  38);   // deep navy
    private static final Color BG_MID       = new Color(26,  38,  54);   // panel bg
    private static final Color BG_LIGHT     = new Color(34,  50,  70);   // input / list bg
    private static final Color ACCENT       = new Color(0,  188, 212);   // teal accent
    private static final Color ACCENT_DARK  = new Color(0,  150, 170);   // hover teal
    private static final Color TEXT_MAIN    = new Color(220, 232, 240);  // primary text
    private static final Color TEXT_DIM     = new Color(120, 150, 175);  // secondary text
    private static final Color BORDER_CLR   = new Color(0,  188, 212, 60); // subtle border
    private static final Font  MONO_FONT    = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font  BODY_FONT    = new Font("SansSerif",  Font.PLAIN, 13);
    // ─────────────────────────────────────────────────────────────

    private ServerConnection connection;
    private String username;
    private String currentRoom = "";
    private boolean authenticated = false;

    // login dialog controls (shared so handler can access)
    private JDialog loginDialog;
    private JTextField loginHostField;
    private JTextField loginPortField;
    private JTextField loginUserField;
    private JPasswordField loginPassField;
    private JButton loginBtnConnect;
    private JLabel loginStatusLabel;

    // ═══════════════════════════════

    private JLabel lblStatus;

    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;

    private DefaultListModel<String> userListModel;
    private JList<String> userList;

    private JTextArea chatArea;

    private JTextField msgInput;
    private JButton btnSend;

    private JComboBox<String> pmUserCombo;
    private JTextArea pmInput;
    private JButton btnSendPM;

    private JTextField searchField;

    private JLabel lblLog;

    // ── Room chat history (persists across leave/rejoin) ─────────
    private final java.util.Map<String, StringBuilder> roomHistory =
            new java.util.HashMap<>();

    // ═══════════════════════════════
    public ClientGUI() {
        connection = new ServerConnection(this);
        buildGUI();
        showLoginDialog();
    }

    // ═══════════════════════════════
    private void buildGUI() {
        setTitle("ChatLite Client");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(BG_DARK);

        add(buildTopBar(), BorderLayout.NORTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(),
                buildCenterPanel()
        );
        mainSplit.setDividerLocation(180);
        mainSplit.setBackground(BG_DARK);
        mainSplit.setBorder(null);

        JSplitPane fullSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                mainSplit,
                buildRightPanel()
        );
        fullSplit.setDividerLocation(620);
        fullSplit.setBackground(BG_DARK);
        fullSplit.setBorder(null);
        add(fullSplit, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── helper: styled TitledBorder ──────────────────────────────
    private TitledBorder titledBorder(String title) {
        TitledBorder tb = new TitledBorder(
            BorderFactory.createLineBorder(BORDER_CLR, 1), title);
        tb.setTitleColor(ACCENT);
        tb.setTitleFont(new Font("SansSerif", Font.BOLD, 11));
        return tb;
    }

    // ── helper: accent button ────────────────────────────────────
    private JButton accentButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ACCENT);
        btn.setForeground(BG_DARK);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(ACCENT_DARK); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(ACCENT); }
        });
        return btn;
    }

    // ── helper: style list ───────────────────────────────────────
    private <T> void styleList(JList<T> list) {
        list.setBackground(BG_LIGHT);
        list.setForeground(TEXT_MAIN);
        list.setFont(BODY_FONT);
        list.setSelectionBackground(ACCENT);
        list.setSelectionForeground(BG_DARK);
        list.setBorder(new EmptyBorder(4, 4, 4, 4));
    }

    // ── helper: style scroll pane ────────────────────────────────
    private JScrollPane darkScroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.getViewport().setBackground(BG_LIGHT);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_CLR, 1));
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            protected void configureScrollBarColors() {
                thumbColor = ACCENT_DARK;
                trackColor = BG_MID;
            }
        });
        return sp;
    }

    // ── helper: style text field ─────────────────────────────────
    private void styleTextField(JTextField tf) {
        tf.setBackground(BG_LIGHT);
        tf.setForeground(TEXT_MAIN);
        tf.setCaretColor(ACCENT);
        tf.setFont(BODY_FONT);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR, 1),
            new EmptyBorder(4, 8, 4, 8)));
    }

    // ─────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(BG_MID);
        panel.setBorder(new EmptyBorder(5, 10, 5, 10));

        lblStatus = new JLabel("CONNECTED TO: --- | STATUS: OFFLINE");
        lblStatus.setForeground(TEXT_DIM);
        lblStatus.setFont(MONO_FONT);

        JLabel dot = new JLabel("●");
        dot.setForeground(ACCENT);
        dot.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(dot);

        panel.add(lblStatus);
        return panel;
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(180, 0));
        panel.setBackground(BG_MID);

        // Rooms
        JPanel roomPanel = new JPanel(new BorderLayout());
        roomPanel.setBorder(titledBorder("CHAT ROOMS"));
        roomPanel.setBackground(BG_MID);

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        styleList(roomList);

        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = roomList.getSelectedValue();
                    if (selected != null) {
                        String roomName = selected.split(" ")[0];
                        joinRoom(roomName);
                    }
                }
            }
        });

        roomPanel.add(darkScroll(roomList), BorderLayout.CENTER);

        JButton btnRefreshRooms = accentButton("Refresh Rooms");
        btnRefreshRooms.addActionListener(e -> connection.send("ROOMS"));
        roomPanel.add(btnRefreshRooms, BorderLayout.SOUTH);

        // Users
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBorder(titledBorder("ONLINE USERS"));
        userPanel.setBackground(BG_MID);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        styleList(userList);

        userPanel.add(darkScroll(userList), BorderLayout.CENTER);

        JButton btnRefreshUsers = accentButton("Refresh Users");
        btnRefreshUsers.addActionListener(e -> connection.send("USERS"));
        userPanel.add(btnRefreshUsers, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roomPanel, userPanel);
        split.setDividerLocation(250);
        split.setBackground(BG_MID);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(3, 3));
        panel.setBorder(titledBorder("CHAT MESSAGES"));
        panel.setBackground(BG_MID);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(MONO_FONT);
        chatArea.setBackground(BG_DARK);
        chatArea.setForeground(TEXT_MAIN);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setCaretColor(ACCENT);
        chatArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane scrollChat = darkScroll(chatArea);

        JPanel inputPanel = new JPanel(new BorderLayout(3, 3));
        inputPanel.setBorder(titledBorder("MESSAGE INPUT"));
        inputPanel.setBackground(BG_MID);

        msgInput = new JTextField();
        styleTextField(msgInput);
        msgInput.setToolTipText("Type message here...");
        msgInput.addActionListener(e -> sendMessage());

        btnSend = accentButton("SEND");
        btnSend.addActionListener(e -> sendMessage());

        inputPanel.add(msgInput, BorderLayout.CENTER);
        inputPanel.add(btnSend, BorderLayout.EAST);

        JPanel searchPanel = new JPanel(new BorderLayout(3, 3));
        searchPanel.setBackground(BG_MID);
        searchField = new JTextField();
        styleTextField(searchField);
        searchField.setToolTipText("Find by user/message...");
        JButton btnSearch = accentButton("🔍");
        btnSearch.addActionListener(e -> searchMessages());
        JLabel searchLbl = new JLabel("SEARCH: ");
        searchLbl.setForeground(TEXT_DIM);
        searchLbl.setFont(MONO_FONT);
        searchPanel.add(searchLbl, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(btnSearch, BorderLayout.EAST);

        JPanel bottomCenter = new JPanel(new BorderLayout(3, 3));
        bottomCenter.setBackground(BG_MID);
        bottomCenter.add(inputPanel, BorderLayout.CENTER);
        bottomCenter.add(searchPanel, BorderLayout.SOUTH);

        panel.add(scrollChat, BorderLayout.CENTER);
        panel.add(bottomCenter, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(3, 3));
        panel.setPreferredSize(new Dimension(200, 0));
        panel.setBorder(titledBorder("PRIVATE MSG"));
        panel.setBackground(BG_MID);

        JPanel toPanel = new JPanel(new BorderLayout(3, 3));
        toPanel.setBackground(BG_MID);
        JLabel toLbl = new JLabel("To:");
        toLbl.setForeground(TEXT_DIM);
        toLbl.setFont(BODY_FONT);
        toPanel.add(toLbl, BorderLayout.WEST);
        pmUserCombo = new JComboBox<>();
        pmUserCombo.setBackground(BG_LIGHT);
        pmUserCombo.setForeground(TEXT_MAIN);
        pmUserCombo.setFont(BODY_FONT);
        toPanel.add(pmUserCombo, BorderLayout.CENTER);

        pmInput = new JTextArea(5, 1);
        pmInput.setLineWrap(true);
        pmInput.setWrapStyleWord(true);
        pmInput.setBackground(BG_LIGHT);
        pmInput.setForeground(TEXT_MAIN);
        pmInput.setCaretColor(ACCENT);
        pmInput.setFont(BODY_FONT);
        pmInput.setBorder(BorderFactory.createLineBorder(BORDER_CLR, 1));

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 3, 3));
        btnPanel.setBackground(BG_MID);
        btnSendPM = accentButton("SEND");
        JButton btnClearPM = accentButton("CLEAR");
        btnClearPM.setBackground(new Color(60, 80, 100));
        btnClearPM.setForeground(TEXT_MAIN);

        btnSendPM.addActionListener(e -> sendPrivateMessage());
        btnClearPM.addActionListener(e -> pmInput.setText(""));

        btnPanel.add(btnSendPM);
        btnPanel.add(btnClearPM);

        panel.add(toPanel, BorderLayout.NORTH);
        panel.add(darkScroll(pmInput), BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildBottomBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_MID);
        panel.setBorder(new EmptyBorder(3, 5, 3, 5));

        lblLog = new JLabel("[--:--:--] Ready");
        lblLog.setFont(MONO_FONT);
        lblLog.setForeground(TEXT_DIM);

        panel.add(lblLog, BorderLayout.CENTER);
        return panel;
    }

    // ═══════════════════════════════
    private void showLoginDialog() {
        loginDialog = new JDialog(this, "ChatLite — Login", Dialog.ModalityType.APPLICATION_MODAL);
        loginDialog.getContentPane().setBackground(BG_MID);

        JPanel p = new JPanel(new GridLayout(5, 1, 5, 5));
        p.setBackground(BG_MID);
        p.setBorder(new EmptyBorder(10, 15, 10, 15));

        loginHostField = new JTextField("localhost");
        loginPortField = new JTextField("5000");
        loginUserField = new JTextField();
        loginPassField = new JPasswordField();

        styleTextField(loginHostField);
        styleTextField(loginPortField);
        styleTextField(loginUserField);
        loginPassField.setBackground(BG_LIGHT);
        loginPassField.setForeground(TEXT_MAIN);
        loginPassField.setCaretColor(ACCENT);
        loginPassField.setFont(BODY_FONT);
        loginPassField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR, 1),
            new EmptyBorder(4, 8, 4, 8)));

        String[] labels = {"Server IP:", "Port:", "Username:", "Password:"};
        JTextField[] fields = {loginHostField, loginPortField, loginUserField, loginPassField};
        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(TEXT_DIM);
            lbl.setFont(BODY_FONT);
            p.add(lbl);
            p.add(fields[i]);
        }

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.setBackground(BG_MID);
        loginBtnConnect = accentButton("Connect");
        btns.add(loginBtnConnect);

        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        loginStatusLabel.setForeground(TEXT_DIM);
        loginStatusLabel.setFont(MONO_FONT);
        loginStatusLabel.setBorder(new EmptyBorder(4, 10, 0, 0));

        loginDialog.getContentPane().setLayout(new BorderLayout(5, 5));
        loginDialog.getContentPane().add(p, BorderLayout.CENTER);
        loginDialog.getContentPane().add(btns, BorderLayout.SOUTH);
        loginDialog.getContentPane().add(loginStatusLabel, BorderLayout.NORTH);
        loginDialog.setSize(360, 260);
        loginDialog.setLocationRelativeTo(this);

        loginBtnConnect.addActionListener(e -> {
            String host = loginHostField.getText().trim();
            int port;
            try { port = Integer.parseInt(loginPortField.getText().trim()); } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(loginDialog, "Invalid port"); return; }
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

    private void joinRoom(String roomName) {
        // save whatever is currently in the chat area to the old room's history
        if (!currentRoom.isEmpty()) {
            roomHistory.put(currentRoom, new StringBuilder(chatArea.getText()));
        }

        currentRoom = roomName;
        connection.send("JOIN " + roomName);
        addLog("Joined room: " + roomName);

        // restore history for the new room if it exists, otherwise start fresh
        chatArea.setText("");
        if (roomHistory.containsKey(roomName)) {
            chatArea.setText(roomHistory.get(roomName).toString());
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            appendMessage("--- Rejoined " + roomName + " — history loaded ---");
        } else {
            appendMessage("*** Joined " + roomName + " ***");
        }
    }

    private void sendMessage() {
        String msg = msgInput.getText().trim();
        if (msg.isEmpty()) return;

        if (currentRoom.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please join a room first!\nDouble-click on a room from the list.");
            return;
        }

        connection.send("MSG " + currentRoom + " " + msg);
        appendMessage("[" + getTime() + "] " + username + ": " + msg);
        msgInput.setText("");
    }

    private void sendPrivateMessage() {
        String targetUser = (String) pmUserCombo.getSelectedItem();
        String msg = pmInput.getText().trim();

        if (targetUser == null || msg.isEmpty()) return;

        connection.send("PM " + targetUser + " " + msg);
        appendMessage("[" + getTime() + "] [PM→" + targetUser + "] " + msg);
        pmInput.setText("");
        addLog("Private message sent to " + targetUser);
    }

    private void searchMessages() {
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) return;

        String[] lines = chatArea.getText().split("\n");
        StringBuilder results = new StringBuilder();

        for (String line : lines) {
            if (line.toLowerCase().contains(query)) {
                results.append(line).append("\n");
            }
        }

        if (results.length() == 0) {
            JOptionPane.showMessageDialog(this, "No results found for: " + query);
        } else {
            JTextArea resultArea = new JTextArea(results.toString(), 10, 40);
            resultArea.setBackground(BG_DARK);
            resultArea.setForeground(TEXT_MAIN);
            resultArea.setFont(MONO_FONT);
            JOptionPane.showMessageDialog(this,
                    new JScrollPane(resultArea),
                    "Search Results", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ═══════════════════════════════
    public void handleServerMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (msg.startsWith("200 WELCOME")) {
                authenticated = true;
                if (loginDialog != null && loginDialog.isShowing()) {
                    loginDialog.dispose();
                }
                username = (loginUserField != null) ? loginUserField.getText().trim() : username;
                String host = (loginHostField != null) ? loginHostField.getText().trim() : "localhost";
                String port = (loginPortField != null) ? loginPortField.getText().trim() : "5000";
                setTitle("ChatLite Client - [" + username + "]");
                lblStatus.setText("CONNECTED TO: " + host + ":" + port + " | STATUS: ONLINE");
                lblStatus.setForeground(ACCENT);
                connection.send("ROOMS");
                connection.send("USERS");
                addLog("Connected to " + host + ":" + port);

            } else if (msg.startsWith("401")) {
                if (loginDialog != null && loginDialog.isShowing()) {
                    JOptionPane.showMessageDialog(loginDialog, "Password incorrect. Please try again.");
                    if (loginPassField != null) loginPassField.setText("");
                    if (loginBtnConnect != null) loginBtnConnect.setEnabled(true);
                    if (loginStatusLabel != null) loginStatusLabel.setText("Password incorrect");
                } else {
                    JPasswordField pf = new JPasswordField();
                    pf.setBackground(BG_LIGHT);
                    pf.setForeground(TEXT_MAIN);
                    int res = JOptionPane.showConfirmDialog(this, pf, "Password incorrect. Enter password:", JOptionPane.OK_CANCEL_OPTION);
                    if (res == JOptionPane.OK_OPTION) {
                        String newPass = new String(pf.getPassword()).trim();
                        if (!newPass.isEmpty() && loginHostField != null && loginUserField != null) {
                            connection.disconnect();
                            connection.connect(loginHostField.getText().trim(), Integer.parseInt(loginPortField.getText().trim()));
                            connection.send("HELLO " + loginUserField.getText().trim() + " " + newPass);
                        }
                    }
                }

            } else if (msg.startsWith("210 JOINED")) {
                addLog("Joined: " + msg.substring(7));

            } else if (msg.startsWith("211 SENT")) {
                // acknowledged

            } else if (msg.startsWith("212 PRIVATE SENT")) {
                addLog("Private message delivered");

            } else if (msg.startsWith("213U")) {
                String user = msg.substring(5).trim();
                if (!userListModel.contains(user)) {
                    userListModel.addElement(user);
                }
                boolean found = false;
                for (int i = 0; i < pmUserCombo.getItemCount(); i++) {
                    if (pmUserCombo.getItemAt(i).equals(user)) { found = true; break; }
                }
                if (!found && !user.equals(username)) pmUserCombo.addItem(user);

            } else if (msg.startsWith("213 END")) {
                addLog("Users list updated");

            } else if (msg.startsWith("214")) {
                String room = msg.substring(4).trim();
                if (!roomListModel.contains(room)) {
                    roomListModel.addElement(room);
                }

            } else if (msg.startsWith("ROOM")) {
                String content = msg.substring(5);
                appendMessage("[" + getTime() + "] " + content);

            } else if (msg.startsWith("PM")) {
                String[] parts = msg.split(" ", 3);
                String sender = parts[1];
                String pmMsg = parts.length > 2 ? parts[2] : "";
                appendMessage("[" + getTime() + "] [PM from " + sender + "] " + pmMsg);
                addLog("Private message from " + sender);

            } else if (msg.startsWith("221 BYE")) {
                addLog("Disconnected from server");

            } else if (msg.startsWith("SERVER: You have been kicked")) {
                // kicked by admin
                lblStatus.setText("DISCONNECTED — KICKED BY SERVER");
                lblStatus.setForeground(new Color(255, 80, 80));
                msgInput.setEnabled(false);
                btnSend.setEnabled(false);
                btnSendPM.setEnabled(false);
                appendMessage("\n⚠ You have been kicked by the server administrator.");
                addLog("You were kicked by the server!");
                JOptionPane.showMessageDialog(ClientGUI.this,
                    "You have been kicked by the server administrator.\nYou are now disconnected.",
                    "Kicked", JOptionPane.WARNING_MESSAGE);
                connection.disconnect();

            } else if (msg.startsWith("SERVER: You have been deleted")) {
                // account deleted by admin while online
                lblStatus.setText("DISCONNECTED — ACCOUNT DELETED");
                lblStatus.setForeground(new Color(255, 80, 80));
                msgInput.setEnabled(false);
                btnSend.setEnabled(false);
                btnSendPM.setEnabled(false);
                appendMessage("\n⚠ Your account has been deleted by the server administrator.");
                addLog("Your account was deleted!");
                JOptionPane.showMessageDialog(ClientGUI.this,
                    "Your account has been deleted by the server administrator.\nYou are now disconnected.",
                    "Account Deleted", JOptionPane.ERROR_MESSAGE);
                connection.disconnect();

            } else if (msg.equals("DISCONNECTED")) {
                lblStatus.setText("DISCONNECTED");
                lblStatus.setForeground(new Color(255, 80, 80));
                addLog("Connection lost!");
            }
        });
    }

    private void appendMessage(String msg) {
        chatArea.append(msg + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
        // persist to room history so it survives leave/rejoin
        if (!currentRoom.isEmpty()) {
            roomHistory.computeIfAbsent(currentRoom, k -> new StringBuilder())
                       .append(msg).append("\n");
        }
    }

    private void addLog(String event) {
        lblLog.setText("[" + getTime() + "] " + event);
    }

    private String getTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}
