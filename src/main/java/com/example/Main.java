package com.example;

import com.example.client.ClientGUI;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Exception ignored) {}

        // Tự động khởi động Java Web Server cổng 8080 ngầm phục vụ file tĩnh và API
        SwingUtilities.invokeLater(() -> {
            com.example.server.ServerGUI serverGUI = new com.example.server.ServerGUI();
            serverGUI.toggleServer(); // Tự động bấm nút khởi động cổng 8080
            
            ClientGUI clientGUI = new ClientGUI(serverGUI);
            clientGUI.setExtendedState(JFrame.MAXIMIZED_BOTH);
            clientGUI.setVisible(true);
        });
    }
}
