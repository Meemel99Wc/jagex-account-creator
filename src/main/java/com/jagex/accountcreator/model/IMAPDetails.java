package com.jagex.accountcreator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IMAP server configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IMAPDetails {
    private String ip;
    private int port;
    private String email;
    private String password;
}
