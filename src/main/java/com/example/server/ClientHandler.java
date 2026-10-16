package com.example.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * ClientHandler: Xử lý từng Request từ Client theo đúng chuẩn Bài 12 - Lập trình mạng.
 * Hỗ trợ:
 * 1. GET / & Static files (HTML, CSS, JS, ảnh)
 * 2. GET /api/time (200 OK - JSON)
 * 3. POST /api/message (201 Created - JSON)
 * 4. GET /abc (404 Not Found)
 * 5. Bắt lỗi 415 Unsupported Media Type & 405 Method Not Allowed
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ServerGUI gui;
    private static final String WEB_ROOT = "www";
    private static final ObjectMapper mapper = new ObjectMapper();

    public ClientHandler(Socket socket, ServerGUI gui) {
        this.clientSocket = socket;
        this.gui = gui;
    }

    @Override
    public void run() {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        try (
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))
        ) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                sendErrorResponse(out, 400, "Bad Request", "text/plain", "400 Bad Request");
                gui.logRequest(clientIP, requestLine, 400);
                return;
            }

            String method = parts[0].toUpperCase();
            String path = parts[1];

            // Đọc Headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            int contentLength = 0;
            String contentType = "";

            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int colonIdx = headerLine.indexOf(":");
                if (colonIdx > 0) {
                    String key = headerLine.substring(0, colonIdx).trim().toLowerCase();
                    String val = headerLine.substring(colonIdx + 1).trim();
                    headers.put(key, val);
                    if (key.equals("content-length")) {
                        try { contentLength = Integer.parseInt(val); } catch (Exception ignored) {}
                    }
                    if (key.equals("content-type")) {
                        contentType = val;
                    }
                }
            }

            // Đọc Body nếu có (cho POST request)
            String requestBody = "";
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int readTotal = 0;
                while (readTotal < contentLength) {
                    int read = reader.read(bodyChars, readTotal, contentLength - readTotal);
                    if (read == -1) break;
                    readTotal += read;
                }
                requestBody = new String(bodyChars, 0, readTotal);
            }

            // ==============================================================
            // ĐIỀU HƯỚNG ROUTING (THEO SLIDE 15 & 18 CỦA BÀI GIẢNG)
            // ==============================================================

            // 1. Route: GET /api/time -> 200 OK application/json
            if (method.equals("GET") && path.equals("/api/time")) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                Map<String, String> jsonMap = new HashMap<>();
                jsonMap.put("time", sdf.format(new Date()));
                String jsonResponse = mapper.writeValueAsString(jsonMap);

                sendResponse(out, 200, "OK", "application/json", jsonResponse.getBytes("UTF-8"));
                gui.logRequest(clientIP, "GET /api/time", 200);
                return;
            }

            // 2. Route: POST /api/message -> 201 Created application/json
            if (method.equals("POST") && path.equals("/api/message")) {
                // Kiểm tra Content-Type theo Slide 18
                if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
                    sendErrorResponse(out, 415, "Unsupported Media Type", "text/plain", "415 Unsupported Media Type: Vui lòng gửi Content-Type: application/json");
                    gui.logRequest(clientIP, "POST /api/message (Sai Content-Type)", 415);
                    return;
                }

                Map<String, String> responseMap = new HashMap<>();
                responseMap.put("message", "Đã nhận dữ liệu");
                responseMap.put("received_body", requestBody);
                responseMap.put("server_status", "Success");
                String responseJson = mapper.writeValueAsString(responseMap);

                sendResponse(out, 201, "Created", "application/json", responseJson.getBytes("UTF-8"));
                gui.logRequest(clientIP, "POST /api/message", 201);
                return;
            }

            // 3. Xử lý các phương thức khác ngoài GET
            if (!method.equals("GET")) {
                sendErrorResponse(out, 405, "Method Not Allowed", "text/plain", "405 Method Not Allowed");
                gui.logRequest(clientIP, method + " " + path, 405);
                return;
            }

            // 4. Phục vụ Static Files (HTML, CSS, JS, Image)
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Cắt bỏ query parameters nếu có
            if (path.contains("?")) {
                path = path.substring(0, path.indexOf("?"));
            }

            Path requestedFile = Paths.get(WEB_ROOT, path).normalize();
            Path root = Paths.get(WEB_ROOT).normalize().toAbsolutePath();

            // Chặn tấn công Directory Traversal (../)
            if (!requestedFile.toAbsolutePath().startsWith(root)) {
                sendErrorResponse(out, 403, "Forbidden", "text/plain", "403 Forbidden");
                gui.logRequest(clientIP, requestLine, 403);
                return;
            }

            File file = requestedFile.toFile();
            if (!file.exists() || file.isDirectory()) {
                // Trả về lỗi 404 Not Found theo Ca kiểm thử 3 trong Slide 15
                sendErrorResponse(out, 404, "Not Found", "text/plain", "404 Not Found");
                gui.logRequest(clientIP, "GET " + path + " (Không tìm thấy)", 404);
                return;
            }

            byte[] fileData = Files.readAllBytes(requestedFile);
            String mimeType = getContentType(requestedFile.toString());

            sendResponse(out, 200, "OK", mimeType, fileData);
            gui.logRequest(clientIP, "GET " + path, 200);

        } catch (Exception e) {
            try {
                gui.logRequest(clientIP, "Lỗi Server: " + e.getMessage(), 500);
                sendErrorResponse(clientSocket.getOutputStream(), 500, "Internal Server Error", "text/plain", "500 Internal Server Error");
            } catch (IOException ignored) {}
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void sendErrorResponse(OutputStream out, int statusCode, String statusMessage, String contentType, String bodyText) throws IOException {
        byte[] bodyBytes = bodyText.getBytes("UTF-8");
        sendResponse(out, statusCode, statusMessage, contentType, bodyBytes);
    }

    private void sendResponse(OutputStream out, int statusCode, String statusMessage, String contentType, byte[] body) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), false);
        writer.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
        writer.print("Content-Type: " + contentType + "; charset=UTF-8\r\n");
        writer.print("Content-Length: " + body.length + "\r\n");
        writer.print("Server: Java-PureSocket-Server/2.0\r\n");
        writer.print("Connection: close\r\n");
        writer.print("\r\n");
        writer.flush();
        out.write(body);
        out.flush();
    }

    private String getContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}
