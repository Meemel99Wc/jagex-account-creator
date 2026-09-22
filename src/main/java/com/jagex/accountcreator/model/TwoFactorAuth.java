package com.jagex.accountcreator.model;

import com.bastiaanjansen.otp.TOTPGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * Two-factor authentication configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorAuth {
    private String setupKey;
    private List<String> backupCodes;

    /**
     * Generate current TOTP code
     */
    public String getTotpCode() {
        try {
            TOTPGenerator totpGenerator = new TOTPGenerator.Builder(setupKey.getBytes())
                    .withHOTPGenerator(builder -> {
                        builder.withPasswordLength(6);
                    })
                    .withPeriod(Duration.ofSeconds(30))
                    .build();
            return totpGenerator.now();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }
}
