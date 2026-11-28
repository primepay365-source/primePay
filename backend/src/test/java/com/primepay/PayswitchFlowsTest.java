package com.primepay;

import com.primepay.support.CapturingMailSender;
import com.primepay.support.CapturingOtpSender;
import com.primepay.support.TestLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The retailer business flow: everything runs from the main wallet.
 * Add funds (gateway category → card/UPI → OTP → net credit), credit-card bill payment (fetch → pay → status
 * → refund or recheck) and withdraw funds (verify bank account → transfer). Uses the simulated providers.
 */
@SpringBootTest(properties = {
        "app.dev.bootstrap.username=admin@primepay.local",
        "app.dev.bootstrap.password=Flow-Admin-Pass-1",
        "app.dev.seed.password=Flow-Seed-Pass-1",
        "app.dev.seed.sample-data=true"
})
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayswitchFlowsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingOtpSender otps;
    @Autowired CapturingMailSender mails;

    String rt, dt, admin;

    @BeforeAll
    void signIn() throws Exception {
        rt = login("retailer@primepay.local", "Flow-Seed-Pass-1");
        dt = login("distributor@primepay.local", "Flow-Seed-Pass-1");
        admin = login("admin@primepay.local", "Flow-Admin-Pass-1");
    }

    // ------------------------------------------------------------------ add funds

    @Test
    void addFundsByCardCreditsAmountMinusCategoryCharge() throws Exception {
        BigDecimal before = balance(rt);
        JsonNode cats = call(get("/api/v1/add-funds/categories"), rt, 200).at("/data");
        assertThat(cats.size()).isEqualTo(3);

        JsonNode s = startSession("ECOM", "10000");
        assertThat(s.at("/charge").decimalValue()).isEqualByComparingTo("400.00"); // gateway 2% + 0.5% x 4 levels above
        assertThat(s.at("/netAmount").decimalValue()).isEqualByComparingTo("9600.00");
        String ref = s.at("/sessionRef").asText();

        JsonNode otpSent = call(post("/api/v1/add-funds/sessions/" + ref + "/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CARD\",\"cardLast4\":\"4242\"}"), rt, 200).at("/data");
        assertThat(otpSent.at("/status").asText()).isEqualTo("OTP_SENT");
        String otp = otpSent.at("/devOtp").asText();

        JsonNode done = call(complete(ref, otp), rt, 200).at("/data");
        assertThat(done.at("/status").asText()).isEqualTo("SUCCESS");
        assertThat(balance(rt)).isEqualByComparingTo(before.add(new BigDecimal("9600.00")));

        // Completing again is safe: no second credit.
        call(complete(ref, otp), rt, 200);
        assertThat(balance(rt)).isEqualByComparingTo(before.add(new BigDecimal("9600.00")));

        JsonNode txn = call(get("/api/v1/transactions/" + done.at("/transactionId").asLong()), rt, 200).at("/data/transaction");
        assertThat(txn.at("/type").asText()).isEqualTo("ADD_FUNDS");
        assertThat(txn.at("/fee").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(txn.at("/cardLast4").asText()).isEqualTo("4242");
    }

    @Test
    void addFundsOutsideCategoryLimitIsRefused() throws Exception {
        JsonNode r = call(post("/api/v1/add-funds/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryCode\":\"TRAVEL\",\"cardNetwork\":\"VISA\",\"cardHolder\":\"Priya Nair\",\"amount\":60000}"), rt, 422);
        assertThat(r.at("/error/code").asText()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    void wrongOtpThreeTimesFailsTheAttemptWithoutCrediting() throws Exception {
        BigDecimal before = balance(rt);
        String ref = startSession("EDU", "5000").at("/sessionRef").asText();
        call(post("/api/v1/add-funds/sessions/" + ref + "/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CARD\",\"cardLast4\":\"1234\"}"), rt, 200);
        assertThat(call(complete(ref, "000000"), rt, 400).at("/error/message").asText()).contains("2 attempts left");
        call(complete(ref, "000001"), rt, 400);
        JsonNode third = call(complete(ref, "000002"), rt, 200).at("/data");
        assertThat(third.at("/status").asText()).isEqualTo("FAILED");
        assertThat(balance(rt)).isEqualByComparingTo(before);
    }

    @Test
    void declinedCardAndUpiAddNothing() throws Exception {
        BigDecimal before = balance(rt);
        String card = startSession("ECOM", "2000").at("/sessionRef").asText();
        String otp = call(post("/api/v1/add-funds/sessions/" + card + "/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CARD\",\"cardLast4\":\"0000\"}"), rt, 200).at("/data/devOtp").asText();
        assertThat(call(complete(card, otp), rt, 200).at("/data/status").asText()).isEqualTo("FAILED");

        String upi = startSession("ECOM", "2000").at("/sessionRef").asText();
        JsonNode awaiting = call(post("/api/v1/add-funds/sessions/" + upi + "/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"UPI\",\"upiId\":\"fail.test@okbank\"}"), rt, 200).at("/data");
        assertThat(awaiting.at("/status").asText()).isEqualTo("AWAITING_UPI");
        assertThat(call(post("/api/v1/add-funds/sessions/" + upi + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"upiId\":\"fail.test@okbank\"}"), rt, 200).at("/data/status").asText()).isEqualTo("FAILED");
        assertThat(balance(rt)).isEqualByComparingTo(before);
    }


    @Test
    void creditCardBillPaymentDebitsAmountPlusChargeAndPaysCommission() throws Exception {
        BigDecimal before = balance(rt);
        JsonNode bill = fetch("4321", "9876543210");
        assertThat(bill.at("/customerName").asText()).isNotBlank();
        assertThat(bill.at("/mobileMasked").asText()).endsWith("3210").doesNotContain("98765");
        JsonNode paid = call(pay(bill.at("/fetchRef").asText(), "1000"), rt, 201).at("/data");
        assertThat(paid.at("/status").asText()).isEqualTo("SUCCESS");
        assertThat(paid.at("/type").asText()).isEqualTo("BILL_PAYMENT");
        // 1000 + 5 charge - 1.00 retailer commission (0.10%)
        assertThat(balance(rt)).isEqualByComparingTo(before.subtract(new BigDecimal("1004.00")));
    }

    @Test
    void failedBillPaymentRefundsEverything() throws Exception {
        BigDecimal before = balance(rt);
        JsonNode paid = call(pay(fetch("0000", "9876543210").at("/fetchRef").asText(), "500"), rt, 201).at("/data");
        assertThat(paid.at("/status").asText()).isEqualTo("FAILED");
        assertThat(balance(rt)).isEqualByComparingTo(before);
    }

    @Test
    void pendingBillPaymentIsResolvedByRecheck() throws Exception {
        BigDecimal before = balance(rt);
        JsonNode ok = call(pay(fetch("1111", "9876543210").at("/fetchRef").asText(), "500"), rt, 201).at("/data");
        assertThat(ok.at("/status").asText()).isEqualTo("PENDING");
        assertThat(balance(rt)).isEqualByComparingTo(before.subtract(new BigDecimal("505.00")));
        assertThat(call(post("/api/v1/bills/" + ok.at("/id").asLong() + "/recheck"), rt, 200).at("/data/status").asText()).isEqualTo("SUCCESS");

        BigDecimal mid = balance(rt);
        JsonNode bad = call(pay(fetch("2222", "9876543210").at("/fetchRef").asText(), "500"), rt, 201).at("/data");
        assertThat(bad.at("/status").asText()).isEqualTo("PENDING");
        assertThat(call(post("/api/v1/bills/" + bad.at("/id").asLong() + "/recheck"), rt, 200).at("/data/status").asText()).isEqualTo("FAILED");
        assertThat(balance(rt)).isEqualByComparingTo(mid); // refunded
    }

    @Test
    void billCannotExceedDueOrBalanceAndUnknownCardHasNoBill() throws Exception {
        String ref = fetch("5555", "9876543210").at("/fetchRef").asText();
        call(pay(ref, "99999999"), rt, 400);
        call(post("/api/v1/bills/fetch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"billerId\":" + billerId() + ",\"cardLast4\":\"9999\",\"mobile\":\"9876543210\"}"), rt, 404);
        // Mobile is mandatory and must be a valid Indian mobile number.
        call(post("/api/v1/bills/fetch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"billerId\":" + billerId() + ",\"cardLast4\":\"4321\",\"mobile\":\"\"}"), rt, 400);
    }

    // ------------------------------------------------------------------ withdraw

    @Test
    void withdrawOnlyToAVerifiedSavedAccount() throws Exception {
        JsonNode rejected = call(addAccount("112233449999", "Priya Nair"), rt, 201).at("/data");
        assertThat(rejected.at("/verified").asBoolean()).isFalse();
        call(withdraw(rejected.at("/beneficiary/id").asLong(), "100"), rt, 409);

        JsonNode ok = call(addAccount("112233445566", "Priya Nair"), rt, 201).at("/data");
        assertThat(ok.at("/verified").asBoolean()).isTrue();
        assertThat(ok.at("/beneficiary/verifiedName").asText()).isEqualTo("PRIYA NAIR");
        assertThat(ok.at("/beneficiary/accountMasked").asText()).isEqualTo("••••5566");
        assertThat(ok.toString()).doesNotContain("112233445566");
        call(addAccount("112233445566", "Priya Nair"), rt, 409); // duplicate

        long id = ok.at("/beneficiary/id").asLong();
        BigDecimal before = balance(rt);
        JsonNode w = call(withdraw(id, "1000"), rt, 201).at("/data");
        assertThat(w.at("/status").asText()).isEqualTo("SUCCESS");
        assertThat(w.at("/beneficiaryAccountLast4").asText()).isEqualTo("5566");
        assertThat(balance(rt)).isLessThan(before);

        // Another retailer can't use this account.
        String rt2 = login("retailer2@primepay.local", "Flow-Seed-Pass-1");
        call(withdraw(id, "100"), rt2, 404);

        call(delete("/api/v1/beneficiaries/" + id), rt, 200);
        call(withdraw(id, "100"), rt, 404);
    }

    @Test
    void withdrawalBeyondBalanceChangesNothing() throws Exception {
        long id = call(addAccount("556677881234", "Priya Nair"), rt, 201).at("/data/beneficiary/id").asLong();
        BigDecimal before = balance(rt);
        JsonNode r = call(withdraw(id, before.add(BigDecimal.ONE).toPlainString()), rt, 422);
        assertThat(r.at("/error/code").asText()).isIn("INSUFFICIENT_BALANCE", "LIMIT_EXCEEDED");
        assertThat(balance(rt)).isEqualByComparingTo(before);
    }

    @Test
    void reportsAndDashboardShowTheNewServices() throws Exception {
        JsonNode dash = call(get("/api/v1/reports/dashboard"), rt, 200).at("/data/kpis");
        assertThat(dash.toString()).contains("Bill payments today", "Funds added today", "Withdrawals today");
        call(get("/api/v1/reports/bill-payment"), rt, 200);
        call(get("/api/v1/reports/add-funds"), admin, 200);
    }


    // ------------------------------------------------------------------ user creation by level

    @Test
    void creationFollowsTheHierarchyRules() throws Exception {
        String sd = login("super@primepay.local", "Flow-Seed-Pass-1");
        String md = login("master@primepay.local", "Flow-Seed-Pass-1");
        long adminId = idOf(admin, "LOCAL-ADMIN-001"), sdId = idOf(admin, "PP-SD-1001"), mdId = idOf(admin, "PP-MD-2014"),
                dtId = idOf(admin, "PP-DT-3307"), rtId = idOf(admin, "PP-RT-48210");

        // Admin can create every level, each under a parent of the level above.
        createUser(admin, "H-SD-1", "SUPER_DISTRIBUTOR", adminId, 201);
        createUser(admin, "H-MD-1", "MASTER_DISTRIBUTOR", sdId, 201);
        createUser(admin, "H-DT-1", "DISTRIBUTOR", mdId, 201);
        createUser(admin, "H-RT-1", "RETAILER", dtId, 201);
        JsonNode wrongParent = createUser(admin, "H-DT-X0", "DISTRIBUTOR", sdId, 400);
        assertThat(wrongParent.at("/error/message").asText()).contains("must belong to a Master Distributor");
        // A Retailer may sit directly under any level above it.
        createUser(admin, "H-RT-A", "RETAILER", adminId, 201);
        createUser(admin, "H-RT-S", "RETAILER", sdId, 201);
        createUser(admin, "H-RT-M", "RETAILER", mdId, 201);

        // Super creates Master, Distributor and Retailer inside its own network.
        createUser(sd, "H-MD-2", "MASTER_DISTRIBUTOR", sdId, 201);
        createUser(sd, "H-DT-2", "DISTRIBUTOR", mdId, 201);
        createUser(sd, "H-RT-2", "RETAILER", dtId, 201);
        createUser(sd, "H-RT-6", "RETAILER", sdId, 201); // directly under the Super Distributor
        createUser(sd, "H-SD-X", "SUPER_DISTRIBUTOR", adminId, 403);

        // Master creates Distributors and Retailers inside its own network only.
        createUser(md, "H-DT-3", "DISTRIBUTOR", mdId, 201);
        createUser(md, "H-RT-4", "RETAILER", dtId, 201);
        createUser(md, "H-RT-5", "RETAILER", mdId, 201); // directly under the Master Distributor
        call(get("/api/v1/users/" + rtId), md, 200);
        long otherSd = createUser(admin, "H-SD-9", "SUPER_DISTRIBUTOR", adminId, 201).at("/data/id").asLong();
        long otherMd = createUser(admin, "H-MD-9", "MASTER_DISTRIBUTOR", otherSd, 201).at("/data/id").asLong();
        long otherDt = createUser(admin, "H-DT-9", "DISTRIBUTOR", otherMd, 201).at("/data/id").asLong();
        assertThat(createUser(md, "H-RT-X2", "RETAILER", otherDt, 403).at("/error/code").asText()).isEqualTo("OUTSIDE_HIERARCHY");
        createUser(md, "H-SD-X2", "SUPER_DISTRIBUTOR", adminId, 403);
        createUser(md, "H-MD-X2", "MASTER_DISTRIBUTOR", sdId, 403);

        // Distributor creates only Retailers; Retailer creates nobody.
        createUser(dt, "H-RT-3", "RETAILER", dtId, 201);
        createUser(dt, "H-DT-X", "DISTRIBUTOR", mdId, 403);
        createUser(rt, "H-RT-X3", "RETAILER", dtId, 403);
    }

    @Test
    void adminCreatesPlatformAdminWithPasswordFromTheForm() throws Exception {
        String sd = login("super@primepay.local", "Flow-Seed-Pass-1");
        String body = """
                {"accountCode":"H-AD-1","displayName":"Test Admin","role":"ADMIN","username":"h.ad.1","initialPassword":"Admin-Form-Pass-123",
                 "email":"h.ad.1@example.com","mobile":"+919812345670"}""";
        JsonNode created = call(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body), admin, 201).at("/data");
        assertThat(created.at("/role").asText()).isEqualTo("ADMIN");
        assertThat(created.at("/parentId").isNull() || created.at("/parentId").isMissingNode()).isTrue();

        // The new user is emailed their role, username and password.
        var mail = mails.awaitLastTo("h.ad.1@example.com");
        assertThat(mail.getSubject()).isEqualTo("Your PrimePay Platform Admin account is ready");
        assertThat(mail.getText()).contains("Role:      Platform Admin", "User ID:   H-AD-1", "Username:  h.ad.1",
                "Password:  Admin-Form-Pass-123", "+919812345670");

        // Signs in (password + code) with the password set at creation; it is stored only as a hash.
        String newAdmin = login("h.ad.1@example.com", "Admin-Form-Pass-123");
        assertThat(call(get("/api/v1/auth/me"), newAdmin, 200).at("/data/role").asText()).isEqualTo("ADMIN");

        // Only a Platform Admin may create one; a parent is still required for every other level.
        call(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body.replace("H-AD-1", "H-AD-2").replace("h.ad.1", "h.ad.2").replace("5670", "5671")), sd, 403);
        call(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountCode":"H-SD-NP","displayName":"No Parent","role":"SUPER_DISTRIBUTOR","username":"h.sd.np","initialPassword":"Admin-Form-Pass-123",
                 "email":"h.sd.np@example.com"}"""), admin, 400);
    }

    @Test
    void everyLevelExceptAdminCanDoBusiness() throws Exception {
        String sd = login("super@primepay.local", "Flow-Seed-Pass-1");
        String md = login("master@primepay.local", "Flow-Seed-Pass-1");
        String[] tokens = {sd, md, dt};
        String[] credited = {"975.00", "970.00", "965.00"}; // 1000 - 2% gateway - 0.5% per level above
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            BigDecimal before = balance(token);
            JsonNode s = call(post("/api/v1/add-funds/sessions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"categoryCode\":\"EDU\",\"cardNetwork\":\"VISA\",\"cardHolder\":\"Test Holder\",\"amount\":1000}"), token, 201).at("/data");
            String ref = s.at("/sessionRef").asText();
            String otp = call(post("/api/v1/add-funds/sessions/" + ref + "/pay").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mode\":\"CARD\",\"cardLast4\":\"4242\"}"), token, 200).at("/data/devOtp").asText();
            assertThat(call(complete(ref, otp), token, 200).at("/data/status").asText()).isEqualTo("SUCCESS");
            assertThat(balance(token)).isEqualByComparingTo(before.add(new BigDecimal(credited[i])));

            long biller = call(get("/api/v1/bills/billers"), token, 200).at("/data/0/id").asLong();
            String fetchRef = call(post("/api/v1/bills/fetch").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"billerId\":" + biller + ",\"cardLast4\":\"4321\",\"mobile\":\"9876543210\"}"), token, 200).at("/data/fetchRef").asText();
            assertThat(call(pay(fetchRef, "500"), token, 201).at("/data/status").asText()).isEqualTo("SUCCESS");
        }
        // Platform Admin has no business.
        call(post("/api/v1/add-funds/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryCode\":\"EDU\",\"cardNetwork\":\"VISA\",\"cardHolder\":\"Test Holder\",\"amount\":1000}"), admin, 403);
        call(get("/api/v1/beneficiaries"), admin, 403);
    }

    private long idOf(String token, String accountCode) throws Exception {
        for (JsonNode u : call(get("/api/v1/users"), token, 200).at("/data")) {
            if (accountCode.equals(u.at("/accountCode").asText())) return u.at("/id").asLong();
        }
        if ("LOCAL-ADMIN-001".equals(accountCode)) return call(get("/api/v1/auth/me"), token, 200).at("/data/accountId").asLong();
        throw new AssertionError("No account " + accountCode);
    }

    private JsonNode createUser(String token, String code, String role, long parentId, int expected) throws Exception {
        String username = code.toLowerCase().replace("-", ".");
        return call(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountCode":"%s","displayName":"Test %s","role":"%s","parentId":%d,"username":"%s","initialPassword":"Hier-User-Pass-123",
                 "email":"%s@example.com","businessName":"Biz %s"}""".formatted(code, code, role, parentId, username, username, code)),
                token, expected);
    }
    // ------------------------------------------------------------------ helpers

    private JsonNode startSession(String category, String amount) throws Exception {
        return call(post("/api/v1/add-funds/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryCode\":\"" + category + "\",\"cardNetwork\":\"RUPAY\",\"cardHolder\":\"Priya Nair\",\"amount\":" + amount + "}"), rt, 201)
                .at("/data");
    }

    private MockHttpServletRequestBuilder complete(String ref, String otp) {
        return post("/api/v1/add-funds/sessions/" + ref + "/complete").contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}");
    }

    private long billerId() throws Exception {
        return call(get("/api/v1/bills/billers?category=CREDIT_CARD"), rt, 200).at("/data/0/id").asLong();
    }

    private JsonNode fetch(String last4, String mobile) throws Exception {
        return call(post("/api/v1/bills/fetch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"billerId\":" + billerId() + ",\"cardLast4\":\"" + last4 + "\",\"mobile\":\"" + mobile + "\"}"), rt, 200).at("/data");
    }

    private MockHttpServletRequestBuilder pay(String fetchRef, String amount) {
        return post("/api/v1/bills/pay").header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fetchRef\":\"" + fetchRef + "\",\"amount\":" + amount + "}");
    }

    private MockHttpServletRequestBuilder addAccount(String number, String holder) {
        return post("/api/v1/beneficiaries").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + number + "\",\"ifsc\":\"SBIN0001234\",\"holderName\":\"" + holder + "\"}");
    }

    private MockHttpServletRequestBuilder withdraw(long id, String amount) {
        return post("/api/v1/withdraw").header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"beneficiaryId\":" + id + ",\"amount\":" + amount + ",\"mode\":\"NEFT\"}");
    }

    private BigDecimal balance(String token) throws Exception {
        return call(get("/api/v1/wallets/me"), token, 200).at("/data/availableBalance").decimalValue();
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
}
