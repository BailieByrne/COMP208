package COMP208.client.clientv1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client {


    // debug flag - set to false to disable debug prints
    public static final boolean debug = true;
    // helper method for debug printing
    public static void print(String s) {
        if (debug) {
            System.out.println(s);
        }
        
    }

    // example classes to demonstrate JSON parsing
    public static class User {
        public String username;
        public int score;
    }

    public static class Settings {
        public boolean fullscreen;
        public boolean lightmode;
        public int volume;
    }

    public static User user;
    public static Settings settings;


    public static void loadConfigs() {
        print("Attempting to load configs");
        try {
            ObjectMapper mapper = new ObjectMapper();
            Path configPath = Paths.get("client", "clientv1", "configs.json");
            if (!Files.exists(configPath)) {
                throw new IllegalArgumentException("config file not found: " + configPath.toAbsolutePath());
            }

            try (InputStream in = Files.newInputStream(configPath)) {
                JsonNode root = mapper.readTree(in);
                user = mapper.treeToValue(root.path("User"), User.class);
                settings = mapper.treeToValue(root.path("Settings"), Settings.class);
                print("Configs loaded successfully");
                print("User: " + user.username + ", score: " + user.score);
                print("Settings: fullscreen=" + settings.fullscreen + ", lightmode=" + settings.lightmode + ", volume=" + settings.volume);
            }

        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            e.printStackTrace();

        }
    }



    public static void main(String[] args) {
        print("Running Client");
        loadConfigs();
    }
}
