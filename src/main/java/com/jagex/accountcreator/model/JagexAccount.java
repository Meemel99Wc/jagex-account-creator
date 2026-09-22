package com.jagex.accountcreator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a created Jagex account with all details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JagexAccount {
    private String email;
    private String password;
    private Birthday birthday;
    
    @JsonProperty("real_ip")
    private String realIp;
    
    private Proxy proxy;
    private TwoFactorAuth tfa;
}
