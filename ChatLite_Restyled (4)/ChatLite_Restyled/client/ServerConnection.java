package client;

import java.io.*;
import java.net.*;

public class ServerConnection {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ClientGUI gui;

    public ServerConnection(ClientGUI gui) {
        this.gui = gui;
    }

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(this::listenForMessages).start();
            return true;

        } catch (IOException e) {
            return false;
        }
    }

    private void listenForMessages() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                gui.handleServerMessage(line);
            }
        } catch (IOException e) {
            gui.handleServerMessage("DISCONNECTED");
        }
    }

    public void send(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {}
    }
}
