package com.sting.clipboardsender;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

public class HttpServer implements Runnable {

    private static final String TAG = "HttpServer";
    private static final String RESPONSE_OK = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nAccess-Control-Allow-Origin: *\r\n\r\nOK";
    private static final String RESPONSE_BAD = "HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain\r\n\r\nBad Request";

    private final int port;
    private final ClipboardService clipboardService;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public HttpServer(int port, ClipboardService clipboardService) {
        this.port = port;
        this.clipboardService = clipboardService;
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            Log.d(TAG, "HTTP Server started on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleRequest(clientSocket);
                } catch (IOException e) {
                    if (running) {
                        Log.e(TAG, "Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to start HTTP server", e);
        }
    }

    private void handleRequest(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();

            String requestLine = reader.readLine();
            if (requestLine == null) {
                sendResponse(out, RESPONSE_BAD);
                close(clientSocket);
                return;
            }

            Log.d(TAG, "Request: " + requestLine);

            String method = null;
            String path = null;
            String[] parts = requestLine.split(" ");
            if (parts.length >= 2) {
                method = parts[0];
                path = parts[1];
            }

            // Read headers
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            // Read body
            String body = "";
            if ("POST".equalsIgnoreCase(method) && contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int read = reader.read(bodyChars, 0, contentLength);
                body = new String(bodyChars, 0, read);
            }

            // Handle /send endpoint
            if ("POST".equalsIgnoreCase(method) && "/send".equals(path)) {
                Log.d(TAG, "Received send request, body length: " + body.length());

                // Set clipboard text
                ClipboardService.setClipboardText(clipboardService, body);

                // Trigger accessibility paste after a short delay
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {}
                        ClipboardAccessibilityService.triggerPaste();
                    }
                }).start();

                sendResponse(out, RESPONSE_OK);
            } else {
                sendResponse(out, RESPONSE_BAD);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error handling request", e);
        } finally {
            close(clientSocket);
        }
    }

    private void sendResponse(OutputStream out, String response) throws IOException {
        out.write(response.getBytes());
        out.flush();
    }

    private void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
