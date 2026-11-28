package com.primepay;

import com.primepay.support.CapturingOtpSender;
import com.primepay.support.TestLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The 18 mandatory business scenarios from the requirements, end to end through the HTTP API
 * (security, validation, services, ledger and database). Runs on Oracle Database (the PRIMEPAY_TEST schema).
 */
@SpringBootTest(properties = {
        "app.dev.bootstrap.username=admin@primepay.local",
        "app.dev.bootstrap.password=Scenario-Admin-Pass-1",
        "app.dev.seed.password=Scenario-Seed-Pass-1",
        "app.dev.seed.sample-data=false"
})
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MandatoryScenariosTest {
    private static final String PW = "Scenario-User-Pass-123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingOtpSender otps;

    String admin, admin2, sd, md, dt, dt2, rt, rt2;
    long adminId, sdId, mdId, dtId, dt2Id, rtId, rt2Id;
    String fundRef;
    long loadTxnId, hdfcId;

    @Test @Order(1)
    void s01_to_s04_eachLevelCreatesTheLevelBelow() throws Exception {
        admin = login("admin@primepay.local", "Scenario-Admin-Pass-1");
        admin2 = login("admin2@primepay.local", "Scenario-Seed-Pass-1");
        adminId = call(get("/api/v1/auth/me"), admin, 200).at("/data/accountId").asLong();

        sdId = createUser(admin, "T-SD-1", "SUPER_DISTRIBUTOR", adminId, "t.super");
        sd = login("t.super", PW);
        mdId = createUser(sd, "T-MD-1", "MASTER_DISTRIBUTOR", sdId, "t.master");
        md = login("t.master", PW);
        dtId = createUser(md, "T-DT-1", "DISTRIBUTOR", mdId, "t.dist");
        dt = login("t.dist", PW);
        dt2Id = createUser(md, "T-DT-2", "DISTRIBUTOR", mdId, "t.dist2");
        dt2 = login("t.dist2", PW);
        rtId = createUser(dt, "T-RT-1", "RETAILER", dtId, "t.retail");
        rt = login("t.retail", PW);
        rt2Id = createUser(dt2, "T-RT-2", "RETAILER", dt2Id, "t.retail2");
        rt2 = login("t.retail2", PW);

        // A retailer can't create anyone.
        call(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(userBody("T-X", "RETAILER", rtId, "t.x")), rt, 403);
    }

    @Test @Order(2)
    void s05_retailerSeesOnlyItsOwnDashboardAndNobodyElse() throws Exception {
        JsonNode dash = call(get("/api/v1/reports/dashboard"), rt, 200);
        assertThat(dash.at("/data/role").asText()).isEqualTo("RETAILER");
        assertThat(dash.at("/data/scope").asText()).isEqualTo("Your account");
        assertThat(call(get("/api/v1/users"), rt, 200).at("/data").size()).isZero();
        call(get("/api/v1/users/" + dtId), rt, 404);
    }

    @Test @Order(3)
    void s06_superSeesPermittedDownstreamUsers() throws Exception {
        JsonNode users = call(get("/api/v1/users"), sd, 200).at("/data");
        assertThat(users.size()).isEqualTo(5); // master, 2 distributors, 2 retailers
        JsonNode masterView = call(get("/api/v1/users"), md, 200).at("/data");
        assertThat(masterView.size()).isEqualTo(4);
    }

    @Test @Order(4)
    void s07_distributorCannotAccessAnotherDistributorsRetailer() throws Exception {
        call(get("/api/v1/users/" + rt2Id), dt, 404);
        call(patch("/api/v1/users/" + rt2Id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"reason\":\"test\"}"), dt, 404);
        // Money never moves down the chain: the transfer API no longer exists.
        call(post("/api/v1/wallets/transfer").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toAccountId\":" + rtId + ",\"amount\":100}"), dt, 405);
    }

    @Test @Order(5)
    void s08_to_s10_eachLevelFundsItsOwnWalletAndUplinesEarnMargins() throws Exception {
        // The Super Distributor's bank load (approved by the Admin) reaches the Super Distributor only.
        JsonNode load = call(post("/api/v1/wallets/load").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1000000,\"mode\":\"NEFT\",\"utr\":\"UTR123456\"}"), sd, 201);
        assertThat(load.at("/data/status").asText()).isEqualTo("PENDING");
        call(post("/api/v1/transactions/" + load.at("/data/id").asLong() + "/approve-load"), admin, 200);
        loadTxnId = load.at("/data/id").asLong();
        assertThat(balance(sd)).isEqualByComparingTo("1000000.00");
        assertThat(balance(md)).isZero();
        assertThat(balance(dt)).isZero();
        assertThat(balance(rt)).isZero();

        // Add funds: gateway 2%, then 0.5% kept by each level ABOVE the account that loads.
        fundRef = topUp(rt, "20000");                                  // 20000 - 400 - 4 x 100
        assertThat(balance(rt)).isEqualByComparingTo("19200.00");
        assertThat(balance(dt)).isEqualByComparingTo("100.00");
        assertThat(balance(md)).isEqualByComparingTo("100.00");
        assertThat(balance(sd)).isEqualByComparingTo("1000100.00");
        topUp(dt, "10000");                                             // 10000 - 200 - 3 x 50 = 9650
        assertThat(balance(dt)).isEqualByComparingTo("9750.00");       // its earlier 100 margin + 9650
        assertThat(balance(rt)).isEqualByComparingTo("19200.00");      // nothing moves down
        assertThat(balance(md)).isEqualByComparingTo("150.00");
        assertThat(balance(sd)).isEqualByComparingTo("1000150.00");
    }

    @Test @Order(6)
    void s11_s12_retailerPayoutDistributesCommission() throws Exception {
        hdfcId = addAccount(rt, "123456789012", "Test Customer");
        JsonNode payout = call(withdraw(hdfcId, "10000", "NEFT", key()), rt, 201);
        assertThat(payout.at("/data/status").asText()).isEqualTo("SUCCESS");
        assertThat(payout.at("/data/beneficiaryAccountLast4").asText()).isEqualTo("9012");
        // 19200 - 10000 - 10 fee + 0.10% commission (10.00)
        assertThat(balance(rt)).isEqualByComparingTo("9200.00");
        JsonNode commission = call(get("/api/v1/transactions/" + payout.at("/data/id").asLong()), admin, 200).at("/data/commission");
        assertThat(commission.size()).isEqualTo(5);
        assertThat(shareOf(commission, "DISTRIBUTOR")).isEqualByComparingTo("20.00");          // 0.20%
        assertThat(shareOf(commission, "MASTER_DISTRIBUTOR")).isEqualByComparingTo("15.00");   // 0.15%

        // A second successful payout also earns commission (regression: system transactions must not share a NULL key).
        call(withdraw(addAccount(rt, "123456789099", "Second Customer"), "1000", "IMPS", key()), rt, 201);
        assertThat(balance(rt)).isEqualByComparingTo("8191.00"); // 9200 - 1000 - 10 fee + 1.00 commission (0.10%)
        // A declined payout refunds amount and fee: net balance unchanged.
        JsonNode failed = call(withdraw(addAccount(rt, "123456780000", "Closed Account"), "500", "IMPS", key()), rt, 201);
        assertThat(failed.at("/data/status").asText()).isEqualTo("FAILED");
        assertThat(balance(rt)).isEqualByComparingTo("8191.00");
    }

    @Test @Order(7)
    void s13_insufficientBalanceChangesNothing() throws Exception {
        BigDecimal before = balance(rt);
        JsonNode r = call(withdraw(hdfcId, "40000", "NEFT", key()), rt, 422); // within the per-payout limit, above the balance
        assertThat(r.at("/error/code").asText()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(balance(rt)).isEqualByComparingTo(before);
    }

    @Test @Order(8)
    void s14_duplicateRequestDoesNotDebitTwice() throws Exception {
        BigDecimal before = balance(rt);
        String k = key();
        String ref1 = call(withdraw(hdfcId, "100", "IMPS", k), rt, 201).at("/data/referenceNo").asText();
        String ref2 = call(withdraw(hdfcId, "100", "IMPS", k), rt, 201).at("/data/referenceNo").asText();
        assertThat(ref2).isEqualTo(ref1);
        // 100 + 10 fee - 0.10 commission, once
        assertThat(balance(rt)).isEqualByComparingTo(before.subtract(new BigDecimal("109.90")));
        // Same key, different request: refused.
        assertThat(call(withdraw(hdfcId, "200", "IMPS", k), rt, 409).at("/error/code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test @Order(9)
    void s15_reversalPostsCompensatingEntries() throws Exception {
        BigDecimal sdBefore = balance(sd);
        call(post("/api/v1/transactions/" + loadTxnId + "/reverse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Bank payment returned\"}"), sd, 403); // only admin
        JsonNode reversal = call(post("/api/v1/transactions/" + loadTxnId + "/reverse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Bank payment returned\"}"), admin, 200);
        assertThat(reversal.at("/data/type").asText()).isEqualTo("REVERSAL");
        assertThat(balance(sd)).isEqualByComparingTo(sdBefore.subtract(new BigDecimal("1000000.00")));
        JsonNode original = call(get("/api/v1/transactions/" + loadTxnId), admin, 200);
        assertThat(original.at("/data/transaction/status").asText()).isEqualTo("REVERSED");
        int originalLines = original.at("/data/ledger").size();
        assertThat(originalLines).isPositive(); // original lines untouched
        JsonNode reversalDetail = call(get("/api/v1/transactions/" + reversal.at("/data/id").asLong()), admin, 200);
        assertThat(reversalDetail.at("/data/ledger").size()).isEqualTo(originalLines); // one compensating line each
        call(post("/api/v1/transactions/" + loadTxnId + "/reverse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"again\"}"), admin, 409); // already reversed
    }

    @Test @Order(10)
    void s16_dailyLimitViolationIsRejected() throws Exception {
        call(post("/api/v1/limits/overrides").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId": %d, "serviceCode": "PAYOUT", "limit": {"minTxn": 1, "maxTxn": 12000, "dailyAmount": 12000,
                 "monthlyAmount": 100000, "dailyCount": 100, "monthlyCount": 1000}}""".formatted(rtId)), admin, 200);
        JsonNode r = call(withdraw(hdfcId, "1000", "IMPS", key()), rt, 422); // 11,100 already paid out today
        assertThat(r.at("/error/code").asText()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(r.at("/error/message").asText()).contains("Daily limit");
    }

    @Test @Order(11)
    void s17_inactiveAccountCannotDoFinancialOperations() throws Exception {
        long rt2Account = addAccount(rt2, "123456789012", "Test Holder");
        call(patch("/api/v1/users/" + rt2Id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"reason\":\"KYC expired\"}"), dt2, 200);
        // The token issued before suspension stops working for money.
        JsonNode r = call(withdraw(rt2Account, "100", "IMPS", key()), rt2, 422);
        assertThat(r.at("/error/code").asText()).isEqualTo("ACCOUNT_INACTIVE");
        // Nor can it add funds.
        assertThat(call(post("/api/v1/add-funds/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryCode\":\"EDU\",\"cardNetwork\":\"VISA\",\"cardHolder\":\"Test Holder\",\"amount\":1000}"), rt2, 422)
                .at("/error/code").asText()).isEqualTo("ACCOUNT_INACTIVE");
        // And it can't sign in.
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"t.retail2@example.com\",\"password\":\"" + PW + "\"}")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(422);
    }

    @Test @Order(12)
    void s18_everyFinancialOperationAppearsInReportsAndAuditAndBooksBalance() throws Exception {
        JsonNode report = call(get("/api/v1/reports/transactions"), admin, 200);
        assertThat(report.at("/data/rows").toString()).contains(fundRef);
        JsonNode auditLog = call(get("/api/v1/audit?size=100"), admin, 200);
        String audit = auditLog.at("/data/items").toString();
        assertThat(audit).contains("ADD_FUNDS").contains("PAYOUT").contains("TRANSACTION_REVERSE")
                .contains("WALLET_LOAD_APPROVE").contains("USER_STATUS_CHANGE").contains("LIMIT_OVERRIDE");
        call(get("/api/v1/audit"), sd, 403);
        JsonNode recon = call(get("/api/v1/reports/reconciliation"), admin, 200);
        assertThat(recon.at("/data/walletTotal").decimalValue()).isEqualByComparingTo(recon.at("/data/ledgerTotal").decimalValue());
        for (JsonNode check : recon.at("/data/checks")) {
            assertThat(check.get("passed").asBoolean()).as(check.get("title").asText()).isTrue();
        }
    }

    @Test @Order(13)
    void makerCheckerAdjustmentNeedsASecondAdmin() throws Exception {
        long id = call(post("/api/v1/admin/adjustments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":" + rtId + ",\"direction\":\"CR\",\"amount\":150,\"reason\":\"Bank charge refund\"}"), admin, 201)
                .at("/data/id").asLong();
        BigDecimal before = balance(rt);
        call(post("/api/v1/admin/adjustments/" + id + "/approve"), admin, 403);
        assertThat(balance(rt)).isEqualByComparingTo(before);
        call(post("/api/v1/admin/adjustments/" + id + "/approve"), admin2, 200);
        assertThat(balance(rt)).isEqualByComparingTo(before.add(new BigDecimal("150.00")));
    }

    @Test @Order(14)
    void lockoutAfterFiveWrongPasswordsAndResetWithOtp() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(loginStatus("t.master", "wrong")).isEqualTo(401);
        }
        assertThat(loginStatus("t.master", "wrong")).isEqualTo(423);
        assertThat(loginStatus("t.master", PW)).isEqualTo(423);
        JsonNode forgot = json.readTree(mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"t.master@example.com\"}")).andReturn().getResponse().getContentAsString());
        assertThat(forgot.at("/data/devOtp").isNull()).isTrue(); // the code never comes back in the response
        String otp = otps.codeFor("t.master@example.com");     // it arrives by email
        assertThat(otp).hasSize(6);
        int reset = mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"t.master@example.com\",\"otp\":\"" + otp + "\",\"newPassword\":\"New-Secure-Pass-9\"}")).andReturn().getResponse().getStatus();
        assertThat(reset).isEqualTo(200);
        assertThat(loginStatus("t.master", "New-Secure-Pass-9")).isEqualTo(200);
        assertThat(login("t.master", "New-Secure-Pass-9")).isNotBlank(); // and the code step completes
    }

    // ---------------------------------------------------------------- helpers

    private long addAccount(String token, String accountNumber, String holder) throws Exception {
        JsonNode r = call(post("/api/v1/beneficiaries").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + accountNumber + "\",\"ifsc\":\"HDFC0001234\",\"holderName\":\"" + holder + "\"}"), token, 201);
        assertThat(r.at("/data/verified").asBoolean()).isTrue();
        return r.at("/data/beneficiary/id").asLong();
    }

    private MockHttpServletRequestBuilder withdraw(long beneficiaryId, String amount, String mode, String idempotencyKey) {
        return post("/api/v1/withdraw").header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"beneficiaryId\":" + beneficiaryId + ",\"amount\":" + amount + ",\"mode\":\"" + mode + "\"}");
    }

    /** Add funds by UPI (no OTP) and return the transaction reference. */
    private String topUp(String token, String amount) throws Exception {
        String ref = call(post("/api/v1/add-funds/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryCode\":\"EDU\",\"cardNetwork\":\"VISA\",\"cardHolder\":\"Test Holder\",\"amount\":" + amount + "}"), token, 201)
                .at("/data/sessionRef").asText();
        call(post("/api/v1/add-funds/sessions/" + ref + "/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"UPI\",\"upiId\":\"test.user@okbank\"}"), token, 200);
        JsonNode done = call(post("/api/v1/add-funds/sessions/" + ref + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"upiId\":\"test.user@okbank\"}"), token, 200).at("/data");
        assertThat(done.at("/status").asText()).isEqualTo("SUCCESS");
        return done.at("/referenceNo").asText();
    }

    private static BigDecimal shareOf(JsonNode commission, String level) {
        for (JsonNode c : commission) if (level.equals(c.at("/levelRole").asText())) return c.at("/amount").decimalValue();
        throw new AssertionError(level + " share missing");
    }

    private BigDecimal balance(String token) throws Exception {
        return call(get("/api/v1/wallets/me"), token, 200).at("/data/availableBalance").decimalValue();
    }

    private long createUser(String token, String code, String role, long parentId, String username) throws Exception {
        return call(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(userBody(code, role, parentId, username)), token, 201)
                .at("/data/id").asLong();
    }

    private static String userBody(String code, String role, long parentId, String username) {
        return """
                {"accountCode":"%s","displayName":"Test %s","role":"%s","parentId":%d,"username":"%s","initialPassword":"%s",
                 "email":"%s@example.com","businessName":"Biz %s"}""".formatted(code, code, role, parentId, username, PW, username, code);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? json.createObjectNode() : json.readTree(body);
    }

    private String login(String username, String password) throws Exception {
        return TestLogin.login(mvc, json, otps, username, password);
    }

    private int loginStatus(String username, String password) throws Exception {
        return TestLogin.passwordStepStatus(mvc, json, username, password);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
