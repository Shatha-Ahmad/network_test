package server;

import java.io.*;
import java.util.*;

/**
 * Persists chat room messages to disk.
 * Each room gets its own file:  chats/<roomName>.txt
 * On JOIN the last MAX_HISTORY lines are replayed to the new member.
 */
public class ChatHistory {

    private static final String DIR         = "chats/";
    private static final int    MAX_HISTORY = 50;   // lines sent on JOIN

    static {
        // make sure the chats/ folder exists when the class is first loaded
        new File(DIR).mkdirs();
    }

    /** Append one line to the room's log file. Thread-safe. */
    public static synchronized void save(String roomName, String line) {
        File file = new File(DIR + sanitize(roomName) + ".txt");
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {
            pw.println(line);
        } catch (IOException e) {
            System.err.println("[ChatHistory] Could not save message for room " + roomName + ": " + e.getMessage());
        }
    }

    /**
     * Return the last MAX_HISTORY lines from the room's log file.
     * Returns an empty list if the file does not exist yet.
     */
    public static List<String> load(String roomName) {
        File file = new File(DIR + sanitize(roomName) + ".txt");
        if (!file.exists()) return Collections.emptyList();

        List<String> all = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) all.add(line);
            }
        } catch (IOException e) {
            System.err.println("[ChatHistory] Could not load history for room " + roomName + ": " + e.getMessage());
        }

        // return last MAX_HISTORY lines only
        int from = Math.max(0, all.size() - MAX_HISTORY);
        return all.subList(from, all.size());
    }

    /** Strip characters that are unsafe in file names. */
    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}