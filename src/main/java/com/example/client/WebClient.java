package com.example.client;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebClient: Xử lý Socket & SSLSocket thuần.
 * Hỗ trợ:
 * 1. GET & POST JSON Request
 * 2. SSL TrustManager cho chứng chỉ IIS cục bộ
 * 3. crawlAndDeploy: Cào source web ngoài và tự động nạp vào thư mục IIS
 */
public class WebClient {

    private static SSLSocketFactory trustingSslSocketFactory;
    private static final int TIMEOUT_MS = 10000;

    static {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            trustingSslSocketFactory = sc.getSocketFactory();

            // Cho phép toàn bộ kết nối HTTPS (kể cả chứng chỉ tự ký của IIS localhost:8443)
            HttpsURLConnection.setDefaultSSLSocketFactory(trustingSslSocketFactory);
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            trustingSslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    public static HttpResponse sendGetRequest(String urlString) throws Exception {
        return sendRequest(urlString, "GET", null, null, 0);
    }

    public static HttpResponse sendPostRequest(String urlString, String jsonBody) throws Exception {
        return sendRequest(urlString, "POST", "application/json", jsonBody, 0);
    }

    public static HttpResponse sendRequest(String urlString, String method, String contentType, String requestBody) throws Exception {
        return sendRequest(urlString, method, contentType, requestBody, 0);
    }

    public static HttpResponse sendRequest(String urlString, String method, String contentType, String requestBody, int redirectCount) throws Exception {
        long startTime = System.currentTimeMillis();
        URI uri = new URI(urlString);
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Địa chỉ URL không hợp lệ: Không tìm thấy host!");
        }

        int port = uri.getPort();
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "http";
        boolean isHttps = "https".equals(scheme);

        if (port == -1) {
            port = isHttps ? 443 : 80;
        }

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            path += "?" + query;
        }

        Socket socket = null;
        String tlsProtocol = "Không mã hóa (Plain HTTP)";
        String cipherSuite = "None";

