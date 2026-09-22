package com.jagex.accountcreator.service;

import com.bastiaanjansen.otp.TOTPGenerator;
import com.jagex.accountcreator.config.Configuration;
import com.jagex.accountcreator.exception.RegistrationException;
import com.jagex.accountcreator.model.*;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Main account creator service that handles Jagex account registration
 * Enhanced with better Cloudflare handling
 */
public class AccountCreator {
    private static final Logger log = LoggerFactory.getLogger(AccountCreator.class);

    private static final String REGISTRATION_URL = "https://account.jagex.com/en-GB/login/registration-start";
    private static final String MANAGEMENT_URL = "https://account.jagex.com/en-GB/manage/profile";
    private static final String IPIFY_URL = "https://api64.ipify.org/?format=raw";

    private final Configuration config;
    private final String accountEmail;
    private final String accountUsername;
    private final Proxy proxy;
    private final IMAPService imapService;
    private final GuerrillaMailService guerrillaMailService;
    private LocalProxyServer localProxyServer;

    public AccountCreator(Configuration config, String accountEmail, Proxy proxy) {
        this.config = config;
        this.accountEmail = accountEmail;
        this.accountUsername = accountEmail.split("@")[0];
        this.proxy = proxy;

        // Log proxy information
        if (proxy != null) {
            log.info("AccountCreator initialized with proxy: {}:{} (auth: {})",
                    proxy.getIp(), proxy.getPort(), proxy.hasAuth());
        } else {
            log.warn("AccountCreator initialized WITHOUT proxy!");
        }

        if (config.isUseImap()) {
            this.imapService = new IMAPService(config.getImapDetails());
            this.guerrillaMailService = null;
        } else {
            this.imapService = null;
            Proxy gmProxy = config.isUseProxyForGuerrillaMail() ? proxy : null;
            this.guerrillaMailService = new GuerrillaMailService(gmProxy);
        }
    }
    private WebDriver createWebDriver() throws Exception {
        log.debug("Creating WebDriver");

        // Setup ChromeDriver
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // Basic options
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-features=OptimizationGuideModelDownloading,OptimizationHintsFetching,OptimizationTargetPrediction,OptimizationHints");

        // Prevent WebRTC IP leaks
        options.addArguments("--enforce-webrtc-ip-permission-check");
        options.addArguments("--force-webrtc-ip-handling-policy=disable_non_proxied_udp");

        // Prevent DNS leaks
        options.addArguments("--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE 127.0.0.1");

        // Additional privacy options
        options.addArguments("--disable-webgl");
        options.addArguments("--disable-webrtc");

        // User agent
        if (config.getUserAgent() != null && !config.getUserAgent().isEmpty()) {
            options.addArguments("--user-agent=" + config.getUserAgent());
        }

        // Headless mode
        if (config.isHeadless()) {
            options.addArguments("--headless=new");
        }

        // Proxy configuration
        log.info("Checking proxy configuration - proxy object is: {}", proxy == null ? "NULL" : "NOT NULL");
        if (proxy != null && proxy.hasAuth()) {
            try {
                log.info("Proxy details - IP: {}, Port: {}, Has Auth: {}",
                        proxy.getIp(), proxy.getPort(), proxy.hasAuth());

                // Create and start local proxy server
                localProxyServer = new LocalProxyServer(proxy);
                localProxyServer.start();

                // Give it a moment to start
                Thread.sleep(500);

                // Use the local proxy
                String localProxyUrl = "http://127.0.0.1:" + localProxyServer.getLocalPort();
                options.addArguments("--proxy-server=" + localProxyUrl);
                log.info("Using local proxy server at {} forwarding to {}:{}",
                        localProxyUrl, proxy.getIp(), proxy.getPort());

            } catch (Exception e) {
                log.error("Failed to create local proxy server", e);
                throw new RuntimeException("Proxy setup failed", e);
            }
        } else if (proxy != null) {
            // Unauthenticated proxy - use directly
            String proxyUrl = "http://" + proxy.getIp() + ":" + proxy.getPort();
            options.addArguments("--proxy-server=" + proxyUrl);
            log.info("Using UNAUTHENTICATED proxy: {}", proxyUrl);
        } else {
            log.warn("NO PROXY configured - using direct connection!");
        }

        // Additional preferences
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.getElementWaitTimeout()));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));

        return driver;
    }
    /**
     * Main entry point for account registration
     */
    public JagexAccount registerAccount() throws Exception {
        log.info("Starting registration for: {}", accountEmail);

        WebDriver driver = null;
        File proxyExtension = null;
        try {
            driver = createWebDriver();

            // Get browser IP
            String browserIp = getBrowserIp(driver);
            log.info("Browser IP: {}", browserIp);

            // Create account object
            Random random = new Random();
            JagexAccount account = JagexAccount.builder()
                    .email(accountEmail)
                    .password(config.getAccountPassword())
                    .birthday(Birthday.builder()
                            .day(random.nextInt(25) + 1)
                            .month(random.nextInt(12) + 1)
                            .year(random.nextInt(32) + 1979)
                            .build())
                    .realIp(browserIp)
                    .proxy(proxy)
                    .build();

            // Perform registration
            performRegistration(driver, account);

            // Setup 2FA if enabled
            if (config.isSet2fa()) {
                setup2FA(driver, account);
            }

            log.info("Registration completed successfully for: {}", accountEmail);
            return account;

        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Error closing driver: {}", e.getMessage());
                }
            }
            // Stop local proxy server if running
            if (localProxyServer != null) {
                try {
                    localProxyServer.stop();
                } catch (Exception e) {
                    log.warn("Error stopping local proxy server: {}", e.getMessage());
                }
            }
            // Clean up proxy extension
            if (proxyExtension != null && proxyExtension.exists()) {
                try {
                    proxyExtension.delete();
                } catch (Exception e) {
                    log.warn("Error deleting proxy extension: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Create a Chrome extension for proxy authentication
     */
    private File createProxyAuthExtension(Proxy proxy) throws Exception {
        String manifest = """
{
    "version": "1.0.0",
    "manifest_version": 2,
    "name": "Chrome Proxy",
    "permissions": [
        "proxy",
        "tabs",
        "unlimitedStorage",
        "storage",
        "<all_urls>",
        "webRequest",
        "webRequestBlocking"
    ],
    "background": {
        "scripts": ["background.js"]
    },
    "minimum_chrome_version":"22.0.0"
}
""";

        String background = String.format("""
var config = {
    mode: "fixed_servers",
    rules: {
        singleProxy: {
            scheme: "http",
            host: "%s",
            port: parseInt(%s)
        },
        bypassList: ["localhost"]
    }
};

chrome.proxy.settings.set({value: config, scope: "regular"}, function() {});

function callbackFn(details) {
    return {
        authCredentials: {
            username: "%s",
            password: "%s"
        }
    };
}

chrome.webRequest.onAuthRequired.addListener(
    callbackFn,
    {urls: ["<all_urls>"]},
    ['blocking']
);
""", proxy.getIp(), proxy.getPort(),
                proxy.getUsername() != null ? proxy.getUsername() : "",
                proxy.getPassword() != null ? proxy.getPassword() : "");

        // Create temporary directory for extension
        Path tempDir = Files.createTempDirectory("proxy_auth_");
        File manifestFile = new File(tempDir.toFile(), "manifest.json");
        File backgroundFile = new File(tempDir.toFile(), "background.js");

        Files.writeString(manifestFile.toPath(), manifest, StandardCharsets.UTF_8);
        Files.writeString(backgroundFile.toPath(), background, StandardCharsets.UTF_8);

        // Create zip file
        File zipFile = new File(tempDir.toFile().getParent(), "proxy_auth_" + System.currentTimeMillis() + ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            addToZip(zos, manifestFile, "manifest.json");
            addToZip(zos, backgroundFile, "background.js");
        }

        // Clean up temp files
        manifestFile.delete();
        backgroundFile.delete();
        tempDir.toFile().delete();

        log.debug("Created proxy auth extension: {}", zipFile.getAbsolutePath());
        return zipFile;
    }

    /**
     * Add file to zip
     */
    private void addToZip(ZipOutputStream zos, File file, String name) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        byte[] bytes = Files.readAllBytes(file.toPath());
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
    }

    /**
     * Set value using JavaScript (bypasses readonly/disabled states)
     */
    private void setValueWithJS(WebDriver driver, WebElement element, String value) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].value = arguments[1];", element, value);
        // Trigger input event to notify any listeners
        js.executeScript("arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", element);
    }

    /**
     * Safely click an element with JavaScript fallback
     */
    private void safeClick(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            log.debug("Normal click failed, using JavaScript click");
            JavascriptExecutor executor = (JavascriptExecutor) driver;
            executor.executeScript("arguments[0].click();", element);
        }
    }

    /**
     * Scroll element into view
     */
    private void scrollIntoView(WebDriver driver, WebElement element) {
        JavascriptExecutor executor = (JavascriptExecutor) driver;
        executor.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
    }

    /**
     * Check if Cloudflare challenge is present
     * Extremely conservative to avoid false positives
     */
    private boolean isCloudflareChallenge(WebDriver driver) {
        try {
            String pageSource = driver.getPageSource();
            String pageTitle = driver.getTitle();

            log.debug("Checking for Cloudflare - Title: '{}'", pageTitle);

            // Check for actual block message (Python version check)
            if (pageSource.contains("Sorry, you have been blocked")) {
                log.warn("Detected Cloudflare BLOCK message");
                return true;
            }

            // ONLY check for very specific challenge indicators
            // Title check is case-sensitive for exact match
            if (pageTitle.equals("Just a moment...") || pageTitle.equals("Attention Required! | Cloudflare")) {
                log.warn("Detected Cloudflare challenge title: {}", pageTitle);
                return true;
            }

            // Check for actual challenge iframe (most reliable)
            try {
                int challengeIframes = driver.findElements(By.cssSelector("iframe[src*='challenges.cloudflare.com']")).size();
                if (challengeIframes > 0) {
                    log.warn("Detected {} Cloudflare challenge iframe(s)", challengeIframes);
                    return true;
                }
            } catch (Exception e) {
                // Ignore
            }

            log.debug("No Cloudflare challenge detected");
            return false;
        } catch (Exception e) {
            log.warn("Error checking for Cloudflare: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Wait for Cloudflare challenge to be solved (either automatically or manually)
     */
    private boolean waitForCloudflareResolution(WebDriver driver, int timeoutSeconds) {
        log.info("Cloudflare challenge detected - waiting for resolution...");

        // If not headless, user can solve it manually
        if (!config.isHeadless()) {
            log.info("Browser is visible - you can solve the Cloudflare challenge manually");
            log.info("Waiting up to {} seconds for challenge to be resolved...", timeoutSeconds);
        }

        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        int checkCount = 0;

        while (System.currentTimeMillis() < endTime) {
            try {
                checkCount++;

                // Check if we've passed the Cloudflare check
                if (!isCloudflareChallenge(driver)) {
                    // Verify we're actually on the registration page
                    String currentUrl = driver.getCurrentUrl();
                    String title = driver.getTitle();

                    log.debug("Challenge appears resolved - URL: {}, Title: {}", currentUrl, title);

                    // Check if we're on the right page
                    if (currentUrl.contains("registration") || title.contains("Create a Jagex account")) {
                        log.info("Cloudflare challenge resolved successfully!");
                        return true;
                    } else {
                        log.debug("Challenge resolved but not on registration page yet, waiting...");
                    }
                }

                // Log progress every 10 seconds in visible mode
                if (!config.isHeadless() && checkCount % 10 == 0) {
                    int elapsed = checkCount;
                    int remaining = timeoutSeconds - elapsed;
                    log.info("Still waiting for challenge resolution... ({} seconds remaining)", remaining);
                }

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.debug("Error while waiting for Cloudflare: {}", e.getMessage());
            }
        }

        log.warn("Cloudflare challenge was not resolved within {} seconds", timeoutSeconds);
        return false;
    }

    /**
     * Handle cookie consent dialog
     */
    private void handleCookieConsent(WebDriver driver) {
        try {
            log.debug("Checking for cookie consent dialog");
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));

            // List of possible cookie consent button selectors
            List<By> cookieSelectors = Arrays.asList(
                    By.id("CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll"),
                    By.id("CybotCookiebotDialogBodyButtonAccept"),
                    By.cssSelector(".CybotCookiebotDialogBodyButton"),
                    By.xpath("//a[contains(@id, 'CybotCookiebotDialogBodyButton')]"),
                    By.xpath("//button[contains(text(), 'Accept') or contains(text(), 'Allow')]")
            );

            for (By selector : cookieSelectors) {
                try {
                    WebElement cookieButton = shortWait.until(ExpectedConditions.elementToBeClickable(selector));
                    log.info("Found cookie consent button, clicking it");
                    safeClick(driver, cookieButton);
                    Thread.sleep(1000); // Wait for dialog to disappear
                    log.info("Cookie consent accepted");
                    return;
                } catch (Exception e) {
                    // Try next selector
                    continue;
                }
            }

            log.debug("No cookie consent dialog found, continuing");

        } catch (Exception e) {
            log.debug("Error checking for cookie consent: {}", e.getMessage());
            // Continue anyway - if there's no cookie dialog, that's fine
        }
    }


    /**
     * Generate random birthday (age 18-80)
     */
    private Birthday generateRandomBirthday() {
        Random random = new Random();
        int currentYear = java.time.Year.now().getValue();

        int year = currentYear - (18 + random.nextInt(63)); // 18 to 80 years old
        int month = 1 + random.nextInt(12);
        int day = 1 + random.nextInt(28); // Safe for all months

        return Birthday.builder()
                .day(day)
                .month(month)
                .year(year)
                .build();
    }

    /**
     * Setup WebDriver with appropriate options
     */
    private WebDriver setupWebDriver() throws Exception {
        log.info("Setting up WebDriver");

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // Basic options
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        // User agent to appear more legitimate
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Headless mode
        if (config.isHeadless()) {
            options.addArguments("--headless=new");
            log.info("Running in headless mode");
        } else {
            log.info("Running in visible mode (can manually solve Cloudflare)");
        }

        // Proxy setup
        if (proxy != null) {
            if (proxy.hasAuth()) {
                log.info("Setting up authenticated proxy via extension");
                File proxyExtension = createProxyAuthExtension(proxy);
                options.addExtensions(proxyExtension);
            } else {
                log.info("Setting up unauthenticated proxy");
                options.addArguments("--proxy-server=" + proxy.getIp() + ":" + proxy.getPort());
            }
        }

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        log.info("WebDriver setup complete");
        return driver;
    }

    /**
     * Get real IP address
     */
    private String getRealIP(WebDriver driver) {
        try {
            log.debug("Getting real IP address");
            driver.get(IPIFY_URL);
            Thread.sleep(2000);
            String ip = driver.findElement(By.tagName("body")).getText().trim();
            log.info("Real IP: {}", ip);
            return ip;
        } catch (Exception e) {
            log.warn("Failed to get real IP: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Perform the main registration flow with enhanced Cloudflare handling
     */
    private void performRegistration(WebDriver driver, JagexAccount account) throws Exception {
        log.info("Starting registration flow");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(config.getElementWaitTimeout()));

        // Navigate to registration page
        driver.get(REGISTRATION_URL);
        Thread.sleep(2000); // Give page time to load

        // Check for Cloudflare block - check multiple indicators
        String pageSource = driver.getPageSource();
        String pageTitle = driver.getTitle();

        if (pageSource.contains("Sorry, you have been blocked") ||


                pageTitle.contains("Access denied")
                ) {
            log.warn("Cloudflare block detected - proxy IP may be flagged");
            throw new RegistrationException("CLOUDFLARE_BLOCK");
        }
        if( pageSource.contains("Ray ID:")){
            log.warn("Detected Cloudflare click to continue");
            wait.until(ExpectedConditions.titleContains("Create a Jagex account"));
        }

        wait.until(ExpectedConditions.titleContains("Create a Jagex account"));

        // Handle cookie consent dialog before interacting with form
        handleCookieConsent(driver);

        // Extra wait after cookie consent to ensure page is stable
        Thread.sleep(2000);

        // Fill in email - target the actual input element, not the label span
        log.debug("Filling in email");
        WebElement emailField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[type='email'][id='email']")));
        scrollIntoView(driver, emailField);
        Thread.sleep(500);
        emailField.click();
        Thread.sleep(300);
        emailField.sendKeys(account.getEmail());

        // Fill in birthday - click then type
        log.debug("Filling in birthday");
        WebElement dayField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[id='registration-start-form--field-day']")));
        scrollIntoView(driver, dayField);
        Thread.sleep(300);
        dayField.click();
        Thread.sleep(200);
        dayField.sendKeys(String.valueOf(account.getBirthday().getDay()));

        WebElement monthField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[id='registration-start-form--field-month']")));
        scrollIntoView(driver, monthField);
        Thread.sleep(300);
        monthField.click();
        Thread.sleep(200);
        monthField.sendKeys(String.valueOf(account.getBirthday().getMonth()));

        WebElement yearField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[id='registration-start-form--field-year']")));
        scrollIntoView(driver, yearField);
        Thread.sleep(300);
        yearField.click();
        Thread.sleep(200);
        yearField.sendKeys(String.valueOf(account.getBirthday().getYear()));

        // Accept terms checkbox
        log.debug("Clicking accept terms checkbox");
        WebElement termsCheckbox = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("registration-start-accept-agreements")));
        scrollIntoView(driver, termsCheckbox);
        safeClick(driver, termsCheckbox);

        // Submit form
        log.debug("Clicking continue button");
        WebElement continueButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("registration-start-form--continue-button")));
        scrollIntoView(driver, continueButton);
        safeClick(driver, continueButton);

        // Wait for page transition
        Thread.sleep(3000);

        // Get verification code
        log.info("Waiting for verification code...");
        String verificationCode;
        if (config.isUseImap()) {
            verificationCode = imapService.getVerificationCode(accountUsername, 120);
        } else {
            verificationCode = guerrillaMailService.getVerificationCode(accountEmail, 120);
        }
        log.info("Got verification code: {}", verificationCode);

        // Enter verification code
        WebElement codeField = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("registration-verify-form-code-input")));
        codeField.click();
        codeField.sendKeys(verificationCode);

        WebElement verifyButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("registration-verify-form-continue-button")));
        safeClick(driver, verifyButton);
        Thread.sleep(3000);

        // Enter display name
        log.debug("Setting display name");
        WebElement displayNameField = wait.until(ExpectedConditions.elementToBeClickable(By.id("displayName")));
        displayNameField.click();
        displayNameField.sendKeys(accountUsername);

        WebElement displayNameContinue = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("registration-account-name-form--continue-button")));
        safeClick(driver, displayNameContinue);
        Thread.sleep(3000);

        // Set password
        log.debug("Setting password");
        WebElement passwordField = wait.until(ExpectedConditions.elementToBeClickable(By.id("password")));
        passwordField.click();
        passwordField.sendKeys(account.getPassword());

        WebElement repasswordField = wait.until(ExpectedConditions.elementToBeClickable(By.id("repassword")));
        repasswordField.click();
        repasswordField.sendKeys(account.getPassword());

        WebElement createButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("registration-password-form--create-account-button")));
        safeClick(driver, createButton);

        // Wait for completion
        wait.until(ExpectedConditions.titleContains("Registration completed"));
        log.info("Registration completed successfully");
    }


    private String getBrowserIp(WebDriver driver) throws Exception {
        log.debug("Getting browser IP from: {}", IPIFY_URL);
        driver.get(IPIFY_URL);
        Thread.sleep(1000);

        WebElement preElement = driver.findElement(By.tagName("pre"));
        String ip = preElement.getText().trim();

        if (ip.isEmpty()) {
            throw new RegistrationException("Failed to get browser IP");
        }

        log.info("IP check via ipify: {}", ip);

        // Additional IP check via different service
        try {
            driver.get("https://icanhazip.com");
            Thread.sleep(1000);
            WebElement body = driver.findElement(By.tagName("body"));
            String ip2 = body.getText().trim();
            log.info("IP check via icanhazip: {}", ip2);
        } catch (Exception e) {
            log.warn("Secondary IP check failed: {}", e.getMessage());
        }

        return ip;
    }
    /**
     * Setup 2FA for the account
     */
    private void setup2FA(WebDriver driver, JagexAccount account) throws Exception {
        log.info("Setting up 2FA");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(config.getElementWaitTimeout()));

        // Navigate to management page
        driver.get(MANAGEMENT_URL);
        Thread.sleep(3000);

        // Click enable 2FA button
        WebElement enable2FAButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='mfa-enable-totp-button']")));
        enable2FAButton.click();
        Thread.sleep(1000);

        // Show secret key
        WebElement showSecretButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("authentication-setup-show-secret")));
        showSecretButton.click();
        Thread.sleep(500);

        // Get setup key
        WebElement secretKeyElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("authentication-setup-secret-key")));
        String setupKey = secretKeyElement.getText().trim();
        log.debug("Got 2FA setup key: {}", setupKey);

        // Continue to QR code
        WebElement qrButton = driver.findElement(By.cssSelector("[data-testid='authenticator-setup-qr-button']"));
        qrButton.click();
        Thread.sleep(1000);

        // Generate TOTP code
        TOTPGenerator totpGenerator = new TOTPGenerator.Builder(setupKey.getBytes())
                .withHOTPGenerator(builder -> {
                    builder.withPasswordLength(6);
                })
                .withPeriod(Duration.ofSeconds(30))
                .build();
        String totpCode = totpGenerator.now();
        log.debug("Generated TOTP code: {}", totpCode);

        // Enter TOTP code
        WebElement totpField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("authentication-setup-verification-code")));
        totpField.click();
        totpField.sendKeys(totpCode);

        // Submit
        WebElement submitButton = driver.findElement(
                By.cssSelector("[data-testid='authentication-setup-qr-code-submit-button']"));
        submitButton.click();
        Thread.sleep(2000);

        // Get backup codes
        WebElement backupCodesElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("authentication-setup-complete-codes")));
        String backupCodesText = backupCodesElement.getText();
        List<String> backupCodes = Arrays.asList(backupCodesText.split("\n"));
        log.debug("Got backup codes: {}", backupCodes);

        // Set 2FA on account
        account.setTfa(TwoFactorAuth.builder()
                .setupKey(setupKey)
                .backupCodes(backupCodes)
                .build());

        log.info("2FA setup completed");
    }
}