package com.seiri.backup_restore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.seiri.backup_restore.ui.MainFrame;

import javax.swing.SwingUtilities;

@SpringBootApplication
public class BackUpAndRestoreModelApplication {

    public static void main(String[] args) {
        // Disable headless mode for Swing
        System.setProperty("java.awt.headless", "false");

        // Start Spring Boot application
        ConfigurableApplicationContext context = SpringApplication.run(BackUpAndRestoreModelApplication.class, args);

        // Launch JFrame on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            MainFrame existingInstance = MainFrame.getInstance();

            if (existingInstance != null && existingInstance.isVisible()) {
                existingInstance.bringToFront();
                System.out.println("Application is already running. Bringing to front.");
            } else {
                MainFrame mainFrame = context.getBean(MainFrame.class);
                mainFrame.setVisible(true);
            }
        });
    }
}
