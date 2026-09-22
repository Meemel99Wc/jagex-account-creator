package com.jagex.accountcreator;

import com.jagex.accountcreator.config.Configuration;
import com.jagex.accountcreator.gui.AccountCreatorGUI;
import com.jagex.accountcreator.model.JagexAccount;
import com.jagex.accountcreator.model.Proxy;
import com.jagex.accountcreator.repository.AccountRepository;
import com.jagex.accountcreator.service.AccountCreator;
import com.jagex.accountcreator.util.AccountUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Main application entry point
 * Supports both GUI and CLI modes
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String DEFAULT_CONFIG_PATH = "config.toml";
    private static final String ACCOUNTS_FILE = "accounts.json";

    public static void main(String[] args) {
        // Check if GUI mode is requested (default) or CLI mode
        boolean useGUI = true;
        String configPath = DEFAULT_CONFIG_PATH;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--cli") || args[i].equals("-c")) {
                useGUI = false;
            } else if (args[i].equals("--config") && i + 1 < args.length) {
                configPath = args[++i];
            } else if (args[i].equals("--help") || args[i].equals("-h")) {
                printHelp();
                return;
            }
        }

        if (useGUI) {
            // Launch Swing GUI (works with any Java installation)
            log.info("Starting GUI mode...");
            AccountCreatorGUI.main(args);
        } else {
            // Run CLI mode
            log.info("Starting CLI mode...");
            runCLI(configPath);
        }
    }

    private static void printHelp() {
        System.out.println("Jagex Account Creator - Java Edition");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar jagex-account-creator.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  (none)              Launch GUI mode (default)");
        System.out.println("  --cli, -c           Run in CLI mode");
        System.out.println("  --config <path>     Specify config file path (default: config.toml)");
        System.out.println("  --help, -h          Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar jagex-account-creator.jar");
        System.out.println("  java -jar jagex-account-creator.jar --cli");
        System.out.println("  java -jar jagex-account-creator.jar --cli --config custom.toml");
    }

    private static void runCLI(String configPath) {
        try {
            // Load configuration
            Configuration config = Configuration.loadFromFile(configPath);
            config.validate();

            log.info("=================================================");
            log.info("Jagex Account Creator - Java Edition");
            log.info("=================================================");
            log.info("Configuration loaded successfully");
            log.info("Accounts to create: {}", config.getAccountsToCreate());
            log.info("Threads: {}", config.getThreads());
            log.info("Using IMAP: {}", config.isUseImap());
            log.info("Using Guerrilla Mail: {}", config.isUseGuerrillaMail());
            log.info("2FA enabled: {}", config.isSet2fa());
            log.info("Proxies enabled: {}", config.isProxiesEnabled());
            log.info("=================================================");

            // Initialize repository
            AccountRepository repository = new AccountRepository(ACCOUNTS_FILE);

            // Determine domains based on configuration
            List<String> domains;
            if (config.isUseImap()) {
                domains = config.getImapDomains();
            } else {
                domains = config.getGuerrillaMailDomains();
            }

            // Create thread pool
            ExecutorService executor = Executors.newFixedThreadPool(config.getThreads());
            List<Future<JagexAccount>> futures = new ArrayList<>();

            // Submit account creation tasks
            for (int i = 0; i < config.getAccountsToCreate(); i++) {
                // Generate email
                String email = AccountUtils.generateEmail(domains, 10);

                // Get proxy if enabled
                Proxy proxy = null;
                if (config.isProxiesEnabled() && !config.getProxies().isEmpty()) {
                    proxy = config.getProxies().get(i % config.getProxies().size());
                }

                final Proxy finalProxy = proxy;
                final String finalEmail = email;

                // Submit task
                Future<JagexAccount> future = executor.submit(() -> {
                    try {
                        log.info("Creating account: {}", finalEmail);
                        AccountCreator creator = new AccountCreator(config, finalEmail, finalProxy);
                        JagexAccount account = creator.registerAccount();

                        // Save account
                        repository.appendAccount(account);

                        return account;
                    } catch (Exception e) {
                        log.error("Failed to create account {}: {}", finalEmail, e.getMessage(), e);
                        return null;
                    }
                });

                futures.add(future);

                // Small delay between submissions
                Thread.sleep(1000);
            }

            // Wait for all tasks to complete and collect results
            log.info("Waiting for all account creations to complete...");
            int successCount = 0;
            int failureCount = 0;

            for (Future<JagexAccount> future : futures) {
                try {
                    JagexAccount account = future.get(10, TimeUnit.MINUTES);
                    if (account != null) {
                        successCount++;
                        log.info("Successfully created account: {}", account.getEmail());
                    } else {
                        failureCount++;
                    }
                } catch (TimeoutException e) {
                    log.error("Account creation timed out");
                    failureCount++;
                    future.cancel(true);
                } catch (Exception e) {
                    log.error("Error getting result: {}", e.getMessage());
                    failureCount++;
                }
            }

            // Shutdown executor
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);

            // Print summary
            log.info("=================================================");
            log.info("Account Creation Summary");
            log.info("=================================================");
            log.info("Total attempted: {}", config.getAccountsToCreate());
            log.info("Successful: {}", successCount);
            log.info("Failed: {}", failureCount);
            log.info("Accounts saved to: {}", new File(ACCOUNTS_FILE).getAbsolutePath());
            log.info("=================================================");

        } catch (Exception e) {
            log.error("Fatal error in main application", e);
            System.exit(1);
        }
    }
}