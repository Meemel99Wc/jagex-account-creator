package com.jagex.accountcreator.config;

import com.jagex.accountcreator.model.IMAPDetails;
import com.jagex.accountcreator.model.Proxy;
import com.moandjiezana.toml.Toml;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration loader for the account creator
 */
@Data
public class Configuration {
    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    // Default settings
    private int accountsToCreate = 1;
    private int threads = 1;
    private String logLevel = "INFO";

    // Browser settings
    private boolean headless = false;
    private boolean enableDevTools = false;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";
    private int elementWaitTimeout = 30;
    private double cacheUpdateThreshold = 0.3;

    // Email settings
    private boolean useImap = false;
    private boolean useGuerrillaMail = true;
    private boolean useProxyForGuerrillaMail = true;
    private List<String> guerrillaMailDomains = new ArrayList<>();
    private IMAPDetails imapDetails;
    private List<String> imapDomains = new ArrayList<>();

    // Account settings
    private String accountPassword = "mIbombo9990!";
    private boolean set2fa = true;

    // Proxy settings
    private boolean proxiesEnabled = true;
    private List<Proxy> proxies = new ArrayList<>();

    /**
     * Load configuration from TOML file
     */
    public static Configuration loadFromFile(String configPath) throws IOException {
        log.info("Loading configuration from: {}", configPath);

        File file = new File(configPath);
        if (!file.exists()) {
            log.warn("Config file not found at {}, creating default config", configPath);
            createDefaultConfig(configPath);
            file = new File(configPath);
        }

        Toml toml = new Toml().read(file);
        Configuration config = new Configuration();

        // Default settings
        if (toml.contains("default")) {
            Toml defaultSection = toml.getTable("default");
            config.accountsToCreate = defaultSection.getLong("accounts_to_create", 1L).intValue();
            config.threads = defaultSection.getLong("threads", 1L).intValue();
            config.logLevel = defaultSection.getString("log_level", "INFO");
        }

        // Browser settings
        if (toml.contains("browser")) {
            Toml browserSection = toml.getTable("browser");
            config.headless = browserSection.getBoolean("headless", false);
            config.enableDevTools = browserSection.getBoolean("enable_dev_tools", false);
            config.userAgent = browserSection.getString("user_agent", config.userAgent);
            config.elementWaitTimeout = browserSection.getLong("element_wait_timeout", 30L).intValue();
            config.cacheUpdateThreshold = browserSection.getDouble("cache_update_threshold", 0.3);
        }

        // Email settings
        if (toml.contains("email")) {
            Toml emailSection = toml.getTable("email");
            config.useImap = emailSection.getBoolean("use_imap", false);
            config.useGuerrillaMail = emailSection.getBoolean("use_guerrilla_mail", true);

            // Guerrilla Mail settings
            if (emailSection.contains("guerrilla_mail")) {
                Toml gmSection = emailSection.getTable("guerrilla_mail");
                config.useProxyForGuerrillaMail = gmSection.getBoolean("use_proxy", true);
                config.guerrillaMailDomains = gmSection.getList("domains", new ArrayList<>());
            }

            // IMAP settings
            if (emailSection.contains("imap")) {
                Toml imapSection = emailSection.getTable("imap");
                config.imapDetails = IMAPDetails.builder()
                        .ip(imapSection.getString("ip"))
                        .port(imapSection.getLong("port").intValue())
                        .email(imapSection.getString("email"))
                        .password(imapSection.getString("password"))
                        .build();
                config.imapDomains = imapSection.getList("domains", new ArrayList<>());
            }
        }

        // Account settings
        if (toml.contains("account")) {
            Toml accountSection = toml.getTable("account");
            config.accountPassword = accountSection.getString("password", "OsrsEnjoyer!123");
            config.set2fa = accountSection.getBoolean("set_2fa", true);
        }

        // Proxy settings
        if (toml.contains("proxies")) {
            Toml proxiesSection = toml.getTable("proxies");
            config.proxiesEnabled = proxiesSection.getBoolean("enabled", false);

            log.info("Proxies enabled in config: {}", config.proxiesEnabled);

            List<Map<String, Object>> proxyList = proxiesSection.getList("list", new ArrayList<>());
            log.info("Found {} proxies in config file", proxyList.size());

            for (Map<String, Object> proxyMap : proxyList) {
                Proxy proxy = Proxy.builder()
                        .ip((String) proxyMap.get("ip"))
                        .port((String) proxyMap.get("port"))
                        .username((String) proxyMap.get("username"))
                        .password((String) proxyMap.get("password"))
                        .build();
                config.proxies.add(proxy);
                log.info("Loaded proxy: {}:{} (has auth: {})", proxy.getIp(), proxy.getPort(), proxy.hasAuth());
            }
        }

        log.info("Configuration loaded successfully - Total proxies loaded: {}", config.proxies.size());
        return config;
    }

    /**
     * Create default configuration file
     */
    private static void createDefaultConfig(String configPath) throws IOException {
        String defaultConfig = """
[default]
accounts_to_create = 1
threads = 1
log_level = "INFO"

[browser]
headless = false
enable_dev_tools = false
user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
element_wait_timeout = 30
cache_update_threshold = 0.3

[email]
use_imap = false
use_guerrilla_mail = true

[email.guerrilla_mail]
use_proxy = true
domains = [
    "sharklasers.com",
    "guerrillamail.info",
    "grr.la",
    "guerrillamail.biz",
    "guerrillamail.com",
    "guerrillamail.de",
    "guerrillamail.net",
    "guerrillamail.org",
    "pokemail.net",
    "spam4.me",
]

[email.imap]
ip = "mail.domain.com"
port = 993
email = "catchall@domain.com"
password = "myCatchAllEmailPassword"
domains = []

[account]
password = "OsrsEnjoyer!123"
set_2fa = true

[proxies]
enabled = false
list = [
    { ip = "198.23.239.134", port = "6540", username = "nomoclbn", password = "svrxc74ez67h"},
    { ip = "45.38.107.97", port = "6014", username = "nomoclbn", password = "svrxc74ez67h"},
    { ip = "107.172.163.27", port = "6543", username = "nomoclbn", password = "svrxc74ez67h"},
    { ip = "198.105.121.200", port = "6462", username = "nomoclbn", password = "svrxc74ez67h"},
    { ip = "64.137.96.74", port = "6641", username = "nomoclbn", password = "svrxc74ez67h"},
    { ip = "216.10.27.159", port = "6837", username = "nomoclbn", password = "svrxc74ez67h"},
    { ip = "23.26.71.145", port = "5628", username = "nomoclbn", password = "svrxc74ez67h"},
    { ip = "23.229.19.94", port = "8689", username = "nomoclbn", password = "svrxc74ez67h"},
]
""";

        Path path = Paths.get(configPath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, defaultConfig);
        log.info("Created default configuration at: {}", configPath);
    }

    public void validate() {
        if (useImap && useGuerrillaMail) {
            throw new IllegalStateException("Cannot use both IMAP and Guerrilla Mail");
        }

        if (!useImap && !useGuerrillaMail) {
            throw new IllegalStateException("Must enable either IMAP or Guerrilla Mail");
        }

        if (useImap && (imapDetails == null || imapDomains.isEmpty())) {
            throw new IllegalStateException("IMAP enabled but details or domains not configured");
        }

        if (useGuerrillaMail && guerrillaMailDomains.isEmpty()) {
            throw new IllegalStateException("Guerrilla Mail enabled but no domains configured");
        }
    }
}