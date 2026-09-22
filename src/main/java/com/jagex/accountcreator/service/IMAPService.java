package com.jagex.accountcreator.service;

import com.jagex.accountcreator.model.IMAPDetails;
import jakarta.mail.*;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.SearchTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for retrieving verification codes via IMAP
 */
public class IMAPService {
    private static final Logger log = LoggerFactory.getLogger(IMAPService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("\\b\\d{6}\\b");
    private static final int TIMEOUT_SECONDS = 120;

    private final IMAPDetails imapDetails;

    public IMAPService(IMAPDetails imapDetails) {
        this.imapDetails = imapDetails;
    }

    /**
     * Get verification code from IMAP inbox
     */
    public String getVerificationCode(String accountUsername, int timeoutSeconds) throws MessagingException, InterruptedException {
        log.info("Checking IMAP for verification code for: {}", accountUsername);

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", imapDetails.getIp());
        props.setProperty("mail.imaps.port", String.valueOf(imapDetails.getPort()));
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.timeout", "30000");
        props.setProperty("mail.imaps.connectiontimeout", "30000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(imapDetails.getIp(), imapDetails.getEmail(), imapDetails.getPassword());

        try {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            try {
                SearchTerm searchTerm = new FromStringTerm("no-reply@contact.jagex.com");
                
                long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
                while (System.currentTimeMillis() < endTime) {
                    Message[] messages = inbox.search(searchTerm);
                    
                    // Check messages in reverse order (newest first)
                    for (int i = messages.length - 1; i >= 0; i--) {
                        Message message = messages[i];
                        String subject = message.getSubject();
                        
                        if (subject != null && subject.contains("verification")) {
                            Matcher matcher = CODE_PATTERN.matcher(subject);
                            if (matcher.find()) {
                                String code = matcher.group();
                                log.info("Found verification code: {}", code);
                                return code;
                            }
                        }
                    }
                    
                    Thread.sleep(2000);
                    
                    // Refresh folder to get new messages
                    inbox.close(false);
                    inbox.open(Folder.READ_ONLY);
                }

                throw new MessagingException("Timed out waiting for verification code");
                
            } finally {
                inbox.close(false);
            }
        } finally {
            store.close();
        }
    }
}
