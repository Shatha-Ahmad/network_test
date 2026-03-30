package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

public class ChatServer {

    private static final int PORT = 5000;

  
    public static Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    
    public static Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        
        SwingUtilities.invokeLater(ServerConsoleGUI::new);
    }
}
