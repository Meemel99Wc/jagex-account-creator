# Jagex Account Creator - Java Edition

A Java-based automation tool for creating Jagex accounts using Selenium WebDriver.

## Features

* **Fast Account Creation**: ~15 seconds per account without 2FA, ~30 seconds with 2FA
* **Multi-threaded**: Create multiple accounts in parallel
* **Headless Support**: Run browser in headless mode for automation
* **Email Options**: 
  * IMAP catch-all email support
  * Guerrilla Mail temporary email service
* **2FA Support**: Automatically enable TOTP 2FA on created accounts
* **Proxy Support**: Full proxy support with authentication
* **JSON Export**: All created accounts saved to `accounts.json`

## Requirements

* **Java**: JDK 17 or higher
* **Maven**: 3.6+ (for building)
* **Chrome Browser**: Must be installed on the system
* **ChromeDriver**: Automatically managed by WebDriverManager

## Setup

### 1. Clone/Download the Project

Download and extract the project files.

### 2. Install Dependencies

The project uses Maven to manage dependencies. Run:

```bash
mvn clean install
```

### 3. Configure

Edit `config.toml` in the root directory:

```toml
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
]

[email.imap]
ip = "mail.domain.com"
port = 993
email = "catchall@domain.com"
password = "yourPassword"
domains = ["yourdomain.com"]

[account]
password = "YourStrongPassword!123"
set_2fa = true

[proxies]
enabled = false
list = [
    # { ip = "proxy.ip", port = "8080", username = "user", password = "pass"},
]
```

### Important Configuration Notes

* **User Agent**: Get your Chrome user agent from `chrome://version` and update the config
* **Email**: Choose either IMAP or Guerrilla Mail (not both)
* **Proxies**: Optional, but recommended for bulk account creation

## IMAP Setup

If using IMAP, you need a catch-all email configuration:

1. Your IMAP server must forward all emails for your domain(s) to a single inbox
2. Configure the catch-all email credentials in `config.toml`
3. List all domains you want to use for account creation

Example:
- Catch-all: `catchall@mydomain.com`
- Domains: `["mydomain.com", "myotherdomain.net"]`
- Creator generates: `abc123@mydomain.com`
- Email arrives at: `catchall@mydomain.com`

Options for catch-all email:
* Self-host [docker-mailserver](https://github.com/docker-mailserver/docker-mailserver)
* Purchase from [mxroute](https://mxroute.com/)
* Other email hosting providers with catch-all support

## Guerrilla Mail Setup

Guerrilla Mail is simpler - just use the provided domains in the config. No additional setup required.

## Running

### Build the JAR

```bash
mvn clean package
```

This creates two JAR files in the `target/` directory:
* `account-creator-1.0.0.jar` - Regular JAR
* `account-creator-1.0.0-jar-with-dependencies.jar` - Fat JAR with all dependencies

### Run the Application

Using the fat JAR (recommended):

```bash
java -jar target/account-creator-1.0.0-jar-with-dependencies.jar
```

Or specify a custom config file:

```bash
java -jar target/account-creator-1.0.0-jar-with-dependencies.jar /path/to/config.toml
```

### Run with Maven

```bash
mvn exec:java -Dexec.mainClass="com.jagex.accountcreator.Main"
```

## Output

Created accounts are saved to `accounts.json` in the following format:

```json
[
  {
    "email": "abc123@sharklasers.com",
    "password": "YourPassword!123",
    "birthday": {
      "day": 15,
      "month": 6,
      "year": 1995
    },
    "real_ip": "203.0.113.42",
    "proxy": {
      "ip": "proxy.example.com",
      "port": "8080",
      "username": "proxyuser",
      "password": "proxypass"
    },
    "tfa": {
      "setupKey": "JBSWY3DPEHPK3PXP",
      "backupCodes": [
        "12345678",
        "87654321",
        ...
      ]
    }
  }
]
```

## Troubleshooting

### Cloudflare Challenge

If you encounter Cloudflare challenges:
1. Ensure your `user_agent` matches your Chrome version exactly
2. Your IP may be flagged - try using a proxy
3. The application will attempt to click through turnstile challenges

### ChromeDriver Issues

The application uses WebDriverManager to automatically download the correct ChromeDriver. If you have issues:
* Ensure Chrome is installed and up-to-date
* Check your internet connection
* Try manually specifying ChromeDriver location in the code

### Timeout Errors

If registration times out:
* Increase `element_wait_timeout` in config
* Check your internet connection
* Verify the Jagex website is accessible

### Headless Mode Issues

If headless mode fails:
* Ensure you're using a recent Chrome version
* Set `headless = false` for debugging
* Check if user agent is properly set

## Project Structure

```
jagex-account-creator/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── jagex/
│       │           └── accountcreator/
│       │               ├── Main.java                 # Entry point
│       │               ├── config/
│       │               │   └── Configuration.java    # Config loader
│       │               ├── exception/
│       │               │   └── RegistrationException.java
│       │               ├── model/                    # Data models
│       │               │   ├── Birthday.java
│       │               │   ├── IMAPDetails.java
│       │               │   ├── JagexAccount.java
│       │               │   ├── Proxy.java
│       │               │   └── TwoFactorAuth.java
│       │               ├── repository/
│       │               │   └── AccountRepository.java # JSON persistence
│       │               ├── service/
│       │               │   ├── AccountCreator.java    # Main creator
│       │               │   ├── GuerrillaMailService.java
│       │               │   └── IMAPService.java
│       │               └── util/
│       │                   └── AccountUtils.java      # Utilities
│       └── resources/
│           └── logback.xml                            # Logging config
├── pom.xml                                            # Maven config
├── config.toml                                        # Application config
└── README.md
```

## Dependencies

* **Selenium WebDriver**: Browser automation
* **WebDriverManager**: Automatic driver management
* **Jakarta Mail**: IMAP support
* **OkHttp**: HTTP requests for Guerrilla Mail
* **Jackson**: JSON processing
* **TOML4J**: Configuration parsing
* **OTP-Java**: TOTP 2FA generation
* **Logback**: Logging
* **Lombok**: Boilerplate reduction

## Known Issues

* Random usernames may occasionally be rejected by Jagex
* Failed runs may leave Chrome processes (especially in headless mode)
* Cloudflare protection may occasionally trigger

## License

This project is for educational purposes only. Use responsibly and in accordance with Jagex's terms of service.

## Contributing

Contributions welcome! Please submit pull requests or open issues for bugs/features.

## Disclaimer

This tool is provided as-is for educational and automation purposes. The authors are not responsible for any misuse or violations of terms of service. Always ensure you comply with Jagex's terms of service and applicable laws when using automation tools.
