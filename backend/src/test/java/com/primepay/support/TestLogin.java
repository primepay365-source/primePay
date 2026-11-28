package com.primepay.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Two-step sign-in for tests: email/mobile + password, then the code captured by {@link CapturingOtpSender}. */
public final class TestLogin {
    private TestLogin() {
    }

    /** Test users created with a short username get the email "username@example.com". */
    public static String identifier(String usernameOrEmail) {
        return usernameOrEmail.contains("@") ? usernameOrEmail.toLowerCase(Locale.ROOT) : usernameOrEmail + "@example.com";
    }

    public static String login(MockMvc mvc, ObjectMapper json, CapturingOtpSender otps, String usernameOrEmail, String password) throws Exception {
        String identifier = identifier(usernameOrEmail);
        MvcResult step1 = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Credentials(identifier, password)))).andReturn();
        assertThat(step1.getResponse().getStatus()).as(step1.getResponse().getContentAsString()).isEqualTo(200);
        String challengeId = json.readTree(step1.getResponse().getContentAsString()).at("/data/challengeId").asText();
        String code = otps.codeFor(identifier);
        MvcResult step2 = mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + code + "\"}")).andReturn();
        assertThat(step2.getResponse().getStatus()).as(step2.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode body = json.readTree(step2.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    /** HTTP status of sign-in step 1 only (password check, lockout, inactive account). */
    public static int passwordStepStatus(MockMvc mvc, ObjectMapper json, String usernameOrEmail, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Credentials(identifier(usernameOrEmail), password)))).andReturn().getResponse().getStatus();
    }

    private record Credentials(String identifier, String password) {
    }
}
