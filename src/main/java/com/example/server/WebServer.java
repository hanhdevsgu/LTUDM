package com.example.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean isRunning = false;
    private final ServerGUI gui;

    public WebServer(ServerGUI gui) {
        this.gui = gui;
    }

    public void start(int port) {
        if (isRunning) return;
        
        try {
            serverSocket = new ServerSocket(port);
            threadPool = Executors.newFixedThreadPool(10); // Pool cho phép 10 clients đồng thời
            isRunning = true;
            gui.logStatus("Server is listening on port " + port);

            Thread acceptThread = new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        threadPool.execute(new ClientHandler(clientSocket, gui));
                    } catch (IOException e) {
                        if (isRunning) {
                            gui.logStatus("Error accepting client: " + e.getMessage());
                        }
                    }
                }
            });
            acceptThread.start();
        } catch (IOException e) {
            gui.logStatus("Failed to start server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null) {
                threadPool.shutdownNow();
            }
            gui.logStatus("Server stopped.");
        } catch (IOException e) {
            gui.logStatus("Error stopping server: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
