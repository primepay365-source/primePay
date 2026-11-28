package com.primepay.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.dev.bootstrap.username=admin@primepay.local",
        "app.dev.bootstrap.password=Test-Only-Admin-Pass-42"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AuthApiTest {
    private static final String ADMIN = "admin@primepay.local";
    private static final String PASSWORD = "Test-Only-Admin-Pass-42";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void loginReturnsBearerTokenAndAccount() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(ADMIN, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.account.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordReturnsInvalidCredentialsEnvelope() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(ADMIN, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void blankLoginReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields[*].field", hasItem("username")))
                .andExpect(jsonPath("$.error.fields[*].field", hasItem("password")));
    }

    @Test
    void malformedJsonReturnsMalformedRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
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

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("accessToken").asText();
    }

    private String credentials(String username, String password) throws Exception {
        return json.writeValueAsString(new LoginRequest(username, password));
    }

    private static String newAccount(String code, String role, long parentId, String username) {
        return """
                {"accountCode":"%s","displayName":"Test %s","role":"%s","parentId":%d,
                 "username":"%s","initialPassword":"Temp-Password-12345"}
                """.formatted(code, code, role, parentId, username);
    }
}
