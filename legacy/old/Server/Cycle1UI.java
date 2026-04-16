import java.io.IOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Cycle1UI {
    //Default runnign true
    private static boolean running = true;

    //Packet constructors for UI interactions consider inlining these
    public static String buyPacketConstructor(String ticker, int quantity){
        return "BUY:" + ticker + ":" + quantity;
    }

    public static String sellPacketConstructor(String ticker, int quantity){
        return "SELL:" + ticker + ":" + quantity;
    }

    public static String powerupPacketConstructor(String powerupName){
        return "POWERUP:" + powerupName;
    }

    //Add deconstructor for the class
    public Cycle1UI() {
        //Constructor can be used to initialize any necessary variables or state
    }

    public static void run(){
        try {
            FXMLLoader loader = new FXMLLoader(Cycle1UI.class.getResource("cycle1.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("Project Belford");
            stage.setAlwaysOnTop(true); // <-- TEST THIS
            stage.setFullScreen(true);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Load the FXML and set up the stage now its mutable in the main loop
        //Setup the UI using the fxml

        //Add a listener thread for incoming packets to the client and update the UI accordingly
        //Graph update (max 0.2s per, min 1s per)
        //AI Ticks to show the AIs progress (condense this to avoid unnecessary updates)


        //MAIN LOOPS
        while (running){
            ///Handler for UI interactions/ updates default packet contrcutor
            
        }

    }

    public static void stop() {
        running = false;
    }
    
}
