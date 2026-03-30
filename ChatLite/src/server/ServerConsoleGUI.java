package server;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ServerConsoleGUI extends JFrame {

    // ── Palette ───────────────────────────────────────────────────────────────
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

    private static final int PORT = 5000;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    private JLabel              lblServerStatus;
    private DefaultTableModel   sessionsTableModel;
    private DefaultTableModel   mailboxTableModel;

    private DefaultListModel<String> existingUsersModel;
    private JList<String>            usersList;

    private JTextField     txtNewUsername;
    private JPasswordField txtNewPassword;

    private JTextArea         logArea;
    private JTextField        txtBroadcast;
    private JComboBox<String> maxMsgSizeCombo;
    private javax.swing.Timer refreshTimer;

    private final File usersFile = new File("users.txt");

    // ── Construction ──────────────────────────────────────────────────────────
    public ServerConsoleGUI() {
        buildGUI();
        loadUsersFromFile();
        startServer();
        startAutoRefresh();
    }

    // ── Main layout ───────────────────────────────────────────────────────────
    private void buildGUI() {
        setTitle("ChatLite Server Console - [RUNNING]");
        setSize(1050, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DEEP);
        setLayout(new BorderLayout(4, 4));

        add(buildTopBar(), BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        mainPanel.setBackground(BG_DEEP);
        mainPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        mainPanel.add(buildSessionsPanel());
        mainPanel.add(buildUserManagementPanel());
        mainPanel.add(buildMailboxPanel());
        add(mainPanel, BorderLayout.CENTER);

        add(buildLogPanel(), BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 7));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new MatteBorder(0, 0, 2, 0, ACCENT));

        JLabel dot = new JLabel("⬤");
        dot.setForeground(ACCENT);
        dot.setFont(new Font("SansSerif", Font.BOLD, 14));

        lblServerStatus = new JLabel(
                "SERVER STATUS: ONLINE  |  PORT (TCP): " + PORT + "  |  Uptime: 00:00:00");
        lblServerStatus.setForeground(ACCENT);
        lblServerStatus.setFont(new Font("Monospaced", Font.PLAIN, 12));

        panel.add(dot);
        panel.add(lblServerStatus);
        return panel;
    }

    // ── Sessions panel ────────────────────────────────────────────────────────
    private JPanel buildSessionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(BG_PANEL);
        panel.setBorder(styledBorder("ACTIVE SESSIONS & ROSTER", ACCENT));

        String[] cols = {"Username", "Status", "IP Address"};
        sessionsTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(sessionsTableModel);
        styleTable(table, ACCENT);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(BORDER_CLR, 1));
        scroll.getViewport().setBackground(BG_DEEP);

        JPanel btnPanel = new JPanel(new BorderLayout(6, 6));
        btnPanel.setBackground(BG_PANEL);
        btnPanel.setBorder(new EmptyBorder(4, 0, 0, 0));

        // FIX 1 — Kick button uses handler.kick("kicked")
        JButton btnKick = styledButton("⚡ Kick User", DANGER);
        btnKick.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                String user = (String) sessionsTableModel.getValueAt(row, 0);
                kickUser(user, "kicked");
            }
        });

        txtBroadcast = new JTextField("Type broadcast message...");
        styleField(txtBroadcast);
        txtBroadcast.setForeground(FG_DIM);
        txtBroadcast.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (txtBroadcast.getText().equals("Type broadcast message...")) {
                    txtBroadcast.setText("");
                    txtBroadcast.setForeground(FG_TEXT);
                }
            }
        });

        JButton btnBroadcast = styledButton("📢 Broadcast", ACCENT2);
        btnBroadcast.addActionListener(e -> sendBroadcast());

        JPanel broadcastRow = new JPanel(new BorderLayout(4, 4));
        broadcastRow.setBackground(BG_PANEL);
        broadcastRow.add(txtBroadcast, BorderLayout.CENTER);
        broadcastRow.add(btnBroadcast, BorderLayout.EAST);

        btnPanel.add(btnKick,      BorderLayout.WEST);
        btnPanel.add(broadcastRow, BorderLayout.CENTER);

        panel.add(scroll,   BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ── User management panel ─────────────────────────────────────────────────
    private JPanel buildUserManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG_PANEL);
        panel.setBorder(styledBorder("USER MANAGEMENT", WARN));

        JPanel inputSection = new JPanel();
        inputSection.setLayout(new BoxLayout(inputSection, BoxLayout.Y_AXIS));
        inputSection.setBackground(BG_PANEL);
        inputSection.setBorder(new EmptyBorder(8, 8, 8, 8));

        inputSection.add(dimLabel("Username:"));
        inputSection.add(Box.createRigidArea(new Dimension(0, 4)));
        txtNewUsername = new JTextField();
        txtNewUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        styleField(txtNewUsername);
        inputSection.add(txtNewUsername);
        inputSection.add(Box.createRigidArea(new Dimension(0, 8)));

        inputSection.add(dimLabel("Password:"));
        inputSection.add(Box.createRigidArea(new Dimension(0, 4)));
        txtNewPassword = new JPasswordField();
        txtNewPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        stylePasswordField(txtNewPassword);
        inputSection.add(txtNewPassword);
        inputSection.add(Box.createRigidArea(new Dimension(0, 12)));

        JButton btnCreate = styledButton("＋ Create User", WARN);
        btnCreate.setForeground(BG_DEEP);
        btnCreate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnCreate.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnCreate.addActionListener(e -> createUser());
        inputSection.add(btnCreate);

        existingUsersModel = new DefaultListModel<>();
        usersList = new JList<>(existingUsersModel);
        usersList.setCellRenderer(new UserListCellRenderer());
        usersList.setBackground(BG_DEEP);
        usersList.setForeground(FG_TEXT);
        usersList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        usersList.setSelectionBackground(WARN.darker().darker());
        usersList.setSelectionForeground(WARN);

        JScrollPane usersScroll = new JScrollPane(usersList);
        usersScroll.setBorder(styledBorder("Existing Users", BORDER_CLR));
        usersScroll.getViewport().setBackground(BG_DEEP);

        JPanel listActions = new JPanel(new GridLayout(1, 2, 6, 6));
        listActions.setBackground(BG_PANEL);
        listActions.setBorder(new EmptyBorder(4, 0, 0, 0));

        JButton btnDelete = styledButton("🗑 Delete", DANGER);
        JButton btnReset  = styledButton("🔑 Reset PW", ACCENT2);

        // FIX 1 — Delete also kicks the user if currently online
        btnDelete.addActionListener(e -> {
            String selected = usersList.getSelectedValue();
            if (selected != null) {
                // kick first if online so they can't keep chatting
                if (ChatServer.onlineUsers.containsKey(selected)) {
                    kickUser(selected, "deleted");
                }
                existingUsersModel.removeElement(selected);
                removeUserFromFile(selected);
                addLog("User deleted: " + selected);
            }
        });

        btnReset.addActionListener(e -> {
            String selected = usersList.getSelectedValue();
            if (selected != null) {
                updateUserPassword(selected, "password");
                JOptionPane.showMessageDialog(this, "Password reset for: " + selected);
                addLog("Password reset: " + selected);
            }
        });

        listActions.add(btnDelete);
        listActions.add(btnReset);

        JPanel listSection = new JPanel(new BorderLayout(4, 4));
        listSection.setBackground(BG_PANEL);
        listSection.add(usersScroll, BorderLayout.CENTER);
        listSection.add(listActions, BorderLayout.SOUTH);

        panel.add(inputSection, BorderLayout.NORTH);
        panel.add(listSection,  BorderLayout.CENTER);
        return panel;
    }

    // ── Mailbox panel ─────────────────────────────────────────────────────────
    private JPanel buildMailboxPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(BG_PANEL);
        panel.setBorder(styledBorder("MAILBOX STATISTICS (Real-Time)", ACCENT2));

        String[] cols = {"User", "Inbox", "Sent", "Archive Size"};
        mailboxTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable mailTable = new JTable(mailboxTableModel);
        styleTable(mailTable, ACCENT2);

        JScrollPane scroll = new JScrollPane(mailTable);
        scroll.setBorder(new LineBorder(BORDER_CLR, 1));
        scroll.getViewport().setBackground(BG_DEEP);

        JButton btnForceCleanup = styledButton("🧹 Force Cleanup (Archive > 30 days)", DANGER);
        btnForceCleanup.addActionListener(e -> {
            addLog("Force cleanup executed.");
            JOptionPane.showMessageDialog(this, "Archive cleanup done!");
        });

        JPanel settingsPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        settingsPanel.setBackground(BG_PANEL);
        settingsPanel.setBorder(styledBorder("SYSTEM SETTINGS", BORDER_CLR));

        String[] sizes = {"16 KB", "32 KB", "64 KB", "128 KB"};
        maxMsgSizeCombo = new JComboBox<>(sizes);
        maxMsgSizeCombo.setSelectedItem("64 KB");
        maxMsgSizeCombo.setBackground(BG_CARD);
        maxMsgSizeCombo.setForeground(FG_TEXT);
        maxMsgSizeCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel sizeRow = new JPanel(new BorderLayout(4, 4));
        sizeRow.setBackground(BG_PANEL);
        sizeRow.add(dimLabel("Max Message Size:"), BorderLayout.WEST);
        sizeRow.add(maxMsgSizeCombo,               BorderLayout.CENTER);

        JButton btnApply = styledButton("✔ Apply Settings", ACCENT);
        btnApply.addActionListener(e -> {
            addLog("Settings applied. Max size: " + maxMsgSizeCombo.getSelectedItem());
            JOptionPane.showMessageDialog(this, "Settings saved!");
        });

        settingsPanel.add(sizeRow);
        settingsPanel.add(btnApply);

        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.setBackground(BG_PANEL);
        south.add(btnForceCleanup, BorderLayout.NORTH);
        south.add(settingsPanel,   BorderLayout.CENTER);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(south,  BorderLayout.SOUTH);
        return panel;
    }

    // ── Log panel ─────────────────────────────────────────────────────────────
    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(BG_DEEP);
        panel.setBorder(new CompoundBorder(
                styledBorder("SYSTEM LOGS (Live Stream)", FG_DIM),
                new EmptyBorder(2, 2, 2, 2)));
        panel.setPreferredSize(new Dimension(0, 160));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(new Color(12, 16, 26));
        logArea.setForeground(ACCENT);
        logArea.setCaretColor(ACCENT);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new LineBorder(BORDER_CLR, 1));
        scroll.getViewport().setBackground(new Color(12, 16, 26));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnPanel.setBackground(BG_DEEP);
        JButton btnClear = styledButton("Clear Logs", new Color(60, 75, 105));
        JButton btnSave  = styledButton("💾 Save Logs", ACCENT2);
        btnClear.addActionListener(e -> logArea.setText(""));
        btnSave.addActionListener(e -> saveLogs());
        btnPanel.add(btnClear);
        btnPanel.add(btnSave);

        panel.add(scroll,   BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ── Server logic ──────────────────────────────────────────────────────────
    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                addLog("INFO: Server started on TCP:" + PORT);

                ChatServer.rooms.put("General",  new ChatRoom("General"));
                ChatServer.rooms.put("Networks", new ChatRoom("Networks"));
                ChatServer.rooms.put("Java",     new ChatRoom("Java"));

                java.util.concurrent.ExecutorService pool =
                        java.util.concurrent.Executors.newCachedThreadPool();

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    String ip = clientSocket.getInetAddress().getHostAddress();
                    addLog("AUTH: New connection from " + ip);
                    pool.execute(new ClientHandler(clientSocket, this));
                }
            } catch (IOException e) {
                if (isRunning) addLog("ERROR: " + e.getMessage());
            }
        }).start();
    }

    private void startAutoRefresh() {
        long startTime = System.currentTimeMillis();
        refreshTimer = new javax.swing.Timer(1000, e -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            String uptime = String.format("%02d:%02d:%02d",
                    elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60);
            lblServerStatus.setText(
                    "SERVER STATUS: ONLINE  |  PORT (TCP): " + PORT + "  |  Uptime: " + uptime);
            refreshSessions();
            refreshMailbox();
        });
        refreshTimer.start();
    }

    private void refreshSessions() {
        sessionsTableModel.setRowCount(0);
        for (Map.Entry<String, ClientHandler> entry : ChatServer.onlineUsers.entrySet()) {
            sessionsTableModel.addRow(new Object[]{
                    entry.getKey(), "ACTIVE", entry.getValue().getClientIP()
            });
        }
        if (usersList != null) usersList.repaint();
    }

    private void refreshMailbox() {
        mailboxTableModel.setRowCount(0);
        for (Map.Entry<String, ClientHandler> entry : ChatServer.onlineUsers.entrySet()) {
            mailboxTableModel.addRow(new Object[]{
                    entry.getKey(),
                    entry.getValue().getInboxCount(),
                    entry.getValue().getSentCount(),
                    "0 KB"
            });
        }
    }

    private void createUser() {
        String username = txtNewUsername.getText().trim();
        String password = new String(txtNewPassword.getPassword()).trim();
        if (username.isEmpty()) { JOptionPane.showMessageDialog(this, "Username cannot be empty!"); return; }
        if (password.isEmpty()) { JOptionPane.showMessageDialog(this, "Password cannot be empty!"); return; }
        if (existingUsersModel.contains(username)) { JOptionPane.showMessageDialog(this, "User already exists: " + username); return; }
        existingUsersModel.addElement(username);
        saveUserToFile(username, password);
        txtNewUsername.setText("");
        txtNewPassword.setText("");
        addLog("New user created: " + username);
    }

    /**
     * FIX 1 — Unified kick method.
     * reason = "kicked" (admin action) | "deleted" (account removed).
     * Uses ClientHandler.kick() which sends the KICKED command BEFORE closing
     * the socket, giving the client time to read and react.
     */
    private void kickUser(String username, String reason) {
        ClientHandler handler = ChatServer.onlineUsers.get(username);
        if (handler != null) {
            handler.kick(reason);
            addLog("KICK (" + reason + "): " + username);
        }
    }

    private void sendBroadcast() {
        String msg = txtBroadcast.getText().trim();
        if (msg.isEmpty() || msg.equals("Type broadcast message...")) return;
        for (ClientHandler client : ChatServer.onlineUsers.values()) {
            client.sendMessage("BROADCAST [SERVER]: " + msg);
        }
        addLog("BROADCAST sent: " + msg);
        txtBroadcast.setText("");
    }

    private void saveLogs() {
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("server_logs.txt"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                FileWriter fw = new FileWriter(chooser.getSelectedFile());
                fw.write(logArea.getText());
                fw.close();
                JOptionPane.showMessageDialog(this, "Logs saved successfully!");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving logs: " + e.getMessage());
        }
    }

    public void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    private void loadUsersFromFile() {
        if (!usersFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) continue;
                String username = line.split(":", 2)[0];
                if (!existingUsersModel.contains(username)) existingUsersModel.addElement(username);
            }
            addLog("Loaded users from " + usersFile.getAbsolutePath());
        } catch (IOException e) {
            addLog("Failed to load users: " + e.getMessage());
        }
    }

    private synchronized void saveUserToFile(String username, String password) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(usersFile, true)))) {
            out.println(username + ":" + password);
            addLog("Persisted user: " + username);
        } catch (IOException e) {
            addLog("Error saving user: " + e.getMessage());
        }
    }

    private synchronized void removeUserFromFile(String username) {
        if (!usersFile.exists()) return;
        File temp = new File(usersFile.getAbsolutePath() + ".tmp");
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile));
             PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (!line.split(":", 2)[0].equals(username)) pw.println(line);
            }
        } catch (IOException e) { addLog("Error removing user: " + e.getMessage()); return; }
        if (!usersFile.delete())     { addLog("Failed to delete users file"); return; }
        if (!temp.renameTo(usersFile)) addLog("Failed to rename temp file");
        else addLog("Removed user from file: " + username);
    }

    private synchronized void updateUserPassword(String username, String newPassword) {
        if (!usersFile.exists()) return;
        File temp = new File(usersFile.getAbsolutePath() + ".tmp");
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile));
             PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
            String line; boolean found = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(":", 2);
                if (p[0].equals(username)) { pw.println(username + ":" + newPassword); found = true; }
                else pw.println(line);
            }
            if (!found) pw.println(username + ":" + newPassword);
        } catch (IOException e) { addLog("Error updating password: " + e.getMessage()); return; }
        if (!usersFile.delete())     { addLog("Failed to delete users file"); return; }
        if (!temp.renameTo(usersFile)) addLog("Failed to rename temp file");
        else addLog("Updated password for: " + username);
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

    private void styleTable(JTable table, Color accentColor) {
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.setBackground(BG_DEEP);
        table.setForeground(FG_TEXT);
        table.setGridColor(BORDER_CLR);
        table.setSelectionBackground(accentColor.darker().darker());
        table.setSelectionForeground(accentColor);
        table.getTableHeader().setBackground(BG_CARD);
        table.getTableHeader().setForeground(accentColor);
        table.getTableHeader().setFont(new Font("Monospaced", Font.BOLD, 11));
    }

    private void styleField(JTextField field) {
        field.setBackground(BG_CARD);
        field.setForeground(FG_TEXT);
        field.setCaretColor(ACCENT);
        field.setFont(new Font("Monospaced", Font.PLAIN, 12));
        field.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1), new EmptyBorder(4, 8, 4, 8)));
    }

    private void stylePasswordField(JPasswordField pf) {
        pf.setBackground(BG_CARD);
        pf.setForeground(FG_TEXT);
        pf.setCaretColor(ACCENT);
        pf.setFont(new Font("Monospaced", Font.PLAIN, 12));
        pf.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1), new EmptyBorder(4, 8, 4, 8)));
    }

    private JLabel dimLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(FG_DIM);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    // ── User list renderer ────────────────────────────────────────────────────
    private static class CircleIcon implements Icon {
        private final int size; private final Color color; private final boolean filled;
        CircleIcon(int s, Color c, boolean f) { size=s; color=c; filled=f; }
        public int getIconWidth()  { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            if (filled) g2.fillOval(x, y, size, size);
            else        g2.drawOval(x, y, size, size);
            g2.dispose();
        }
    }

    private class UserListCellRenderer extends JLabel implements ListCellRenderer<String> {
        private final Icon green   = new CircleIcon(10, ACCENT, true);
        private final Icon offline = new CircleIcon(10, FG_DIM,  false);
        public UserListCellRenderer() { setOpaque(true); setBorder(BorderFactory.createEmptyBorder(2,4,2,4)); setFont(new Font("Monospaced", Font.PLAIN, 12)); }
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            setText("  " + value);
            setIcon(ChatServer.onlineUsers.containsKey(value) ? green : offline);
            if (isSelected) { setBackground(WARN.darker().darker()); setForeground(WARN); }
            else            { setBackground(BG_DEEP);                 setForeground(FG_TEXT); }
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServerConsoleGUI::new);
    }
}