package server;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private ServerConsoleGUI console;
    private int inboxCount = 0;
    private int sentCount  = 0;
    private String clientIP;

    public ClientHandler(Socket socket, ServerConsoleGUI console) {
        this.socket  = socket;
        this.console = console;
        this.clientIP = socket.getInetAddress().getHostAddress();
    }

    public int    getInboxCount() { return inboxCount; }
    public int    getSentCount()  { return sentCount;  }
    public String getClientIP()   { return clientIP;   }

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                handleCommand(line.trim());
            }

        } catch (IOException e) {
            System.out.println("Client disconnected: " + username);
        } finally {
            disconnect();
        }
    }

    private void handleCommand(String line) {
        String[] parts = line.split(" ", 3);
        String command = parts[0].toUpperCase();

        switch (command) {

            case "HELLO":
                if (parts.length < 3) { out.println("400 ERROR Missing username/password"); return; }
                String reqUser = parts[1];
                String reqPass = parts[2];
                if (ChatServer.onlineUsers.containsKey(reqUser)) {
                    out.println("409 ALREADY LOGGED IN");
                    if (console != null) console.addLog("AUTH FAILED: already online -> " + reqUser + " from " + clientIP);
                    disconnect();
                    return;
                }
                boolean authorized = false;
                File usersFile = new File("users.txt");
                if (usersFile.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
                        String l;
                        while ((l = br.readLine()) != null) {
                            l = l.trim();
                            if (l.isEmpty() || !l.contains(":")) continue;
                            if (l.equals(reqUser + ":" + reqPass)) { authorized = true; break; }
                        }
                    } catch (IOException ioe) {
                        if (console != null) console.addLog("AUTH ERROR reading users file: " + ioe.getMessage());
                    }
                }
                if (!authorized) {
                    out.println("401 UNAUTHORIZED");
                    if (console != null) console.addLog("AUTH FAILED: wrong credentials -> " + reqUser + " from " + clientIP);
                    disconnect();
                    return;
                }
                username = reqUser;
                ChatServer.onlineUsers.put(username, this);
                out.println("200 WELCOME");
                if (console != null) console.addLog(username + " logged in from " + clientIP);
                System.out.println(username + " logged in.");
                break;

            case "JOIN":
                if (parts.length < 2) { out.println("400 ERROR Missing room"); return; }
                String roomName = parts[1];
                ChatRoom room = ChatServer.rooms.get(roomName);
                if (room == null) {
                    room = new ChatRoom(roomName);
                    ChatServer.rooms.put(roomName, room);
                }
                room.addMember(this);
                out.println("210 JOINED " + roomName);
                break;

            case "MSG":
                if (parts.length < 3) { out.println("400 ERROR"); return; }
                String targetRoom = parts[1];
                String message = parts[2];
                ChatRoom msgRoom = ChatServer.rooms.get(targetRoom);
                if (msgRoom != null) {
                    msgRoom.broadcast("ROOM " + targetRoom + " " + username + ": " + message, this);
                    out.println("211 SENT");
                }
                sentCount++;
                break;

            case "PM":
                if (parts.length < 3) { out.println("400 ERROR"); return; }
                String targetUser = parts[1];
                String pmMessage = parts[2];
                ClientHandler target = ChatServer.onlineUsers.get(targetUser);
                if (target != null) {
                    target.sendMessage("PM " + username + " " + pmMessage);
                    out.println("212 PRIVATE SENT");
                    target.inboxCount++;
                    sentCount++;
                } else {
                    out.println("404 USER NOT FOUND");
                }
                break;

            case "USERS":
                out.println("213 " + ChatServer.onlineUsers.size());
                for (String user : ChatServer.onlineUsers.keySet()) {
                    out.println("213U " + user);
                }
                out.println("213 END");
                break;

            case "ROOMS":
                for (String rName : ChatServer.rooms.keySet()) {
                    out.println("214 " + rName);
                }
                break;

            case "LEAVE":
                if (parts.length < 2) return;
                ChatRoom leaveRoom = ChatServer.rooms.get(parts[1]);
                if (leaveRoom != null) leaveRoom.removeMember(this);
                out.println("215 LEFT");
                break;

            case "QUIT":
                out.println("221 BYE");
                disconnect();
                break;

            default:
                out.println("400 UNKNOWN COMMAND");
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getUsername() {
        return username;
    }

    public void disconnect() {
        if (username != null) {
            ChatServer.onlineUsers.remove(username);
        }
        try { socket.close(); } catch (IOException e) {}
    }
}