        try {
            if (isHttps) {
                socket = trustingSslSocketFactory.createSocket();
                socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);

                SSLSocket sslSocket = (SSLSocket) socket;
                sslSocket.startHandshake();
                SSLSession session = sslSocket.getSession();
                tlsProtocol = session.getProtocol();
                cipherSuite = session.getCipherSuite();
            } else {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);
            }

            OutputStream out = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), false);

            byte[] bodyBytes = (requestBody != null) ? requestBody.getBytes("UTF-8") : new byte[0];

            writer.print(method + " " + path + " HTTP/1.1\r\n");
            writer.print("Host: " + host + (port != 80 && port != 443 ? ":" + port : "") + "\r\n");
            writer.print("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n");
            writer.print("Accept: text/html,application/xhtml+xml,application/json,*/*;q=0.9\r\n");
            writer.print("Accept-Language: vi,en-US;q=0.9,en;q=0.8\r\n");

            if (contentType != null) {
                writer.print("Content-Type: " + contentType + "\r\n");
            }
            if (bodyBytes.length > 0) {
                writer.print("Content-Length: " + bodyBytes.length + "\r\n");
            }
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.flush();

            if (bodyBytes.length > 0) {
                out.write(bodyBytes);
                out.flush();
            }

            BufferedInputStream bin = new BufferedInputStream(socket.getInputStream());

            String statusLine = readAsciiLine(bin);
            if (statusLine == null) {
                throw new IOException("Máy chủ đóng kết nối đột ngột mà không phản hồi dữ liệu.");
            }

            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readAsciiLine(bin)) != null && !line.trim().isEmpty()) {
                int colonIdx = line.indexOf(":");
                if (colonIdx > 0) {
                    headers.put(line.substring(0, colonIdx).trim(), line.substring(colonIdx + 1).trim());
                } else {
                    headers.put(line.trim(), "");
                }
            }

            // Tự động chuyển hướng (Redirect 301, 302, 303, 307, 308)
            int statusCode = parseStatusCode(statusLine);
            if ((statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308) && redirectCount < 5) {
                String location = null;
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if ("location".equalsIgnoreCase(entry.getKey())) {
                        location = entry.getValue();
                        break;
                    }
                }
                if (location != null && !location.trim().isEmpty()) {
                    try { socket.close(); } catch (Exception ignored) {}
                    URI nextUri = uri.resolve(location.trim());
                    return sendRequest(nextUri.toString(), "GET", null, null, redirectCount + 1);
                }
            }

            boolean isChunked = false;
            int contentLength = -1;
            String charset = "UTF-8";

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey().toLowerCase();
                String val = entry.getValue().toLowerCase();
                if ("transfer-encoding".equals(key) && val.contains("chunked")) {
                    isChunked = true;
                }
                if ("content-length".equals(key)) {
                    try {
                        contentLength = Integer.parseInt(entry.getValue().trim());
                    } catch (Exception ignored) {}
                }
                if ("content-type".equals(key) && val.contains("charset=")) {
                    int cIdx = val.indexOf("charset=");
                    String cs = entry.getValue().substring(cIdx + 8).trim();
                    int endCs = cs.indexOf(';');
                    if (endCs != -1) cs = cs.substring(0, endCs).trim();
                    if (!cs.isEmpty()) charset = cs;
                }
            }

            byte[] responseBytes;
            if (isChunked) {
                responseBytes = readChunkedStream(bin);
            } else if (contentLength >= 0) {
                responseBytes = readFixedStream(bin, contentLength);
            } else {
                responseBytes = readAllRemainingStream(bin);
            }

            String finalBody;
            try {
                finalBody = new String(responseBytes, charset);
            } catch (Exception e) {
                finalBody = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
            }

            // Loại bỏ hoàn toàn mã hex chunk còn sót (nếu có)
            finalBody = cleanChunkArtifacts(finalBody);

            long duration = System.currentTimeMillis() - startTime;
            return new HttpResponse(statusLine, headers, finalBody, isHttps, tlsProtocol, cipherSuite, duration);

        } catch (SocketTimeoutException e) {
            throw new Exception("Lỗi Timeout: Quá thời gian chờ phản hồi (" + TIMEOUT_MS + " ms)!");
        } catch (ConnectException e) {
            throw new Exception("Lỗi Kết Nối: Không thể kết nối tới " + host + ":" + port + " (Máy chủ chưa bật hoặc sai cổng Port)!");
        } finally {
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static String readAsciiLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        if (sb.length() == 0 && c == -1) return null;
        return sb.toString();
    }

    private static byte[] readChunkedStream(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            String hexLine = readAsciiLine(in);
            if (hexLine == null) break;
            hexLine = hexLine.trim();
            if (hexLine.isEmpty()) continue;
            int semi = hexLine.indexOf(';');
            if (semi != -1) hexLine = hexLine.substring(0, semi).trim();
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(hexLine, 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (chunkSize == 0) {
                // Đọc phần trailer headers (nếu có) cho đến dòng trống
                while (true) {
                    String trailer = readAsciiLine(in);
                    if (trailer == null || trailer.trim().isEmpty()) break;
                }
                break;
            }
            byte[] chunk = new byte[chunkSize];
            int totalRead = 0;
            while (totalRead < chunkSize) {
                int r = in.read(chunk, totalRead, chunkSize - totalRead);
                if (r == -1) break;
                totalRead += r;
            }
            baos.write(chunk, 0, totalRead);
            // Bỏ qua ký tự \r\n kết thúc chunk
            readAsciiLine(in);
        }
        return baos.toByteArray();
    }

    private static byte[] readFixedStream(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int r = in.read(data, totalRead, length - totalRead);
            if (r == -1) break;
            totalRead += r;
        }
        return data;
    }

    private static byte[] readAllRemainingStream(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toByteArray();
    }

    private static String cleanChunkArtifacts(String body) {
        if (body == null || body.isEmpty()) return "";
        // Nếu trang bắt đầu bằng mã hex chunk (ví dụ "7b0d\n<!DOCTYPE" hoặc "3201\n<html")
        body = body.replaceFirst("^[0-9a-fA-F]{1,8}\\r?\\n(?=[\\s<])", "");
        // Nếu trang kết thúc bằng số 0 của chunk cuối
        body = body.replaceFirst("(?s)\\r?\\n0\\r?\\n*$", "");
        return body;
    }

    private static int parseStatusCode(String statusLine) {
        try {
            String[] parts = statusLine.split(" ");
            if (parts.length >= 2) return Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        return 0;
    }

    public static String extractDomainFolderName(String urlStr) {
        try {
            URI u = new URI(urlStr);
            String host = u.getHost();
            if (host != null) {
                host = host.toLowerCase().replaceAll("^www\\.", "");
                String mainPart = host;
                if (mainPart.contains(".")) {
                    mainPart = mainPart.substring(0, mainPart.indexOf('.'));
                }
                mainPart = mainPart.replaceAll("[^a-zA-Z0-9_-]", "");
                if (!mainPart.isEmpty()) {
                    return "src_" + mainPart;
                }
            }
        } catch (Exception ignored) {}
        return "src_web";
    }

    /**
     * Tự động cào mã nguồn từ externalUrl và tạo folder riêng cho từng domain (ví dụ: src_24h, src_thongtindaotao)
     * đồng thời nạp vào IIS để mở https://localhost:8443 kiểm tra ngay.
     */
    public static String crawlAndDeploy(String externalUrl, int port) throws Exception {
        HttpResponse res = sendGetRequest(externalUrl);
        if (res.getStatusCode() >= 200 && res.getStatusCode() < 400) {
            String baseUrl = externalUrl;
            if (!baseUrl.endsWith("/")) baseUrl += "/";
            String baseTag = "<base href=\"" + baseUrl + "\" id=\"baseRef\">";
            String htmlToSave = res.body;

            // Xóa tag base cũ nếu có để tránh xung đột đường dẫn
            htmlToSave = htmlToSave.replaceAll("(?i)<base\\s+[^>]*>", "");

            if (htmlToSave.toLowerCase().contains("<head>")) {
                htmlToSave = htmlToSave.replaceFirst("(?i)<head>", "<head>\n    " + baseTag);
            } else {
                htmlToSave = baseTag + "\n" + htmlToSave;
            }

            String folderName = extractDomainFolderName(externalUrl);

            // 1. Lưu vào thư mục chung src_download/<folderName> (ví dụ: src_download/src_24h, src_download/src_google)
            File downloadParentDir = new File("src_download");
            if (!downloadParentDir.exists()) downloadParentDir.mkdirs();

            File domainDir = new File(downloadParentDir, folderName);
            if (!domainDir.exists()) domainDir.mkdirs();
            File domainFile = new File(domainDir, "index.html");
            try (FileWriter fw = new FileWriter(domainFile, java.nio.charset.StandardCharsets.UTF_8)) {
                fw.write(htmlToSave);
            }
            copySupportAssets(domainDir);

            // 2. Tự động tải các file JS / CSS liên kết về thư mục cục bộ để host offline
            downloadLinkedAssets(htmlToSave, baseUrl, domainDir);

            // 3. Đồng bộ toàn bộ mã nguồn vào thư mục www của dự án
            File wwwDir = new File("www");
            if (!wwwDir.exists()) wwwDir.mkdirs();
            copyAllFiles(domainDir, wwwDir);

            // 4. Đồng bộ toàn bộ mã nguồn vào thư mục IIS (C:\inetpub\site1)
            String iisDirStr = (port == 8444) ? "C:\\inetpub\\site2" : "C:\\inetpub\\site1";
            File iisDir = new File(iisDirStr);
            if (iisDir.exists()) {
                copyAllFiles(domainDir, iisDir);
            }
            return folderName;
        } else {
            throw new Exception("Máy chủ phản hồi mã lỗi: " + res.statusLine);
        }
    }

    public static void copyAllFiles(File sourceDir, File targetDir) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs();
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && !f.getName().equalsIgnoreCase("web.config")) {
                        File dest = new File(targetDir, f.getName());
                        java.nio.file.Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void downloadLinkedAssets(String html, String baseUrl, File... targetDirs) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)(?:src|href)=[\"']([^\"']+\\.(?:js|css)(?:\\?[^\"']*)?)[\"']");
            java.util.regex.Matcher m = p.matcher(html);
            java.util.Set<String> downloaded = new java.util.HashSet<>();
            int count = 0;
            while (m.find() && count < 10) {
                String rawUrl = m.group(1);
                if (rawUrl.startsWith("data:") || downloaded.contains(rawUrl)) continue;
                downloaded.add(rawUrl);

                String cleanAssetPath = rawUrl.contains("?") ? rawUrl.substring(0, rawUrl.indexOf('?')) : rawUrl;
                String fileName = new File(cleanAssetPath).getName();
                if (fileName.isEmpty() || !fileName.contains(".")) continue;

                String fullAssetUrl;
                if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    fullAssetUrl = rawUrl;
                } else if (rawUrl.startsWith("//")) {
                    fullAssetUrl = "https:" + rawUrl;
                } else if (rawUrl.startsWith("/")) {
                    URI u = new URI(baseUrl);
                    fullAssetUrl = u.getScheme() + "://" + u.getHost() + (u.getPort() != -1 ? ":" + u.getPort() : "") + rawUrl;
                } else {
                    fullAssetUrl = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + rawUrl;
                }

                try {
                    HttpResponse assetRes = sendGetRequest(fullAssetUrl);
                    if (assetRes.getStatusCode() == 200 && assetRes.body != null && !assetRes.body.isEmpty()) {
                        byte[] assetBytes = assetRes.body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        for (File dir : targetDirs) {
                            if (dir != null && dir.exists()) {
                                File targetFile = new File(dir, fileName);
                                java.nio.file.Files.write(targetFile.toPath(), assetBytes);
                            }
                        }
                        count++;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static void copySupportAssets(File targetDir) {
        try {
            File wwwDir = new File("www");
            String[] assets = {"style.css", "app.js"};
            for (String a : assets) {
                File src = new File(wwwDir, a);
                if (src.exists()) {
                    File dest = new File(targetDir, a);
                    if (!dest.exists()) {
                        java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static class HttpResponse {
        public final String statusLine;
        public final Map<String, String> headers;
        public final String body;
        public final boolean isHttps;
        public final String tlsProtocol;
        public final String cipherSuite;
        public final long responseTimeMs;

        public HttpResponse(String statusLine, Map<String, String> headers, String body,
                            boolean isHttps, String tlsProtocol, String cipherSuite, long responseTimeMs) {
            this.statusLine = statusLine;
            this.headers = headers;
            this.body = body;
            this.isHttps = isHttps;
            this.tlsProtocol = tlsProtocol;
            this.cipherSuite = cipherSuite;
            this.responseTimeMs = responseTimeMs;
        }

        public int getStatusCode() {
            try {
                String[] parts = statusLine.split(" ");
                if (parts.length >= 2) {
                    return Integer.parseInt(parts[1]);
                }
            } catch (Exception ignored) {}
            return 0;
        }
    }
}
