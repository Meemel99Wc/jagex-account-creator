package com.jagex.accountcreator.gui;

import com.jagex.accountcreator.config.Configuration;
import com.jagex.accountcreator.model.JagexAccount;
import com.jagex.accountcreator.model.Proxy;
import com.jagex.accountcreator.repository.AccountRepository;
import com.jagex.accountcreator.service.AccountCreator;
import com.jagex.accountcreator.util.AccountUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Swing-based GUI for Jagex Account Creator (works with any Java installation)
 */
public class AccountCreatorSwingGUI extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(AccountCreatorSwingGUI.class);
    private static final String ACCOUNTS_FILE = "accounts.json";
    
    private Configuration config;
    private AccountRepository repository;
    private ExecutorService executor;
    private volatile boolean isRunning = false;
    
    // UI Components
    private JTextField accountsField;
    private JTextField threadsField;
    private JTextField passwordField;
    private JCheckBox enable2FACheck;
    private JCheckBox headlessCheck;
    private JCheckBox useImapCheck;
    private JCheckBox useGuerrillaCheck;
    private JCheckBox proxiesEnabledCheck;
    private JTextArea logArea;
    private JTextArea proxyArea;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    
    private int totalAccounts = 0;
    private int completedAccounts = 0;
    
    public AccountCreatorSwingGUI() {
        setTitle("Jagex Account Creator - Java Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        
        // Initialize
        try {
            config = Configuration.loadFromFile("config.toml");
            repository = new AccountRepository(ACCOUNTS_FILE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to load configuration: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        
        initializeUI();
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isRunning) {
                    stopAccountCreation();
                }
            }
        });
    }
    
    private void initializeUI() {
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Title
        JLabel titleLabel = new JLabel("Jagex Account Creator", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("General Settings", createGeneralPanel());
        tabbedPane.addTab("Email Settings", createEmailPanel());
        tabbedPane.addTab("Proxy Settings", createProxyPanel());
        tabbedPane.addTab("Logs", createLogsPanel());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        
        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        startButton = new JButton("Start Creating Accounts");
        startButton.setPreferredSize(new Dimension(200, 35));
        startButton.setBackground(new Color(76, 175, 80));
        startButton.setForeground(Color.WHITE);
        startButton.setFont(new Font("Arial", Font.BOLD, 14));
        startButton.addActionListener(e -> startAccountCreation());
        
        stopButton = new JButton("Stop");
        stopButton.setPreferredSize(new Dimension(100, 35));
        stopButton.setBackground(new Color(244, 67, 54));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFont(new Font("Arial", Font.BOLD, 14));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopAccountCreation());
        
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private JPanel createGeneralPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        // Accounts to create
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Accounts to Create:"), gbc);
        gbc.gridx = 1;
        accountsField = new JTextField(String.valueOf(config.getAccountsToCreate()), 20);
        panel.add(accountsField, gbc);
        row++;
        
        // Threads
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Threads:"), gbc);
        gbc.gridx = 1;
        threadsField = new JTextField(String.valueOf(config.getThreads()), 20);
        panel.add(threadsField, gbc);
        row++;
        
        // Password
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Account Password:"), gbc);
        gbc.gridx = 1;
        passwordField = new JTextField(config.getAccountPassword(), 20);
        panel.add(passwordField, gbc);
        row++;
        
        // Enable 2FA
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Enable 2FA:"), gbc);
        gbc.gridx = 1;
        enable2FACheck = new JCheckBox();
        enable2FACheck.setSelected(config.isSet2fa());
        panel.add(enable2FACheck, gbc);
        row++;
        
        // Headless mode
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Headless Mode:"), gbc);
        gbc.gridx = 1;
        headlessCheck = new JCheckBox();
        headlessCheck.setSelected(config.isHeadless());
        panel.add(headlessCheck, gbc);
        
        return panel;
    }
    
    private JPanel createEmailPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JPanel checkboxPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        
        JLabel methodLabel = new JLabel("Email Verification Method:");
        methodLabel.setFont(new Font("Arial", Font.BOLD, 14));
        checkboxPanel.add(methodLabel);
        
        useGuerrillaCheck = new JCheckBox("Use Guerrilla Mail (Temporary emails - recommended for testing)");
        useGuerrillaCheck.setSelected(config.isUseGuerrillaMail());
        useGuerrillaCheck.addActionListener(e -> {
            if (useGuerrillaCheck.isSelected()) {
                useImapCheck.setSelected(false);
            }
        });
        checkboxPanel.add(useGuerrillaCheck);
        
        useImapCheck = new JCheckBox("Use IMAP (Your own catch-all email - configure in config.toml)");
        useImapCheck.setSelected(config.isUseImap());
        useImapCheck.addActionListener(e -> {
            if (useImapCheck.isSelected()) {
                useGuerrillaCheck.setSelected(false);
            }
        });
        checkboxPanel.add(useImapCheck);
        
        panel.add(checkboxPanel, BorderLayout.NORTH);
        
        JTextArea infoArea = new JTextArea(
            "Guerrilla Mail:\n" +
            "  • Free temporary email service\n" +
            "  • No setup required\n" +
            "  • Best for testing\n\n" +
            "IMAP:\n" +
            "  • Use your own domain with catch-all email\n" +
            "  • More reliable\n" +
            "  • Configure in config.toml file"
        );
        infoArea.setEditable(false);
        infoArea.setBackground(new Color(245, 245, 245));
        infoArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(infoArea, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createProxyPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        proxiesEnabledCheck = new JCheckBox("Enable Proxies");
        proxiesEnabledCheck.setSelected(config.isProxiesEnabled());
        proxiesEnabledCheck.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(proxiesEnabledCheck, BorderLayout.NORTH);
        
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        JLabel proxyLabel = new JLabel("Proxy List (one per line: ip:port:username:password or ip:port):");
        centerPanel.add(proxyLabel, BorderLayout.NORTH);
        
        proxyArea = new JTextArea(10, 40);
        proxyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // Load existing proxies
        StringBuilder proxyText = new StringBuilder();
        for (Proxy proxy : config.getProxies()) {
            proxyText.append(proxy.getIp()).append(":").append(proxy.getPort());
            if (proxy.hasAuth()) {
                proxyText.append(":").append(proxy.getUsername())
                         .append(":").append(proxy.getPassword());
            }
            proxyText.append("\n");
        }
        proxyArea.setText(proxyText.toString());
        
        JScrollPane scrollPane = new JScrollPane(proxyArea);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel label = new JLabel("Activity Log:");
        label.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(label, BorderLayout.NORTH);
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void startAccountCreation() {
        // Validate inputs
        try {
            totalAccounts = Integer.parseInt(accountsField.getText());
            int threads = Integer.parseInt(threadsField.getText());
            
            if (totalAccounts <= 0 || threads <= 0) {
                JOptionPane.showMessageDialog(this,
                    "Accounts and threads must be greater than 0",
                    "Invalid Input", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Update config
            config.setAccountsToCreate(totalAccounts);
            config.setThreads(threads);
            config.setAccountPassword(passwordField.getText());
            config.setSet2fa(enable2FACheck.isSelected());
            config.setHeadless(headlessCheck.isSelected());
            config.setUseGuerrillaMail(useGuerrillaCheck.isSelected());
            config.setUseImap(useImapCheck.isSelected());
            config.setProxiesEnabled(proxiesEnabledCheck.isSelected());
            
            // Validate email method
            if (!config.isUseGuerrillaMail() && !config.isUseImap()) {
                JOptionPane.showMessageDialog(this,
                    "Please select an email verification method",
                    "Configuration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Parse proxies
            if (config.isProxiesEnabled()) {
                List<Proxy> proxies = parseProxies(proxyArea.getText());
                if (proxies.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        "Proxies enabled but no valid proxies provided",
                        "Configuration Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                config.setProxies(proxies);
            }
            
            config.validate();
            
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                "Invalid number format",
                "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Configuration error: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Start creation
        isRunning = true;
        completedAccounts = 0;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        
        updateStatus("Starting account creation...");
        logMessage("=".repeat(50));
        logMessage("Starting account creation");
        logMessage("Accounts to create: " + totalAccounts);
        logMessage("Threads: " + config.getThreads());
        logMessage("=".repeat(50));
        
        // Run in background thread
        new Thread(() -> {
            try {
                runAccountCreation();
            } catch (Exception e) {
                log.error("Error in account creation", e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(AccountCreatorSwingGUI.this,
                        "Error: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                    resetUI();
                });
            }
        }).start();
    }
    
    private void runAccountCreation() {
        executor = Executors.newFixedThreadPool(config.getThreads());
        List<Future<JagexAccount>> futures = new ArrayList<>();
        
        // Determine domains
        List<String> domains;
        if (config.isUseImap()) {
            domains = config.getImapDomains();
        } else {
            domains = config.getGuerrillaMailDomains();
        }
        
        // Submit tasks
        for (int i = 0; i < totalAccounts && isRunning; i++) {
            String email = AccountUtils.generateEmail(domains, 10);
            
            Proxy proxy = null;
            if (config.isProxiesEnabled() && !config.getProxies().isEmpty()) {
                proxy = config.getProxies().get(i % config.getProxies().size());
            }
            
            final Proxy finalProxy = proxy;
            final String finalEmail = email;
            final int accountNum = i + 1;
            
            Future<JagexAccount> future = executor.submit(() -> {
                try {
                    logMessage(String.format("[%d/%d] Creating account: %s", 
                        accountNum, totalAccounts, finalEmail));
                    
                    AccountCreator creator = new AccountCreator(config, finalEmail, finalProxy);
                    JagexAccount account = creator.registerAccount();
                    
                    repository.appendAccount(account);
                    
                    SwingUtilities.invokeLater(() -> {
                        completedAccounts++;
                        updateProgress();
                        logMessage(String.format("✓ [%d/%d] Successfully created: %s", 
                            completedAccounts, totalAccounts, finalEmail));
                    });
                    
                    return account;
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        completedAccounts++;
                        updateProgress();
                        logMessage(String.format("✗ [%d/%d] Failed: %s - %s", 
                            completedAccounts, totalAccounts, finalEmail, e.getMessage()));
                    });
                    return null;
                }
            });
            
            futures.add(future);
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        
        // Wait for completion
        for (Future<JagexAccount> future : futures) {
            if (!isRunning) {
                future.cancel(true);
            } else {
                try {
                    future.get(10, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    logMessage("Account creation timed out");
                    future.cancel(true);
                } catch (Exception e) {
                    // Already logged
                }
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        SwingUtilities.invokeLater(() -> {
            logMessage("=".repeat(50));
            logMessage("Account creation completed");
            logMessage("Total accounts: " + completedAccounts);
            logMessage("Saved to: " + new File(ACCOUNTS_FILE).getAbsolutePath());
            logMessage("=".repeat(50));
            updateStatus("Completed: " + completedAccounts + " accounts created");
            resetUI();
        });
    }
    
    private void stopAccountCreation() {
        isRunning = false;
        logMessage("Stopping account creation...");
        updateStatus("Stopping...");
        
        if (executor != null) {
            executor.shutdownNow();
        }
    }
    
    private void resetUI() {
        isRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        progressBar.setValue(0);
    }
    
    private void updateProgress() {
        int progress = (int) ((double) completedAccounts / totalAccounts * 100);
        progressBar.setValue(progress);
        updateStatus(String.format("Creating accounts: %d/%d", completedAccounts, totalAccounts));
    }
    
    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }
    
    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    private List<Proxy> parseProxies(String proxyText) {
        List<Proxy> proxies = new ArrayList<>();
        
        if (proxyText == null || proxyText.trim().isEmpty()) {
            return proxies;
        }
        
        for (String line : proxyText.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            String[] parts = line.split(":");
            if (parts.length >= 2) {
                Proxy.ProxyBuilder builder = Proxy.builder()
                    .ip(parts[0].trim())
                    .port(parts[1].trim());
                
                if (parts.length >= 4) {
                    builder.username(parts[2].trim())
                           .password(parts[3].trim());
                }
                
                proxies.add(builder.build());
            }
        }
        
        return proxies;
    }
    
    public static void main(String[] args) {
        try {
            // Set system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel
        }
        
        SwingUtilities.invokeLater(() -> {
            AccountCreatorSwingGUI gui = new AccountCreatorSwingGUI();
            gui.setVisible(true);
        });
    }
}
