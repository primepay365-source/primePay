package com.primepay.auth.otp;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SMS codes through MSG91's Flow API (India, DLT-registered template). Set MSG91_AUTH_KEY and MSG91_TEMPLATE_ID;
 * the approved template must contain the variable ##otp##, for example:
 * "Your PrimePay code is ##otp##. It expires in 5 minutes. Do not share it."
 */
@Component
@Order(20)
public class Msg91SmsOtpSender implements OtpSender {
    private final String authKey;
    private final String templateId;
    private final RestClient client;

    public Msg91SmsOtpSender(@Value("${app.otp.sms.msg91.auth-key:}") String authKey,
                             @Value("${app.otp.sms.msg91.template-id:}") String templateId,
                             @Value("${app.otp.sms.msg91.base-url:https://control.msg91.com}") String baseUrl) {
        this.authKey = authKey;
        this.templateId = templateId;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
    }

    @Override
    public boolean supports(OtpChannel channel) {
        return channel == OtpChannel.SMS;
    }

    @Override
    public boolean isConfigured() {
        return !authKey.isBlank() && !templateId.isBlank();
    }

    @Override
    public void send(OtpChannel channel, String destination, String code, String purpose, Duration validFor) {
        // MSG91 expects the number with country code and no "+": 91XXXXXXXXXX
        String mobile = destination.replaceAll("[^0-9]", "");
        Map<String, Object> body = Map.of(
                "template_id", templateId,
                "short_url", "0",
                "recipients", List.of(Map.of("mobiles", mobile, "otp", code)));
        JsonNode response;
        try {
            response = client.post().uri("/api/v5/flow")
                    .header("authkey", authKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new OtpDeliveryException("MSG91 request failed: " + e.getClass().getSimpleName(), e);
        }
        if (response == null || !"success".equalsIgnoreCase(response.path("type").asText())) {
            throw new OtpDeliveryException("MSG91 rejected the message: " + (response == null ? "empty response" : response.path("message").asText()));
        }
    }
}
