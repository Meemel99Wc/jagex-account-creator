package com.jagex.accountcreator.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jagex.accountcreator.model.JagexAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Repository for persisting created accounts to JSON file
 */
public class AccountRepository {
    private static final Logger log = LoggerFactory.getLogger(AccountRepository.class);
    private final ObjectMapper objectMapper;
    private final Path accountsFilePath;

    public AccountRepository(String accountsFilePath) {
        this.accountsFilePath = Path.of(accountsFilePath);
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Load all accounts from file
     */
    public synchronized List<JagexAccount> loadAccounts() throws IOException {
        File file = accountsFilePath.toFile();
        
        if (!file.exists() || file.length() == 0) {
            log.debug("Accounts file does not exist or is empty");
            return new ArrayList<>();
        }

        try {
            JagexAccount[] accounts = objectMapper.readValue(file, JagexAccount[].class);
            return new ArrayList<>(Arrays.asList(accounts));
        } catch (IOException e) {
            log.error("Error loading accounts from file", e);
            throw e;
        }
    }

    /**
     * Save all accounts to file
     */
    public synchronized void saveAccounts(List<JagexAccount> accounts) throws IOException {
        // Ensure parent directory exists
        Files.createDirectories(accountsFilePath.getParent());
        
        try {
            objectMapper.writeValue(accountsFilePath.toFile(), accounts);
            log.debug("Saved {} accounts to file", accounts.size());
        } catch (IOException e) {
            log.error("Error saving accounts to file", e);
            throw e;
        }
    }

    /**
     * Append a single account to the file
     */
    public synchronized void appendAccount(JagexAccount account) throws IOException {
        log.info("Saving account to file: {}", account.getEmail());
        
        List<JagexAccount> accounts = loadAccounts();
        accounts.add(account);
        saveAccounts(accounts);
        
        log.info("Account saved successfully");
    }
}
