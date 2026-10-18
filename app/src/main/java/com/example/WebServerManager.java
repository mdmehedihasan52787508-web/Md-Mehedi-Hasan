package com.example;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AirLink Embedded HTTP Server
 * Provides browser-accessible web interface and REST API endpoints over local Wi-Fi.
 * Built entirely using Java standard sockets (java.net.ServerSocket) for 100% AIDE compatibility
 * with zero external library dependencies.
 */
public class WebServerManager {

    private static final String TAG = "AirLinkWebServer";
    private static WebServerManager instance;

    private final Context context;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean isRunning = false;
    private int port = 8888;

    public static synchronized WebServerManager getInstance(Context context) {
        if (instance == null) {
            instance = new WebServerManager(context.getApplicationContext());
        }
        return instance;
    }

    private WebServerManager(Context context) {
        this.context = context;
    }

    public synchronized void start(int serverPort) {
        if (isRunning) {
            if (this.port == serverPort) return;
            stop();
        }

        this.port = serverPort;
        this.threadPool = Executors.newCachedThreadPool();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                Log.d(TAG, "AirLink HTTP server started on port " + port);

                while (isRunning && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleClient(clientSocket));
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Server error: " + e.getMessage());
                }
            }
        }).start();
    }

    public synchronized void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping server: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;

            String method = parts[0];
            String path = parts[1];

            // Parse headers
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception ignored) {}
                }
            }

            // Route Requests
            if (path.startsWith("/api/")) {
                handleApiRequest(method, path, reader, contentLength, out);
            } else {
                serveStaticAsset(path, out);
            }

        } catch (Exception e) {
            Log.e(TAG, "Client handling exception: " + e.getMessage());
        }
    }

    private void handleApiRequest(String method, String path, BufferedReader reader, int contentLength, OutputStream out) throws Exception {
        if ("/api/status".equals(path)) {
            JSONObject obj = new JSONObject();
            obj.put("status", "online");
            obj.put("server", "AirLink/1.0");
            obj.put("pinRequired", true);
            sendResponse(out, 200, "application/json", obj.toString().getBytes("UTF-8"));
        } else if ("/api/pair".equals(path) && "POST".equalsIgnoreCase(method)) {
            char[] bodyChars = new char[contentLength];
            reader.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars);
            
            JSONObject req = new JSONObject(body.isEmpty() ? "{}" : body);
            String pin = req.optString("pin", "");
            String deviceName = req.optString("name", "Remote Browser");

            JSONObject res = new JSONObject();
            if (DevicePairing.getInstance().verifyPin(pin)) {
                DevicePairing.PairedDevice dev = DevicePairing.getInstance().registerDevice(deviceName, "Wi-Fi Client");
                res.put("success", true);
                res.put("token", dev.authToken);
                res.put("message", "Device paired successfully");
            } else {
                res.put("success", false);
                res.put("message", "Invalid or expired pairing PIN");
            }
            sendResponse(out, 200, "application/json", res.toString().getBytes("UTF-8"));
        } else if ("/api/files".equals(path)) {
            String filesJson = FileTransferManager.getInstance(context).getReceivedFilesJson();
            sendResponse(out, 200, "application/json", filesJson.getBytes("UTF-8"));
        } else {
            sendResponse(out, 404, "text/plain", "Not Found".getBytes("UTF-8"));
        }
    }

    private void serveStaticAsset(String path, OutputStream out) throws Exception {
        if ("/".equals(path) || path.isEmpty()) {
            path = "/index.html";
        }

        // Clean query strings
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }

        String assetPath = path.startsWith("/") ? path.substring(1) : path;
        String mimeType = "text/plain";
        if (assetPath.endsWith(".html")) mimeType = "text/html; charset=UTF-8";
        else if (assetPath.endsWith(".css")) mimeType = "text/css";
        else if (assetPath.endsWith(".js")) mimeType = "application/javascript";
        else if (assetPath.endsWith(".png")) mimeType = "image/png";
        else if (assetPath.endsWith(".svg")) mimeType = "image/svg+xml";

        try (InputStream assetIn = context.getAssets().open(assetPath)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = assetIn.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            sendResponse(out, 200, mimeType, buffer.toByteArray());
        } catch (Exception e) {
            sendResponse(out, 404, "text/plain", ("404 Not Found: " + path).getBytes("UTF-8"));
        }
    }

    private void sendResponse(OutputStream out, int statusCode, String contentType, byte[] body) throws Exception {
        String statusText = (statusCode == 200) ? "OK" : (statusCode == 404 ? "Not Found" : "Error");
        String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "\r\n";
        out.write(header.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }
}
