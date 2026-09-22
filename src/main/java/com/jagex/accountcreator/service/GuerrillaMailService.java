package com.jagex.accountcreator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with Guerrilla Mail API
 */
public class GuerrillaMailService {
    private static final Logger log = LoggerFactory.getLogger(GuerrillaMailService.class);
    private static final String API_URL = "https://api.guerrillamail.com/ajax.php";
    private static final int TIMEOUT_SECONDS = 120;

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public GuerrillaMailService(com.jagex.accountcreator.model.Proxy proxy) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        if (proxy != null) {
            Proxy javaProxy = new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(proxy.getIp(), Integer.parseInt(proxy.getPort()))
            );
            builder.proxy(javaProxy);
            
            if (proxy.hasAuth()) {
                builder.proxyAuthenticator((route, response) -> {
                    String credential = okhttp3.Credentials.basic(
                            proxy.getUsername(),
                            proxy.getPassword()
                    );
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }

        this.client = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get verification code from Guerrilla Mail
     */
    public String getVerificationCode(String accountEmail, int timeoutSeconds) throws IOException, InterruptedException {
        log.info("Getting verification code for: {}", accountEmail);

        // Get email address
        String url = API_URL + "?f=get_email_address&lang=en";
        Request request = new Request.Builder().url(url).build();
        
        JsonNode emailResponse;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get email address: " + response);
            }
            emailResponse = objectMapper.readTree(response.body().string());
        }

        String sidToken = emailResponse.get("sid_token").asText();
        log.debug("Got SID token: {}", sidToken);

        // Set email username
        String accountUsername = accountEmail.split("@")[0];
        url = API_URL + "?f=set_email_user&email_user=" + accountUsername + "&lang=en&sid_token=" + sidToken;
        request = new Request.Builder().url(url).build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to set email: " + response);
            }
            JsonNode setEmailResponse = objectMapper.readTree(response.body().string());
            String emailAddr = setEmailResponse.get("email_addr").asText();
            
            if (!emailAddr.contains(accountUsername)) {
                throw new IOException("Failed to set account email on Guerrilla Mail");
            }
            log.debug("Set email to: {}", emailAddr);
        }

        // Poll for verification email
        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < endTime) {
            url = API_URL + "?f=check_email&sid_token=" + sidToken + "&seq=0";
            request = new Request.Builder().url(url).build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Failed to check email: {}", response);
                    Thread.sleep(1000);
                    continue;
                }

                JsonNode checkResponse = objectMapper.readTree(response.body().string());
                JsonNode emailList = checkResponse.get("list");
                
                if (emailList != null && emailList.isArray()) {
                    for (JsonNode email : emailList) {
                        String mailFrom = email.get("mail_from").asText();
                        if ("no-reply@contact.jagex.com".equals(mailFrom)) {
                            String mailSubject = email.get("mail_subject").asText();
                            String code = mailSubject.split(" ")[0];
                            log.info("Found verification code: {}", code);
                            return code;
                        }
                    }
                }
            }
            
            Thread.sleep(1000);
        }

        throw new IOException("Timed out waiting for verification code");
    }
}
