package COMP208.client.clientv1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

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
    public static class Config {
        public String username;
        public int score;
        public boolean fullscreen;
        public boolean lightmode;
        public int volume;
    }

    public static Config config;



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
                config = mapper.treeToValue(root, Config.class);
                print("Configs loaded successfully (values below):");
                printConfigs();
            }

        } catch (Exception e) {
            print("Error parsing JSON: " + e.getMessage());
            e.printStackTrace();

        }
    }

    public static void printConfigs() {
        print("Current configs:");
        for (Field field : Config.class.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(config);
                print(field.getName() + ": " + value);
            } catch (Exception e) {
                print("Error accessing config field: " + e.getMessage());
                e.printStackTrace();
            }
        }
        print("------------------------------");
    }

    public static void saveConfigs() {
        try {
            Path configPath = Paths.get("client", "clientv1", "configs.json");
            Files.createDirectories(configPath.getParent());
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
            print("Configs saved to " + configPath.toAbsolutePath());
        } catch (Exception e) {
            print("Error saving configs: " + e.getMessage());
        }
    }

    public static void updateConfig(String key, String newValue) {
        try {
            Field field = Config.class.getDeclaredField(key);
            field.setAccessible(true);
            boolean updated = false;
            if (field.getType() == int.class) {
                field.setInt(config, Integer.parseInt(newValue));
                updated = true;
            } else if (field.getType() == boolean.class) {
                field.setBoolean(config, Boolean.parseBoolean(newValue));
                updated = true;
            } else if (field.getType() == String.class) {
                field.set(config, newValue);
                updated = true;
            }
            print("Updated " + key + " to " + newValue);
            if (updated) {
                saveConfigs();
            }
        } catch (NoSuchFieldException e) {
            print("Invalid config key: " + key);
        } catch (Exception e) {
            print("Error updating config: " + e.getMessage());
        }
    }

    public static void defaultConfigs() {
        print("Resetting configs to default values");
        config = new Config();
        config.username = "Player1";
        config.score = 0;
        config.fullscreen = false;
        config.lightmode = true;
        config.volume = 50;
    }

    public static void Init() {
        int state = 1; //change this if you want the game to start from a different state - by default its the main menu as obviosuly the game should open onto the main menu
        while (true) {
            print("Main loop initialized");
            if (state == 0) {
                print("Main Loop exit");
                return;
            } else if (state == 1) {
                state = MainMenuInit();
            } else if (state == 2) {
                state = GameInit();
            } else if (state == 3) {
                state = SettingsMenuInit();
            }
        }
    }


    public static int MainMenuInit() {
        System.out.println("Project Belford");
        System.out.println("0 quit - 1 Main menu - 2 Game - 3 Settings");
        while (true) {
            print("Main menu initialized");
            Scanner myObj = new Scanner(System.in); 
            System.out.print("> ");
            int input = myObj.nextInt(); 
            if (input == 3) {
                SettingsMenuInit();
            } else {
                return input;
            }
            
        }
    }

    public static int GameInit() {
        while (true) {
            print("Game initialized");
            return 0;
        }
    }

    public static int SettingsMenuInit() {
        loadConfigs();
        System.out.println("Settings");
        System.out.println("0 return to main menu - 1 print current config values - 2 set config values to default - or variable name to change it (e.g. username)");
        while (true) {
            print("Settings initalized");
            Scanner myObj = new Scanner(System.in); 
            System.out.print("> ");
            String input = myObj.nextLine();
            if (input.equals("0")) {
                return 0;
            } else if (input.equals("1")) {
                printConfigs();
            } else if (input.equals("2")) {
                defaultConfigs();
                saveConfigs();
            } else {
                System.out.println("Enter new value for " + input);
                System.out.print("> ");
                updateConfig(input, myObj.nextLine());
            }
        }
    }


    public static void main(String[] args) {
        print("Running Client");
        loadConfigs();
        Init();
    }
}
