package com.jagex.accountcreator.exception;

/**
 * Exception thrown during account registration
 */
public class RegistrationException extends Exception {
    public RegistrationException(String message) {
        super(message);
    }

    public RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
