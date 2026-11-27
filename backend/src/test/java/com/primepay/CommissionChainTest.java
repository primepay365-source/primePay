package com.primepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
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
 * The complete hierarchy and commission workflow on a clean database: Admin → Super → Master → Distributor →
 * Retailer, each created by its parent through the API; a sandbox bill payment; and every level's share checked
 * against the commission rule as configured (read from the API, not assumed).
 */
@SpringBootTest(properties = {
        "app.dev.bootstrap.username=admin@primepay.local",
        "app.dev.bootstrap.password=Chain-Admin-Pass-1",
        "app.dev.seed.password=Chain-Seed-Pass-1",
        "app.dev.seed.sample-data=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommissionChainTest {
    private static final String PW = "Chain-User-Pass-123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String admin, sd, md, dt, rt;
    long adminId, sdId, mdId, dtId, rtId;
    long billTxnId;

    @Test @Order(1)
    void eachLevelCreatesTheNextThroughTheApi() throws Exception {
        admin = login("admin@primepay.local", "Chain-Admin-Pass-1");
        adminId = call(get("/api/v1/auth/me"), admin, 200).at("/data/accountId").asLong();
        sdId = create(admin, "C-SD-1", "SUPER_DISTRIBUTOR", adminId);
        sd = login("c.sd.1", PW);
        mdId = create(sd, "C-MD-1", "MASTER_DISTRIBUTOR", sdId);
        md = login("c.md.1", PW);
        dtId = create(md, "C-DT-1", "DISTRIBUTOR", mdId);
        dt = login("c.dt.1", PW);
        rtId = create(dt, "C-RT-1", "RETAILER", dtId);
        rt = login("c.rt.1", PW);

        // Parent links are what each creator chose.
        assertThat(call(get("/api/v1/users/" + rtId), admin, 200).at("/data/parentId").asLong()).isEqualTo(dtId);
        assertThat(call(get("/api/v1/users/" + dtId), admin, 200).at("/data/parentId").asLong()).isEqualTo(mdId);
        assertThat(call(get("/api/v1/users/" + mdId), admin, 200).at("/data/parentId").asLong()).isEqualTo(sdId);
        assertThat(call(get("/api/v1/users/" + sdId), admin, 200).at("/data/parentId").asLong()).isEqualTo(adminId);
        // Each dashboard opens with the right role.
        for (var e : Map.of(admin, "ADMIN", sd, "SUPER_DISTRIBUTOR", md, "MASTER_DISTRIBUTOR", dt, "DISTRIBUTOR", rt, "RETAILER").entrySet()) {
            assertThat(call(get("/api/v1/reports/dashboard"), e.getKey(), 200).at("/data/role").asText()).isEqualTo(e.getValue());
        }
        // Network counts on dashboards.
        JsonNode sdNet = call(get("/api/v1/reports/dashboard"), sd, 200).at("/data/network");
        assertThat(sdNet.toString()).contains("\"Total users\",\"value\":3");
    }

    @Test @Order(2)
    void sandboxBillPaymentPaysEveryLevelExactlyItsConfiguredShare() throws Exception {
        addFunds(rt, "5000");
        String fetchRef = fetch(rt, "4321");
        JsonNode paid = call(pay(fetchRef, "1000"), rt, 201).at("/data");
        assertThat(paid.at("/status").asText()).isEqualTo("SUCCESS");
        billTxnId = paid.at("/id").asLong();

        Map<String, BigDecimal> expected = expectedShares("BILL_PAYMENT", new BigDecimal("1000.00"));
        JsonNode commission = call(get("/api/v1/transactions/" + billTxnId), admin, 200).at("/data/commission");
        Map<String, BigDecimal> actual = new HashMap<>();
        commission.forEach(c -> actual.put(c.at("/levelRole").asText(), c.at("/amount").decimalValue()));
        assertThat(actual.keySet()).isEqualTo(expected.keySet());
        expected.forEach((level, value) -> assertThat(actual.get(level)).as(level).isEqualByComparingTo(value));
        BigDecimal allocated = actual.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal configured = expected.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo(configured);

        // Each earner's statement shows its own share, and the parent sees it under the right child.
        assertThat(earned(rt)).isEqualByComparingTo(expected.get("RETAILER"));
        assertThat(earned(dt)).isEqualByComparingTo(expected.get("DISTRIBUTOR"));
        assertThat(earned(md)).isEqualByComparingTo(expected.get("MASTER_DISTRIBUTOR"));
        assertThat(earned(sd)).isEqualByComparingTo(expected.get("SUPER_DISTRIBUTOR"));
        JsonNode dtChildren = call(get("/api/v1/commissions/statement"), dt, 200).at("/data/children");
        assertThat(childCommission(dtChildren, rtId)).isEqualByComparingTo(expected.get("DISTRIBUTOR"));
        JsonNode sdChildren = call(get("/api/v1/commissions/statement"), sd, 200).at("/data/children");
        assertThat(childCommission(sdChildren, mdId)).isEqualByComparingTo(expected.get("SUPER_DISTRIBUTOR"));
        JsonNode platform = call(get("/api/v1/commissions/statement"), admin, 200).at("/data");
        assertThat(platform.at("/scope").asText()).isEqualTo("PLATFORM");
        assertThat(platform.at("/earnedAllTime").decimalValue()).isEqualByComparingTo(configured);
        assertThat(platform.at("/platformShareAllTime").decimalValue()).isEqualByComparingTo(expected.get("PLATFORM"));
    }

    @Test @Order(3)
    void pendingCommissionIsShownThenCreditedOnceOnly() throws Exception {
        JsonNode pending = call(pay(fetch(rt, "1111"), "500"), rt, 201).at("/data");
        assertThat(pending.at("/status").asText()).isEqualTo("PENDING");
        Map<String, BigDecimal> expected = expectedShares("BILL_PAYMENT", new BigDecimal("500.00"));
        JsonNode st = call(get("/api/v1/commissions/statement"), dt, 200).at("/data");
        assertThat(st.at("/pendingAmount").decimalValue()).isEqualByComparingTo(expected.get("DISTRIBUTOR"));
        assertThat(st.at("/pendingTransactions").asLong()).isEqualTo(1);

        BigDecimal before = earned(dt);
        long id = pending.at("/id").asLong();
        assertThat(call(post("/api/v1/bills/" + id + "/recheck"), rt, 200).at("/data/status").asText()).isEqualTo("SUCCESS");
        // Asking again is harmless: it returns the final status and credits nothing more.
        assertThat(call(post("/api/v1/bills/" + id + "/recheck"), rt, 200).at("/data/status").asText()).isEqualTo("SUCCESS");
        assertThat(earned(dt)).isEqualByComparingTo(before.add(expected.get("DISTRIBUTOR")));
        assertThat(call(get("/api/v1/transactions/" + id), admin, 200).at("/data/commission").size()).isEqualTo(expected.size());
        assertThat(call(get("/api/v1/commissions/statement"), dt, 200).at("/data/pendingAmount").decimalValue()).isZero();
    }

    @Test @Order(4)
    void everyoneSeesOnlyWhatTheirRoleAllows() throws Exception {
        // Only the Platform Admin configures commission.
        String rule = """
                {"ruleCode":"CR-HACK-1","serviceCode":"BILL_PAYMENT","calcType":"PERCENT","effectiveFrom":"2026-01-01",
                 "shares":[{"levelRole":"DISTRIBUTOR","rate":5}]}""";
        for (String token : new String[] {sd, md, dt, rt}) {
            call(post("/api/v1/commissions/rules").contentType(MediaType.APPLICATION_JSON).content(rule), token, 403);
        }
        // Rates: own level and levels you can create; nobody else's.
        call(get("/api/v1/commissions/rates?role=RETAILER"), rt, 200);
        call(get("/api/v1/commissions/rates?role=DISTRIBUTOR"), rt, 403);
        call(get("/api/v1/commissions/rates?role=SUPER_DISTRIBUTOR"), md, 403);
        call(get("/api/v1/commissions/rates?role=RETAILER"), md, 200);
        // A statement only ever contains the caller's own earnings.
        JsonNode rtLines = call(get("/api/v1/commissions/statement"), rt, 200).at("/data/lines");
        rtLines.forEach(l -> assertThat(l.at("/levelRole").asText()).isEqualTo("RETAILER"));
        // User access follows the hierarchy.
        call(get("/api/v1/users/" + dtId), rt, 404);
        assertThat(call(get("/api/v1/users"), rt, 200).at("/data").size()).isZero();
        call(get("/api/v1/users/" + sdId), md, 404);
        call(get("/api/v1/users/" + rtId), md, 200);
        call(get("/api/v1/audit"), sd, 403);
        call(get("/api/v1/reports/audit"), dt, 403);
        // Rule validation: no level twice, never more than 100% in total.
        String twice = """
                {"ruleCode":"CR-BAD-1","serviceCode":"BILL_PAYMENT","calcType":"PERCENT","effectiveFrom":"2027-01-01",
                 "shares":[{"levelRole":"RETAILER","rate":1},{"levelRole":"RETAILER","rate":1}]}""";
        call(post("/api/v1/commissions/rules").contentType(MediaType.APPLICATION_JSON).content(twice), admin, 400);
        String tooMuch = """
                {"ruleCode":"CR-BAD-2","serviceCode":"BILL_PAYMENT","calcType":"PERCENT","effectiveFrom":"2027-01-01",
                 "shares":[{"levelRole":"RETAILER","rate":60},{"levelRole":"DISTRIBUTOR","rate":50}]}""";
        call(post("/api/v1/commissions/rules").contentType(MediaType.APPLICATION_JSON).content(tooMuch), admin, 400);
    }

    // ---------------------------------------------------------------- helpers

    /** Expected share per level for an amount, computed from the active rule exactly as configured. */
    private Map<String, BigDecimal> expectedShares(String service, BigDecimal amount) throws Exception {
        Map<String, BigDecimal> out = new HashMap<>();
        for (JsonNode r : call(get("/api/v1/commissions/rules"), admin, 200).at("/data")) {
            if (!service.equals(r.at("/serviceCode").asText()) || !"Active".equals(r.at("/state").asText())) continue;
            for (JsonNode s : r.at("/shares")) {
                BigDecimal v = "FIXED".equals(r.at("/calcType").asText()) ? s.at("/fixedAmount").decimalValue()
                        : amount.multiply(s.at("/rate").decimalValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (!s.at("/minAmount").isNull() && v.compareTo(s.at("/minAmount").decimalValue()) < 0) v = s.at("/minAmount").decimalValue();
                if (!s.at("/maxAmount").isNull() && v.compareTo(s.at("/maxAmount").decimalValue()) > 0) v = s.at("/maxAmount").decimalValue();
                out.put(s.at("/levelRole").asText(), v.setScale(2, RoundingMode.HALF_UP));
            }
            return out;
        }
        throw new AssertionError("No active rule for " + service);
    }

    private BigDecimal earned(String token) throws Exception {
        return call(get("/api/v1/commissions/statement"), token, 200).at("/data/earnedAllTime").decimalValue();
    }

    private static BigDecimal childCommission(JsonNode children, long id) {
        for (JsonNode c : children) if (c.at("/accountId").asLong() == id) return c.at("/commission").decimalValue();
        throw new AssertionError("child " + id + " missing in " + children);
    }

    private void addFunds(String token, String amount) throws Exception {
        String ref = call(post("/api/v1/add-funds/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryCode\":\"EDU\",\"cardNetwork\":\"VISA\",\"cardHolder\":\"Test Holder\",\"amount\":" + amount + "}"), token, 201)
                .at("/data/sessionRef").asText();
        String otp = call(post("/api/v1/add-funds/sessions/" + ref + "/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CARD\",\"cardLast4\":\"4242\"}"), token, 200).at("/data/devOtp").asText();
        assertThat(call(post("/api/v1/add-funds/sessions/" + ref + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"" + otp + "\"}"), token, 200).at("/data/status").asText()).isEqualTo("SUCCESS");
    }

    private String fetch(String token, String last4) throws Exception {
        long biller = call(get("/api/v1/bills/billers"), token, 200).at("/data/0/id").asLong();
        return call(post("/api/v1/bills/fetch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"billerId\":" + biller + ",\"cardLast4\":\"" + last4 + "\",\"mobile\":\"9876543210\"}"), token, 200).at("/data/fetchRef").asText();
    }

    private MockHttpServletRequestBuilder pay(String fetchRef, String amount) {
        return post("/api/v1/bills/pay").header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fetchRef\":\"" + fetchRef + "\",\"amount\":" + amount + "}");
    }

    private long create(String token, String code, String role, long parentId) throws Exception {
        String username = code.toLowerCase().replace("-", ".");
        return call(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountCode":"%s","displayName":"Test %s","role":"%s","parentId":%d,"username":"%s","initialPassword":"%s",
                 "email":"%s@example.com","mobile":"+919800000000","businessName":"Biz %s"}""".formatted(code, code, role, parentId, username, PW, username, code)),
                token, 201).at("/data/id").asLong();
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
}
