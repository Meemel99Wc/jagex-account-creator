package com.jagex.accountcreator.util;

import com.jagex.accountcreator.model.Proxy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing proxy configurations
 */
public class ProxyParser {

    /**
     * Parse a proxy string in format: ip:port:username:password
     * or ip:port for unauthenticated proxies
     */
    public static Proxy parseProxyString(String proxyString) {
        if (proxyString == null || proxyString.trim().isEmpty()) {
            return null;
        }

        String[] parts = proxyString.trim().split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid proxy format: " + proxyString +
                    ". Expected format: ip:port or ip:port:username:password");
        }

        String ip = parts[0].trim();
        String port = parts[1].trim();
        String username = null;
        String password = null;

        if (parts.length >= 4) {
            username = parts[2].trim();
            password = parts[3].trim();
        }

        return Proxy.builder()
                .ip(ip)
                .port(port)
                .username(username)
                .password(password)
                .build();
    }

    /**
     * Parse multiple proxy strings from a list
     */
    public static List<Proxy> parseProxyList(List<String> proxyStrings) {
        List<Proxy> proxies = new ArrayList<>();

        for (String proxyString : proxyStrings) {
            if (proxyString != null && !proxyString.trim().isEmpty() && !proxyString.trim().startsWith("#")) {
                try {
                    Proxy proxy = parseProxyString(proxyString);
                    proxies.add(proxy);
                } catch (Exception e) {
                    System.err.println("Failed to parse proxy: " + proxyString + " - " + e.getMessage());
                }
            }
        }

        return proxies;
    }

    /**
     * Load proxies from a file (one proxy per line)
     */
    public static List<Proxy> loadProxiesFromFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        List<String> lines = Files.readAllLines(path);
        return parseProxyList(lines);
    }
}