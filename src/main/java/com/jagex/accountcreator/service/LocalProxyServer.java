package com.jagex.accountcreator.service;

import com.jagex.accountcreator.model.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local HTTP proxy server that forwards to an authenticated upstream proxy
 */
public class LocalProxyServer {
    private static final Logger log = LoggerFactory.getLogger(LocalProxyServer.class);
    private static final int BUFFER_SIZE = 8192;

    private final int localPort;
    private final Proxy upstreamProxy;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private AtomicBoolean running;

    public LocalProxyServer(Proxy upstreamProxy) throws IOException {
        this.upstreamProxy = upstreamProxy;
        this.serverSocket = new ServerSocket(0); // Auto-assign port
        this.localPort = serverSocket.getLocalPort();
        this.executor = Executors.newCachedThreadPool();
        this.running = new AtomicBoolean(false);

        log.info("Local proxy server created on port: {}", localPort);
    }

    public int getLocalPort() {
        return localPort;
    }

    public void start() {
        running.set(true);
        Thread acceptThread = new Thread(() -> {
            log.info("Local proxy server listening on port {}", localPort);
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running.get()) {
                        log.error("Error accepting connection", e);
                    }
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        log.info("Stopping local proxy server");
        running.set(false);
        executor.shutdownNow();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error closing server socket", e);
        }
    }

    private void handleClient(Socket clientSocket) {
        log.info("=== NEW CLIENT CONNECTION from {} ===", clientSocket.getRemoteSocketAddress());
        try (clientSocket) {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            // Read the client's request
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientIn, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                log.warn("Client sent empty request");
                return;
            }

            log.info("REQUEST: {}", requestLine);

            // Parse request method and URL
            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                return;
            }

            String method = parts[0];
            String url = parts[1];

            // Read headers
            StringBuilder headers = new StringBuilder();
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                headers.append(line).append("\r\n");
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            // Connect to upstream proxy
            log.info("Connecting to upstream proxy: {}:{}", upstreamProxy.getIp(), upstreamProxy.getPort());
            Socket upstreamSocket = new Socket(upstreamProxy.getIp(), Integer.parseInt(upstreamProxy.getPort()));
            log.info("Connected to upstream proxy successfully");
            InputStream upstreamIn = upstreamSocket.getInputStream();
            OutputStream upstreamOut = upstreamSocket.getOutputStream();

            // Handle CONNECT method (HTTPS)
            if ("CONNECT".equalsIgnoreCase(method)) {
                // Send CONNECT request to upstream proxy with auth
                StringBuilder connectRequest = new StringBuilder();
                connectRequest.append(requestLine).append("\r\n");
                connectRequest.append("Host: ").append(url).append("\r\n");

                if (upstreamProxy.hasAuth()) {
                    String credentials = upstreamProxy.getUsername() + ":" + upstreamProxy.getPassword();
                    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                    connectRequest.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n");
                }
                connectRequest.append("\r\n");

                upstreamOut.write(connectRequest.toString().getBytes(StandardCharsets.UTF_8));
                upstreamOut.flush();

                // Read response from upstream proxy
                BufferedReader upstreamReader = new BufferedReader(new InputStreamReader(upstreamIn, StandardCharsets.UTF_8));
                String responseLine = upstreamReader.readLine();
                log.info("Upstream proxy CONNECT response: {}", responseLine);

                // Forward response to client
                clientOut.write((responseLine + "\r\n").getBytes(StandardCharsets.UTF_8));

                // Read and forward response headers
                while ((line = upstreamReader.readLine()) != null && !line.isEmpty()) {
                    clientOut.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
                clientOut.write("\r\n".getBytes(StandardCharsets.UTF_8));
                clientOut.flush();

                // If connection established, tunnel data
                if (responseLine != null && responseLine.contains("200")) {
                    tunnel(clientSocket, upstreamSocket);
                }
            } else {
                // Handle regular HTTP request
                StringBuilder request = new StringBuilder();
                request.append(requestLine).append("\r\n");
                request.append(headers);

                if (upstreamProxy.hasAuth()) {
                    String credentials = upstreamProxy.getUsername() + ":" + upstreamProxy.getPassword();
                    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                    request.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n");
                }
                request.append("\r\n");

                upstreamOut.write(request.toString().getBytes(StandardCharsets.UTF_8));

                // Forward request body if present
                if (contentLength > 0) {
                    byte[] buffer = new byte[contentLength];
                    int read = clientIn.read(buffer);
                    upstreamOut.write(buffer, 0, read);
                }
                upstreamOut.flush();

                // Forward response back to client
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = upstreamIn.read(buffer)) != -1) {
                    clientOut.write(buffer, 0, bytesRead);
                    clientOut.flush();
                }
            }

            upstreamSocket.close();
        } catch (Exception e) {
            log.error("Error handling client: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Full exception:", e);
            }
        }
    }

    private void tunnel(Socket client, Socket upstream) {
        Thread clientToUpstream = new Thread(() -> {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                InputStream in = client.getInputStream();
                OutputStream out = upstream.getOutputStream();
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        });

        Thread upstreamToClient = new Thread(() -> {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                InputStream in = upstream.getInputStream();
                OutputStream out = client.getOutputStream();
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        });

        clientToUpstream.start();
        upstreamToClient.start();

        try {
            clientToUpstream.join();
            upstreamToClient.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}