# Jagex Account Creator - Setup Guide

This guide will walk you through setting up and running the Jagex Account Creator Java application.

## Prerequisites

### 1. Java Development Kit (JDK)

**Required Version**: JDK 17 or higher

**Installation**:

- **Windows**: Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
- **macOS**: 
  ```bash
  brew install openjdk@17
  ```
- **Linux**:
  ```bash
  sudo apt install openjdk-17-jdk  # Ubuntu/Debian
  sudo yum install java-17-openjdk # RHEL/CentOS
  ```

**Verify Installation**:
```bash
java -version
```

Should output something like:
```
openjdk version "17.0.x" ...
```

### 2. Apache Maven

**Required Version**: Maven 3.6 or higher

**Installation**:

- **Windows**: Download from [Maven website](https://maven.apache.org/download.cgi) and follow [installation guide](https://maven.apache.org/install.html)
- **macOS**:
  ```bash
  brew install maven
  ```
- **Linux**:
  ```bash
  sudo apt install maven  # Ubuntu/Debian
  sudo yum install maven  # RHEL/CentOS
  ```

**Verify Installation**:
```bash
mvn -version
```

### 3. Google Chrome

The application requires Google Chrome to be installed on your system. ChromeDriver will be downloaded automatically.

**Installation**:
- Download from [google.com/chrome](https://www.google.com/chrome/)

## Quick Start

### 1. Extract the Project

Extract the downloaded ZIP file to your desired location.

### 2. Configure the Application

Edit `config.toml` in the project root directory:

```toml
[default]
accounts_to_create = 1  # Number of accounts to create
threads = 1             # Number of parallel threads

[browser]
headless = false        # Set to true for headless operation
user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"

[email]
use_imap = false           # Use IMAP email
use_guerrilla_mail = true  # Use Guerrilla Mail (easier to start with)

[account]
password = "YourPassword!123"  # Password for all created accounts
set_2fa = true                  # Enable 2FA on accounts
```

**Important**: 
- Get your Chrome user agent by visiting `chrome://version` in Chrome browser
- Copy the "User Agent" string and paste it into the config

### 3. Build the Project

**On Windows**:
```bash
build.bat
```

**On macOS/Linux**:
```bash
chmod +x build.sh
./build.sh
```

**Or manually**:
```bash
mvn clean package
```

This will:
- Download all dependencies
- Compile the code
- Create executable JAR file in `target/` directory

### 4. Run the Application

**On Windows**:
```bash
run.bat
```

**On macOS/Linux**:
```bash
chmod +x run.sh
./run.sh
```

**Or manually**:
```bash
java -jar target/account-creator-1.0.0-jar-with-dependencies.jar
```

## Email Configuration

### Option 1: Guerrilla Mail (Recommended for Testing)

This is the easiest option - no setup required!

```toml
[email]
use_imap = false
use_guerrilla_mail = true

[email.guerrilla_mail]
use_proxy = true
domains = [
    "sharklasers.com",
    "guerrillamail.com",
    # ... more domains
]
```

The application will automatically:
1. Generate random email addresses
2. Receive verification codes
3. Complete registration

### Option 2: IMAP (Recommended for Production)

Requires a catch-all email setup:

```toml
[email]
use_imap = true
use_guerrilla_mail = false

[email.imap]
ip = "mail.yourdomain.com"
port = 993
email = "catchall@yourdomain.com"
password = "your_email_password"
domains = ["yourdomain.com", "anotherdomain.com"]
```

**How It Works**:
1. Application generates: `random123@yourdomain.com`
2. Email arrives at: `catchall@yourdomain.com`
3. Application connects via IMAP and retrieves the verification code

**Setting Up Catch-All Email**:

**Option A - Self-Host**:
- Use [docker-mailserver](https://github.com/docker-mailserver/docker-mailserver)
- Configure postfix for catch-all forwarding

**Option B - Paid Service**:
- [MXRoute](https://mxroute.com/) - Affordable email hosting
- Most email hosting providers support catch-all

**Option C - Configure Your Own**:
- cPanel: Email Routing > Default Address > Forward to email
- Plesk: Mail > Email Addresses > Catch-all
- Gmail: Not supported for catch-all

## Proxy Configuration

Recommended for creating multiple accounts:

```toml
[proxies]
enabled = true
list = [
    { ip = "proxy1.example.com", port = "8080", username = "user", password = "pass"},
    { ip = "proxy2.example.com", port = "3128"},  # No auth
]
```

**Proxy Types Supported**:
- HTTP proxies
- HTTPS proxies
- Authenticated proxies

**Finding Proxies**:
- Purchase from proxy providers (recommended)
- Free proxy lists (not reliable)
- Your own proxy server

## Advanced Configuration

### Multi-Threading

Create multiple accounts simultaneously:

```toml
[default]
accounts_to_create = 10
threads = 5  # Creates 2 accounts per thread
```

**Considerations**:
- More threads = faster, but more resource usage
- Recommended: 1 thread per 2-4 accounts
- Ensure you have enough proxies for parallel creation

### Headless Mode

Run without visible browser window:

```toml
[browser]
headless = true
```

**Use Cases**:
- Server deployments
- Automated workflows
- Resource-constrained environments

**Note**: Ensure user agent is properly set in headless mode.

### 2FA Setup

Enable TOTP two-factor authentication:

```toml
[account]
set_2fa = true
```

The application will:
1. Enable 2FA on the account
2. Save the setup key
3. Save backup codes
4. Store everything in `accounts.json`

You can use the setup key with any TOTP app (Google Authenticator, Authy, etc.).

## Troubleshooting

### Build Issues

**Problem**: Maven dependencies fail to download

**Solution**:
```bash
mvn clean install -U  # Force update
```

**Problem**: "JAVA_HOME not set"

**Solution**:
```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-17

# Linux/macOS
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

### Runtime Issues

**Problem**: "ChromeDriver not found"

**Solution**: 
- Ensure Chrome is installed
- Check internet connection (ChromeDriver downloads automatically)
- Try manually: `mvn clean package` again

**Problem**: Cloudflare challenge appears

**Solutions**:
1. Verify user agent matches your Chrome version
2. Use a different proxy
3. Reduce creation speed
4. The application attempts to solve turnstile automatically

**Problem**: "Connection timeout"

**Solutions**:
1. Increase timeout in config:
   ```toml
   [browser]
   element_wait_timeout = 60
   ```
2. Check internet connection
3. Verify proxies are working

**Problem**: Email verification code not received

**Solutions**:
1. For IMAP: Verify credentials and server settings
2. For Guerrilla Mail: Check internet connection
3. Increase timeout (default 120 seconds)
4. Try a different email domain

### Common Errors

**Error**: "Failed to create account"

**Check**:
- Browser logs in `logs/account-creator.log`
- Chrome console (if `enable_dev_tools = true`)
- Network connectivity
- Proxy status

**Error**: "Selenium WebDriverException"

**Solutions**:
- Update Chrome to latest version
- Clear Chrome cache
- Restart system
- Try non-headless mode for debugging

## Output

### accounts.json

All created accounts are saved here:

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
      "port": "8080"
    },
    "tfa": {
      "setupKey": "JBSWY3DPEHPK3PXP",
      "backupCodes": ["12345678", "87654321"]
    }
  }
]
```

### Log Files

Located in `logs/account-creator.log`:
- Detailed operation logs
- Error messages
- Debug information

## Production Deployment

### On a Server

1. Install Java and Maven
2. Build the project: `mvn clean package`
3. Copy JAR file to server
4. Create `config.toml`
5. Run:
   ```bash
   nohup java -jar account-creator-1.0.0-jar-with-dependencies.jar > output.log 2>&1 &
   ```

### Docker (Optional)

Create a `Dockerfile`:

```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/account-creator-1.0.0-jar-with-dependencies.jar app.jar
COPY config.toml .
RUN apt-get update && apt-get install -y chromium chromium-driver
CMD ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t jagex-creator .
docker run -v $(pwd)/accounts.json:/app/accounts.json jagex-creator
```

## Best Practices

1. **Start Small**: Test with 1-2 accounts first
2. **Use Proxies**: Essential for bulk creation
3. **Monitor Logs**: Watch for errors and adjust
4. **Rotate IPs**: Use different proxies per account
5. **Respect Limits**: Don't create too many accounts too quickly
6. **Backup Data**: Save `accounts.json` regularly

## Support

For issues or questions:
1. Check logs: `logs/account-creator.log`
2. Review this guide
3. Check GitHub issues
4. Contact developer

## Legal Notice

This tool is for educational and legitimate automation purposes only. Ensure compliance with:
- Jagex Terms of Service
- Local laws and regulations
- Proxy provider terms
- Email service terms

The developers are not responsible for misuse of this tool.
