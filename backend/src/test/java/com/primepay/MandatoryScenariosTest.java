package com.primepay;

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
 * (security, validation, services, ledger and database). Runs on the H2 dev database in Oracle mode.
 */
@SpringBootTest(properties = {
        "app.dev.bootstrap.username=admin@primepay.local",
        "app.dev.bootstrap.password=Scenario-Admin-Pass-1",
        "app.dev.seed.password=Scenario-Seed-Pass-1",
        "app.dev.seed.sample-data=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MandatoryScenariosTest {
    private static final String PW = "Scenario-User-Pass-123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String admin, admin2, sd, md, dt, dt2, rt, rt2;
    long adminId, sdId, mdId, dtId, dt2Id, rtId, rt2Id;
    String transferRef;
    long transferTxnId;

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
        JsonNode denied = call(transfer(rt2Id, "100.00", key()), dt, 403);
        assertThat(denied.at("/error/code").asText()).isEqualTo("OUTSIDE_HIERARCHY");
        call(patch("/api/v1/users/" + rt2Id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"reason\":\"test\"}"), dt, 404);
    }

    @Test @Order(5)
    void s08_to_s10_fundsFlowDownTheHierarchy() throws Exception {
        JsonNode load = call(post("/api/v1/wallets/load").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1000000,\"mode\":\"NEFT\",\"utr\":\"UTR123456\"}"), sd, 201);
        assertThat(load.at("/data/status").asText()).isEqualTo("PENDING");
        call(post("/api/v1/transactions/" + load.at("/data/id").asLong() + "/approve-load"), admin, 200);
        assertThat(balance(sd)).isEqualByComparingTo("1000000.00");

        call(transfer(mdId, "300000.00", key()), sd, 201);       // 8
        call(transfer(dtId, "100000.00", key()), md, 201);       // 9
        JsonNode t = call(transfer(rtId, "20000.00", key()), dt, 201); // 10
        transferRef = t.at("/data/referenceNo").asText();
        transferTxnId = t.at("/data/id").asLong();
        assertThat(balance(sd)).isEqualByComparingTo("700000.00");
        assertThat(balance(md)).isEqualByComparingTo("200000.00");
        assertThat(balance(dt)).isEqualByComparingTo("80000.00");
        assertThat(balance(rt)).isEqualByComparingTo("20000.00");

        // Only to direct children: Super can't skip a level.
        call(transfer(dtId, "10.00", key()), sd, 403);
    }

    @Test @Order(6)
    void s11_s12_retailerPayoutDistributesCommission() throws Exception {
        long hdfc = addAccount(rt, "123456789012", "Test Customer");
        JsonNode payout = call(withdraw(hdfc, "10000", "NEFT"), rt, 201);
        assertThat(payout.at("/data/status").asText()).isEqualTo("SUCCESS");
        assertThat(payout.at("/data/beneficiaryAccountLast4").asText()).isEqualTo("9012");
        // 20000 - 10000 - 10 fee + 0.10% commission (10.00)
        assertThat(balance(rt)).isEqualByComparingTo("10000.00");
        JsonNode detail = call(get("/api/v1/transactions/" + payout.at("/data/id").asLong()), rt, 200);
        assertThat(detail.at("/data/commission").size()).isEqualTo(5);
        assertThat(call(get("/api/v1/commissions/statement"), dt, 200).at("/data/creditedThisMonth").decimalValue())
                .isEqualByComparingTo("20.00"); // 0.20%
        assertThat(call(get("/api/v1/commissions/statement"), md, 200).at("/data/creditedThisMonth").decimalValue())
                .isEqualByComparingTo("15.00"); // 0.15%

        // A second successful payout also earns commission (regression: system transactions must not share a NULL key).
        call(withdraw(addAccount(rt, "123456789099", "Second Customer"), "1000", "IMPS"), rt, 201);
        assertThat(balance(rt)).isEqualByComparingTo("8991.00"); // 10000 - 1000 - 10 fee + 1.00 commission (0.10%)
        // A declined payout refunds amount and fee: net balance unchanged.
        JsonNode failed = call(withdraw(addAccount(rt, "123456780000", "Closed Account"), "500", "IMPS"), rt, 201);
        assertThat(failed.at("/data/status").asText()).isEqualTo("FAILED");
        assertThat(balance(rt)).isEqualByComparingTo("8991.00");
    }

    @Test @Order(7)
    void s13_insufficientBalanceChangesNothing() throws Exception {
        BigDecimal before = balance(dt), receiver = balance(rt);
        JsonNode r = call(transfer(rtId, "150000.00", key()), dt, 422); // within the 2,00,000 per-transfer limit, above the balance
        assertThat(r.at("/error/code").asText()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(balance(dt)).isEqualByComparingTo(before);
        assertThat(balance(rt)).isEqualByComparingTo(receiver);
    }

    @Test @Order(8)
    void s14_duplicateTransferDoesNotDebitTwice() throws Exception {
        BigDecimal before = balance(dt);
        String k = key();
        String ref1 = call(transfer(rtId, "1000.00", k), dt, 201).at("/data/referenceNo").asText();
        String ref2 = call(transfer(rtId, "1000.00", k), dt, 201).at("/data/referenceNo").asText();
        assertThat(ref2).isEqualTo(ref1);
        assertThat(balance(dt)).isEqualByComparingTo(before.subtract(new BigDecimal("1000.00")));
        // Same key, different request: refused.
        assertThat(call(transfer(rtId, "2000.00", k), dt, 409).at("/error/code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test @Order(9)
    void s15_reversalPostsCompensatingEntries() throws Exception {
        // The retailer already spent most of the first transfer: reversing it is refused and nothing changes.
        BigDecimal rtNow = balance(rt);
        JsonNode refused = call(post("/api/v1/transactions/" + transferTxnId + "/reverse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"test\"}"), admin, 422);
        assertThat(refused.at("/error/code").asText()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(balance(rt)).isEqualByComparingTo(rtNow);

        JsonNode fresh = call(transfer(rtId, "3000.00", key()), dt, 201);
        long freshId = fresh.at("/data/id").asLong();
        transferRef = fresh.at("/data/referenceNo").asText();
        BigDecimal dtBefore = balance(dt), rtBefore = balance(rt);
        call(post("/api/v1/transactions/" + freshId + "/reverse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Sent to wrong retailer\"}"), dt, 403); // only admin
        JsonNode reversal = call(post("/api/v1/transactions/" + freshId + "/reverse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Sent to wrong retailer\"}"), admin, 200);
        assertThat(reversal.at("/data/type").asText()).isEqualTo("REVERSAL");
        assertThat(balance(dt)).isEqualByComparingTo(dtBefore.add(new BigDecimal("3000.00")));
        assertThat(balance(rt)).isEqualByComparingTo(rtBefore.subtract(new BigDecimal("3000.00")));
        JsonNode original = call(get("/api/v1/transactions/" + freshId), admin, 200);
        assertThat(original.at("/data/transaction/status").asText()).isEqualTo("REVERSED");
        assertThat(original.at("/data/ledger").size()).isEqualTo(2); // original lines untouched
        JsonNode reversalDetail = call(get("/api/v1/transactions/" + reversal.at("/data/id").asLong()), admin, 200);
        assertThat(reversalDetail.at("/data/ledger").size()).isEqualTo(2); // compensating DR + CR
        call(post("/api/v1/transactions/" + freshId + "/reverse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"again\"}"), admin, 409); // already reversed
    }

    @Test @Order(10)
    void s16_dailyLimitViolationIsRejected() throws Exception {
        call(post("/api/v1/limits/overrides").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId": %d, "serviceCode": "WALLET_TRANSFER", "limit": {"minTxn": 1, "maxTxn": 5000, "dailyAmount": 5000,
                 "monthlyAmount": 100000, "dailyCount": 100, "monthlyCount": 1000}}""".formatted(dtId)), admin, 200);
        JsonNode r = call(transfer(rtId, "4999.00", key()), dt, 422);
        assertThat(r.at("/error/code").asText()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(r.at("/error/message").asText()).contains("Daily limit");
    }

    @Test @Order(11)
    void s17_inactiveAccountCannotDoFinancialOperations() throws Exception {
        long rt2Account = addAccount(rt2, "123456789012", "Test Holder");
        call(patch("/api/v1/users/" + rt2Id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"reason\":\"KYC expired\"}"), dt2, 200);
        // The token issued before suspension stops working for money.
        JsonNode r = call(withdraw(rt2Account, "100", "IMPS"), rt2, 422);
        assertThat(r.at("/error/code").asText()).isEqualTo("ACCOUNT_INACTIVE");
        // Nor can anyone send money to it.
        assertThat(call(transfer(rt2Id, "100.00", key()), dt2, 422).at("/error/code").asText()).isEqualTo("ACCOUNT_INACTIVE");
        // And it can't sign in.
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"t.retail2\",\"password\":\"" + PW + "\"}")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(422);
    }

    @Test @Order(12)
    void s18_everyFinancialOperationAppearsInReportsAndAuditAndBooksBalance() throws Exception {
        JsonNode report = call(get("/api/v1/reports/transactions"), admin, 200);
        assertThat(report.at("/data/rows").toString()).contains(transferRef);
        JsonNode auditLog = call(get("/api/v1/audit?size=100"), admin, 200);
        String audit = auditLog.at("/data/items").toString();
        assertThat(audit).contains("WALLET_TRANSFER").contains("PAYOUT").contains("TRANSACTION_REVERSE")
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
                .content("{\"username\":\"t.master\"}")).andReturn().getResponse().getContentAsString());
        String otp = forgot.at("/data/devOtp").asText();
        assertThat(otp).hasSize(6);
        int reset = mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"t.master\",\"otp\":\"" + otp + "\",\"newPassword\":\"New-Secure-Pass-9\"}")).andReturn().getResponse().getStatus();
        assertThat(reset).isEqualTo(200);
        assertThat(loginStatus("t.master", "New-Secure-Pass-9")).isEqualTo(200);
    }

    // ---------------------------------------------------------------- helpers

    private long addAccount(String token, String accountNumber, String holder) throws Exception {
        JsonNode r = call(post("/api/v1/beneficiaries").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + accountNumber + "\",\"ifsc\":\"HDFC0001234\",\"holderName\":\"" + holder + "\"}"), token, 201);
        assertThat(r.at("/data/verified").asBoolean()).isTrue();
        return r.at("/data/beneficiary/id").asLong();
    }

    private MockHttpServletRequestBuilder withdraw(long beneficiaryId, String amount, String mode) {
        return post("/api/v1/withdraw").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"beneficiaryId\":" + beneficiaryId + ",\"amount\":" + amount + ",\"mode\":\"" + mode + "\"}");
    }

    private MockHttpServletRequestBuilder transfer(long to, String amount, String key) {
        return post("/api/v1/wallets/transfer").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toAccountId\":" + to + ",\"amount\":" + amount + ",\"remarks\":\"test\"}");
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
                 "email":"%s@example.com","mobile":"+919800000000","businessName":"Biz %s"}""".formatted(code, code, role, parentId, username, PW, username, code);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? json.createObjectNode() : json.readTree(body);
    }

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private int loginStatus(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse().getStatus();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
