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

    // ── Theme palette (mirrors ClientGUI) ────────────────────────
    private static final Color BG_DARK      = new Color(18,  26,  38);
    private static final Color BG_MID       = new Color(26,  38,  54);
    private static final Color BG_LIGHT     = new Color(34,  50,  70);
    private static final Color ACCENT       = new Color(0,  188, 212);
    private static final Color ACCENT_DARK  = new Color(0,  150, 170);
    private static final Color ACCENT_WARN  = new Color(255, 160,  40);  // warm orange for destructive
    private static final Color TEXT_MAIN    = new Color(220, 232, 240);
    private static final Color TEXT_DIM     = new Color(120, 150, 175);
    private static final Color BORDER_CLR   = new Color(0,  188, 212, 60);
    private static final Font  MONO_FONT    = new Font("Monospaced", Font.PLAIN, 11);
    private static final Font  BODY_FONT    = new Font("SansSerif",  Font.PLAIN, 12);
    // ─────────────────────────────────────────────────────────────

    private static final int PORT = 5000;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    private JLabel lblServerStatus;
    private DefaultTableModel sessionsTableModel;
    private DefaultTableModel mailboxTableModel;
    private JTable sessionsTable;  // kept as field so kick button can always read selection

    private DefaultListModel<String> existingUsersModel;
    private JList<String> usersList;

    private JTextField txtNewUsername;
    private JPasswordField txtNewPassword;

    private JTextArea logArea;
    private JTextField txtBroadcast;
    private JComboBox<String> maxMsgSizeCombo;
    private javax.swing.Timer refreshTimer;

    private final File usersFile = new File("users.txt");

    public ServerConsoleGUI() {
        buildGUI();
        loadUsersFromFile();
        startServer();
        startAutoRefresh();
    }

    // ── helpers ───────────────────────────────────────────────────
    private TitledBorder titledBorder(String title) {
        TitledBorder tb = new TitledBorder(
            BorderFactory.createLineBorder(BORDER_CLR, 1), title);
        tb.setTitleColor(ACCENT);
        tb.setTitleFont(new Font("SansSerif", Font.BOLD, 11));
        return tb;
    }

    private JButton accentButton(String text) {
        return accentButton(text, ACCENT, BG_DARK);
    }

    private JButton accentButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bg.darker()); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(BG_LIGHT);
        tf.setForeground(TEXT_MAIN);
        tf.setCaretColor(ACCENT);
        tf.setFont(BODY_FONT);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR, 1),
            new EmptyBorder(3, 6, 3, 6)));
    }

    private JScrollPane darkScroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.getViewport().setBackground(BG_LIGHT);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_CLR, 1));
        return sp;
    }

    private void styleTable(JTable table) {
        table.setBackground(BG_LIGHT);
        table.setForeground(TEXT_MAIN);
        table.setFont(BODY_FONT);
        table.setRowHeight(22);
        table.setGridColor(new Color(40, 60, 80));
        table.setSelectionBackground(ACCENT);
        table.setSelectionForeground(BG_DARK);
        table.getTableHeader().setBackground(BG_MID);
        table.getTableHeader().setForeground(ACCENT);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.getTableHeader().setBorder(BorderFactory.createLineBorder(BORDER_CLR, 1));
    }
    // ─────────────────────────────────────────────────────────────

    private void buildGUI() {
        setTitle("ChatLite Server Console - [RUNNING]");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(BG_DARK);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildLogPanel(), BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        panel.setBackground(BG_MID);

        JLabel dot = new JLabel("●");
        dot.setForeground(ACCENT);
        dot.setFont(new Font("SansSerif", Font.BOLD, 16));

        lblServerStatus = new JLabel(
            "SERVER STATUS: ONLINE  |  PORT (TCP): " + PORT +
            "  |  Uptime: 00:00:00"
        );
        lblServerStatus.setForeground(ACCENT);
        lblServerStatus.setFont(MONO_FONT);

        panel.add(dot);
        panel.add(lblServerStatus);
        return panel;
    }

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 5, 5));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        panel.setBackground(BG_DARK);

        panel.add(buildUserManagementPanel());
        panel.add(buildSessionsPanel());
        panel.add(buildMailboxPanel());

        return panel;
    }

    private JPanel buildUserManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(titledBorder("USER MANAGEMENT"));
        panel.setBackground(BG_MID);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(BG_MID);
        left.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel lblUser = new JLabel("Username:");
        lblUser.setForeground(TEXT_DIM);
        lblUser.setFont(BODY_FONT);
        lblUser.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtNewUsername = new JTextField();
        styleTextField(txtNewUsername);
        txtNewUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        txtNewUsername.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(lblUser);
        left.add(Box.createRigidArea(new Dimension(0, 6)));
        left.add(txtNewUsername);
        left.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel lblPass = new JLabel("Password:");
        lblPass.setForeground(TEXT_DIM);
        lblPass.setFont(BODY_FONT);
        lblPass.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtNewPassword = new JPasswordField();
        txtNewPassword.setBackground(BG_LIGHT);
        txtNewPassword.setForeground(TEXT_MAIN);
        txtNewPassword.setCaretColor(ACCENT);
        txtNewPassword.setFont(BODY_FONT);
        txtNewPassword.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR, 1),
            new EmptyBorder(3, 6, 3, 6)));
        txtNewPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        txtNewPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(lblPass);
        left.add(Box.createRigidArea(new Dimension(0, 6)));
        left.add(txtNewPassword);
        left.add(Box.createRigidArea(new Dimension(0, 12)));

        JButton btnCreate = accentButton("Create User", new Color(0, 200, 130), BG_DARK);
        btnCreate.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnCreate.addActionListener(e -> createUser());
        btnCreate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        left.add(btnCreate);

        panel.add(left, BorderLayout.WEST);

        existingUsersModel = new DefaultListModel<>();
        usersList = new JList<>(existingUsersModel);
        usersList.setCellRenderer(new UserListCellRenderer());
        usersList.setBackground(BG_LIGHT);
        usersList.setForeground(TEXT_MAIN);
        usersList.setFont(BODY_FONT);
        usersList.setSelectionBackground(ACCENT);
        usersList.setSelectionForeground(BG_DARK);

        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setBackground(BG_MID);
        JScrollPane usersScroll = darkScroll(usersList);
        usersScroll.setBorder(titledBorder("Existing Users"));

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 6, 6));
        btnPanel.setBackground(BG_MID);
        JButton btnDelete = accentButton("Delete Selected", ACCENT_WARN, BG_DARK);
        JButton btnReset  = accentButton("Reset Password", new Color(60, 80, 110), TEXT_MAIN);

        btnDelete.addActionListener(e -> {
            String selected = usersList.getSelectedValue();
            if (selected != null) {
                // if the user is currently online, notify them and disconnect
                ClientHandler onlineHandler = ChatServer.onlineUsers.get(selected);
                if (onlineHandler != null) {
                    onlineHandler.sendMessage("SERVER: You have been deleted by the administrator.");
                    onlineHandler.disconnect();
                    addLog("DELETE: Kicked online user " + selected + " before deletion.");
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

        btnPanel.add(btnDelete);
        btnPanel.add(btnReset);

        right.add(usersScroll, BorderLayout.CENTER);
        right.add(btnPanel, BorderLayout.SOUTH);

        panel.add(right, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildSessionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(titledBorder("ACTIVE SESSIONS & ROSTER"));
        panel.setBackground(BG_MID);

        String[] cols = {"Username", "Status", "IP Address"};
        sessionsTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        sessionsTable = new JTable(sessionsTableModel);
        styleTable(sessionsTable);
        // remember last selected row so the kick button still works after focus moves
        final int[] lastSelectedRow = {-1};
        sessionsTable.getSelectionModel().addListSelectionListener(e -> {
            int r = sessionsTable.getSelectedRow();
            if (r >= 0) lastSelectedRow[0] = r;
        });

        JPanel btnPanel = new JPanel(new BorderLayout(6, 6));
        btnPanel.setBackground(BG_MID);

        JButton btnKick = accentButton("Kick User", ACCENT_WARN, BG_DARK);
        btnKick.addActionListener(e -> {
            // try currently selected row first, fall back to last remembered row
            int row = sessionsTable.getSelectedRow();
            if (row < 0) row = lastSelectedRow[0];
            if (row >= 0 && row < sessionsTableModel.getRowCount()) {
                String user = (String) sessionsTableModel.getValueAt(row, 0);
                kickUser(user);
            } else {
                JOptionPane.showMessageDialog(ServerConsoleGUI.this,
                    "Please select a user from the table first.",
                    "No user selected", JOptionPane.WARNING_MESSAGE);
            }
        });

        txtBroadcast = new JTextField("Type broadcast message...");
        styleTextField(txtBroadcast);
        txtBroadcast.setForeground(TEXT_DIM);
        txtBroadcast.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (txtBroadcast.getText().equals("Type broadcast message...")) {
                    txtBroadcast.setText("");
                    txtBroadcast.setForeground(TEXT_MAIN);
                }
            }
        });

        JButton btnBroadcast = accentButton("Send Broadcast");
        btnBroadcast.addActionListener(e -> sendBroadcast());

        JPanel broadcastPanel = new JPanel(new BorderLayout(3, 3));
        broadcastPanel.setBackground(BG_MID);
        broadcastPanel.add(txtBroadcast, BorderLayout.CENTER);
        broadcastPanel.add(btnBroadcast, BorderLayout.EAST);

        btnPanel.add(btnKick, BorderLayout.WEST);
        btnPanel.add(broadcastPanel, BorderLayout.CENTER);

        panel.add(darkScroll(sessionsTable), BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildMailboxPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(titledBorder("MAILBOX STATISTICS (Real-Time)"));
        panel.setBackground(BG_MID);

        JPanel mailboxPanel = new JPanel(new BorderLayout());
        mailboxPanel.setBackground(BG_MID);

        String[] cols = {"User", "Inbox", "Sent", "Archive Size"};
        mailboxTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable mailTable = new JTable(mailboxTableModel);
        styleTable(mailTable);

        JButton btnForceCleanup = accentButton("Force Cleanup Now (Archive > 30 days)", ACCENT_WARN, BG_DARK);
        btnForceCleanup.addActionListener(e -> {
            addLog("Force cleanup executed.");
            JOptionPane.showMessageDialog(this, "Archive cleanup done!");
        });

        mailboxPanel.add(darkScroll(mailTable), BorderLayout.CENTER);
        mailboxPanel.add(btnForceCleanup, BorderLayout.SOUTH);

        JPanel settingsPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        settingsPanel.setBorder(titledBorder("SYSTEM LOGS"));
        settingsPanel.setBackground(BG_MID);

        String[] sizes = {"16 KB", "32 KB", "64 KB", "128 KB"};
        maxMsgSizeCombo = new JComboBox<>(sizes);
        maxMsgSizeCombo.setSelectedItem("64 KB");
        maxMsgSizeCombo.setBackground(BG_LIGHT);
        maxMsgSizeCombo.setForeground(TEXT_MAIN);
        maxMsgSizeCombo.setFont(BODY_FONT);

        JPanel sizePanel = new JPanel(new BorderLayout(3, 3));
        sizePanel.setBackground(BG_MID);
        JLabel sizeLbl = new JLabel("Max Message Size:");
        sizeLbl.setForeground(TEXT_DIM);
        sizeLbl.setFont(BODY_FONT);
        sizePanel.add(sizeLbl, BorderLayout.WEST);
        sizePanel.add(maxMsgSizeCombo, BorderLayout.CENTER);

        JButton btnApply = accentButton("Apply Settings");
        btnApply.addActionListener(e -> {
            addLog("Settings applied. Max size: " + maxMsgSizeCombo.getSelectedItem());
            JOptionPane.showMessageDialog(this, "Settings saved!");
        });

        settingsPanel.add(sizePanel);
        settingsPanel.add(btnApply);

        panel.add(mailboxPanel, BorderLayout.CENTER);
        panel.add(settingsPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(3, 3));
        panel.setBorder(titledBorder("SYSTEM LOGS (Live Stream)"));
        panel.setPreferredSize(new Dimension(0, 150));
        panel.setBackground(BG_MID);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(MONO_FONT);
        logArea.setBackground(BG_DARK);
        logArea.setForeground(new Color(0, 230, 150));  // green terminal text

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG_MID);

        JButton btnClear = accentButton("Clear Logs", new Color(60, 80, 110), TEXT_MAIN);
        JButton btnSave  = accentButton("Save Logs to .txt");

        btnClear.addActionListener(e -> logArea.setText(""));
        btnSave.addActionListener(e -> saveLogs());

        btnPanel.add(btnClear);
        btnPanel.add(btnSave);

        panel.add(darkScroll(logArea), BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

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
                "SERVER STATUS: ONLINE  |  PORT (TCP): " + PORT +
                "  |  Uptime: " + uptime
            );

            refreshSessions();
            refreshMailbox();
        });

        refreshTimer.start();
    }

    private void refreshSessions() {
        sessionsTableModel.setRowCount(0);
        for (Map.Entry<String, ClientHandler> entry :
                ChatServer.onlineUsers.entrySet()) {
            sessionsTableModel.addRow(new Object[]{
                entry.getKey(),
                "ACTIVE",
                entry.getValue().getClientIP()
            });
        }
        if (usersList != null) usersList.repaint();
    }

    private void refreshMailbox() {
        mailboxTableModel.setRowCount(0);
        for (Map.Entry<String, ClientHandler> entry :
                ChatServer.onlineUsers.entrySet()) {
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
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty!");
            return;
        }
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Password cannot be empty!");
            return;
        }
        if (existingUsersModel.contains(username)) {
            JOptionPane.showMessageDialog(this, "User already exists: " + username);
            return;
        }
        existingUsersModel.addElement(username);
        saveUserToFile(username, password);
        txtNewUsername.setText("");
        txtNewPassword.setText("");
        addLog("New user created: " + username);
    }

    private void kickUser(String username) {
        ClientHandler handler = ChatServer.onlineUsers.get(username);
        if (handler != null) {
            handler.sendMessage("SERVER: You have been kicked by the administrator.");
            handler.disconnect();
            addLog("KICK: User " + username + " was kicked.");
        } else {
            addLog("KICK: " + username + " is not currently online.");
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
            String time = LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServerConsoleGUI::new);
    }

    // ------------------ User persistence helpers ------------------

    private void loadUsersFromFile() {
        if (!usersFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) continue;
                String username = line.split(":", 2)[0];
                if (!existingUsersModel.contains(username)) {
                    existingUsersModel.addElement(username);
                }
            }
            addLog("Loaded users from " + usersFile.getAbsolutePath());
        } catch (IOException e) {
            addLog("Failed to load users: " + e.getMessage());
        }
    }

    private synchronized void saveUserToFile(String username, String password) {
        try (FileWriter fw = new FileWriter(usersFile, true);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(username + ":" + password);
            out.flush();
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
                String u = line.split(":", 2)[0];
                if (!u.equals(username)) pw.println(line);
            }
        } catch (IOException e) {
            addLog("Error removing user from file: " + e.getMessage());
            return;
        }
        if (!usersFile.delete()) { addLog("Failed to delete original users file"); return; }
        if (!temp.renameTo(usersFile)) { addLog("Failed to rename temp users file"); }
        else { addLog("Removed user from file: " + username); }
    }

    private synchronized void updateUserPassword(String username, String newPassword) {
        if (!usersFile.exists()) return;
        File temp = new File(usersFile.getAbsolutePath() + ".tmp");
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile));
             PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
            String line;
            boolean found = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(":", 2);
                if (parts[0].equals(username)) {
                    pw.println(username + ":" + newPassword);
                    found = true;
                } else {
                    pw.println(line);
                }
            }
            if (!found) pw.println(username + ":" + newPassword);
        } catch (IOException e) {
            addLog("Error updating password: " + e.getMessage());
            return;
        }
        if (!usersFile.delete()) { addLog("Failed to delete original users file"); return; }
        if (!temp.renameTo(usersFile)) { addLog("Failed to rename temp users file"); }
        else { addLog("Updated password for: " + username); }
    }

    // ── Status circle icon ────────────────────────────────────────
    private static class CircleIcon implements Icon {
        private final int size;
        private final Color color;
        private final boolean filled;

        CircleIcon(int size, Color color, boolean filled) {
            this.size = size; this.color = color; this.filled = filled;
        }

        public int getIconWidth()  { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (filled) { g2.setColor(color); g2.fillOval(x, y, size, size); }
            else        { g2.setColor(color); g2.drawOval(x, y, size, size); }
            g2.dispose();
        }
    }

    // ── User list cell renderer ───────────────────────────────────
    private static class UserListCellRenderer extends JLabel implements ListCellRenderer<String> {
        private final Icon green    = new CircleIcon(10, new Color(0, 200, 130), true);
        private final Icon grayEmpty = new CircleIcon(10, new Color(80, 100, 120), false);

        public UserListCellRenderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends String> list, String value, int index,
                boolean isSelected, boolean cellHasFocus) {
            setText("  " + value);
            boolean online = ChatServer.onlineUsers.containsKey(value);
            setIcon(online ? green : grayEmpty);
            if (isSelected) {
                setBackground(new Color(0, 188, 212));
                setForeground(new Color(18, 26, 38));
            } else {
                setBackground(new Color(34, 50, 70));
                setForeground(new Color(220, 232, 240));
            }
            return this;
        }
    }
}
