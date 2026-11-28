package com.primepay.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primepay.support.CapturingOtpSender;
import com.primepay.support.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Two-step sign-in (email or mobile + password, then a one-time code) and the auth API envelope. Runs on Oracle. */
@SpringBootTest(properties = {
        "app.dev.bootstrap.username=admin@primepay.local",
        "app.dev.bootstrap.password=Test-Only-Admin-Pass-42",
        "app.dev.seed.sample-data=false"
})
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
class AuthApiTest {
    private static final String ADMIN = "admin@primepay.local";
    private static final String PASSWORD = "Test-Only-Admin-Pass-42";
    /** The development seed gives each demo account a stable fictional mobile (see DevAdminBootstrap). */
    private static final String ADMIN_MOBILE = "+919" + String.format("%09d", Math.floorMod("LOCAL-ADMIN-001".hashCode(), 1_000_000_000));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CapturingOtpSender otps;

    @Test
    void passwordStepSendsAnEmailCodeAndGivesNoToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(ADMIN, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId", notNullValue()))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                .andExpect(jsonPath("$.data.destination").value("a•••@primepay.local"))
                .andExpect(jsonPath("$.data.codeLength").value(6))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void codeStepReturnsBearerTokenAndAccount() throws Exception {
        String challenge = challenge(ADMIN, PASSWORD);
        mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(challenge, otps.codeFor(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.account.role").value("ADMIN"));
    }

    @Test
    void mobileNumberSignsInWithAnSmsCode() throws Exception {
        String typed = ADMIN_MOBILE.substring(3, 8) + " " + ADMIN_MOBILE.substring(8); // typed like "98765 43210"
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(typed, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("SMS"))
                .andExpect(jsonPath("$.data.destination").value("+91 ••••••" + ADMIN_MOBILE.substring(ADMIN_MOBILE.length() - 4)))
                .andReturn().getResponse().getContentAsString();
        String challenge = json.readTree(body).at("/data/challengeId").asText();
        mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(challenge, otps.codeFor(ADMIN_MOBILE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void wrongCodesCountDownThenTheSignInMustStartAgain() throws Exception {
        String challenge = challenge(ADMIN, PASSWORD);
        String right = otps.codeFor(ADMIN);
        String wrong = right.equals("000000") ? "111111" : "000000";
        for (int left = 4; left >= 1; left--) {
            mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(challenge, wrong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.message").value(containsString(left + " attempt")));
        }
        mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(challenge, wrong)))
                .andExpect(status().isBadRequest());
        // After five wrong codes even the right one is refused.
        mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(challenge, right)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void aCodeWorksOnceAndOnlyForItsOwnChallenge() throws Exception {
        String first = challenge(ADMIN, PASSWORD);
        String firstCode = otps.codeFor(ADMIN);
        String second = challenge(ADMIN, PASSWORD);
        String secondCode = otps.codeFor(ADMIN);
        if (!firstCode.equals(secondCode)) {
            mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(second, firstCode)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(first, firstCode)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(first, firstCode)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody("not-a-challenge", "123456")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendIsRateLimited() throws Exception {
        String challenge = challenge(ADMIN, PASSWORD);
        mvc.perform(post("/api/v1/auth/login/resend").contentType(MediaType.APPLICATION_JSON).content("{\"challengeId\":\"" + challenge + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void wrongPasswordReturnsInvalidCredentialsEnvelopeAndSendsNoCode() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials("nobody@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email/mobile or password."))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void blankLoginReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields[*].field", hasItem("identifier")))
                .andExpect(jsonPath("$.error.fields[*].field", hasItem("password")));
    }

    @Test
    void malformedJsonReturnsMalformedRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void meWithoutTokenReturnsUnauthenticatedEnvelope() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meReturnsSignedInAdmin() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(ADMIN, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(ADMIN))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.canTransact").value(false));
    }

    @Test
    void requestIdIsEchoedWhenSafe() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("X-Request-Id", "test-request-0001"))
                .andExpect(header().string("X-Request-Id", "test-request-0001"))
                .andExpect(jsonPath("$.requestId").value("test-request-0001"));
    }

    @Test
    void adminCanCreateSuperDistributorButDistributorNeedsAMasterParent() throws Exception {
        String token = login(ADMIN, PASSWORD);
        long adminId = json.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).at("/data/accountId").asLong();

        mvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newAccount("PP-SD-T001", "SUPER_DISTRIBUTOR", adminId, "super.t001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPER_DISTRIBUTOR"))
                .andExpect(jsonPath("$.parentId").value(adminId));

        mvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newAccount("PP-DT-T001", "DISTRIBUTOR", adminId, "dist.t001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private String login(String identifier, String password) throws Exception {
        return TestLogin.login(mvc, json, otps, identifier, password);
    }

    private String challenge(String identifier, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(identifier, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/data/challengeId").asText();
    }

    private String credentials(String identifier, String password) throws Exception {
        return json.writeValueAsString(new LoginRequest(identifier, password));
    }

    private static String verifyBody(String challengeId, String code) {
        return "{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + code + "\"}";
    }

    private static String newAccount(String code, String role, long parentId, String username) {
        return """
                {"accountCode":"%s","displayName":"Test %s","role":"%s","parentId":%d,
                 "username":"%s","initialPassword":"Temp-Password-12345"}
                """.formatted(code, code, role, parentId, username);
    }
}
