package com.primepay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primepay.support.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The per-account cap on sign-ins that reach the code step (protects inboxes and SMS spend). Runs on Oracle. */
@SpringBootTest(properties = {
        "app.dev.bootstrap.username=admin@primepay.local",
        "app.dev.bootstrap.password=Rate-Limit-Admin-Pass-7",
        "app.dev.seed.sample-data=false",
        "app.otp.max-sign-ins-per-15-min=2"
})
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
class SignInRateLimitTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void tooManySignInsInFifteenMinutesAreRefusedBeforeAnyCodeIsSent() throws Exception {
        assertThat(TestLogin.passwordStepStatus(mvc, json, "admin@primepay.local", "Rate-Limit-Admin-Pass-7")).isEqualTo(200);
        assertThat(TestLogin.passwordStepStatus(mvc, json, "admin@primepay.local", "Rate-Limit-Admin-Pass-7")).isEqualTo(200);
        assertThat(TestLogin.passwordStepStatus(mvc, json, "admin@primepay.local", "Rate-Limit-Admin-Pass-7")).isEqualTo(429);
    }
}
