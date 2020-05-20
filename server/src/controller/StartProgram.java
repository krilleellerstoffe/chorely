package controller;

import java.io.File;

/**
 * Main method of the server.
 * Sets up logging properties and then launches a ServerController.
 */
public class StartProgram {
    public static void main(String[] args) {
        File f = new File("logs/logging.properties");
        if (f.exists() && !f.isDirectory()) {
            System.setProperty("java.util.logging.config.file", "logs/logging.properties");
        } else {
            System.err.println("Couldn't load logging properties file. Exiting.");
            System.exit(0);
        }
        
        // todo: create the files/groups and files/users directories if they don't exist.

        ServerController prog = new ServerController(6583);
    }
}
