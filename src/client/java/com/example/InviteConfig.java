package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class InviteConfig {
    private static String ownerUsername = null;

    public static void loadConfig(Path configPath) {
        Properties props = new Properties();
        try {
            if (Files.exists(configPath)) {
                props.load(Files.newBufferedReader(configPath));
                ownerUsername = props.getProperty("ownerUsername");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getOwnerUsername() {
        return ownerUsername;
    }
}