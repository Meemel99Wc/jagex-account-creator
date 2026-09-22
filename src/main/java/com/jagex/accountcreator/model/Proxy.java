package com.jagex.accountcreator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents proxy configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Proxy {
    private String ip;
    private String port;
    private String username;
    private String password;

    public boolean hasAuth() {
        return username != null && !username.isEmpty() 
            && password != null && !password.isEmpty();
    }

    public String getProxyUrl() {
        if (hasAuth()) {
            return String.format("http://%s:%s@%s:%s", username, password, ip, port);
        }
        return String.format("http://%s:%s", ip, port);
    }
}
