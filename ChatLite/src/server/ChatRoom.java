package server;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatRoom {

    private String name;

    
    private List<ClientHandler> members = new CopyOnWriteArrayList<>();

    public ChatRoom(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    
    public void addMember(ClientHandler client) {
        members.add(client);
    }

    
    public void removeMember(ClientHandler client) {
        members.remove(client);
    }

    
    public void broadcast(String message, ClientHandler sender) {
        for (ClientHandler member : members) {
            
            if (member != sender) {
                member.sendMessage(message);
            }
        }
    }

    public List<ClientHandler> getMembers() {
        return members;
    }
}
