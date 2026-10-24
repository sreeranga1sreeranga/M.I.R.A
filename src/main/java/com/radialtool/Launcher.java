package com.radialtool;

import java.io.File;

public class Launcher {
    public static void main(String[] args) {
        // Headless CLI Mode for Tauri v2 Sidecar:
        // Usage: java -jar mira-engine.jar "/path/to/file.xlsx" "JSON"
        if (args.length >= 2) {
            File targetFile = new File(args[0]);
            String action = args[1].toUpperCase();
            if (targetFile.exists()) {
                RadialConverterApp engine = new RadialConverterApp();
                engine.runHeadlessConversion(targetFile, action);
                System.exit(0);
            } else {
                System.err.println("File not found: " + args[0]);
                System.exit(1);
            }
        } else {
            // Fallback to JavaFX mode if launched without args
            RadialConverterApp.main(args);
        }
    }
}