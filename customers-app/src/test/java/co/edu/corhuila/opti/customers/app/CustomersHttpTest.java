package co.edu.corhuila.opti.customers.app;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.edu.corhuila.opti.customers.testsupport.TestClock;

/**
 * Contract checks of the customers API, done over HTTP with the in-memory repositories:
 * authentication, error envelope, correlation, validation, idempotency, listings and roles.
 */
@SpringBootTest(classes = HttpTestApplication.class)
@AutoConfigureMockMvc
class CustomersHttpTest {

    private static final String ADMIN = "ADMIN";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(10_000);

    @Autowired
    MockMvc mvc;
    @Autowired
    TestClock clock;

    // ---- authentication -------------------------------------------------------------------

    @Test
    void healthNeedsNoToken() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void requestWithoutTokenIsUnauthorizedWithTheEnvelope() throws Exception {
        mvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void tokenWithAlgNoneIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients").header("Authorization", "Bearer " + TestTokens.algNone(clock.instant())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hs256TokenSignedWithThePublicKeyIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + TestTokens.hs256WithPublicKey(clock.instant())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients").header("Authorization", "Bearer " + TestTokens.expired(clock.instant())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + TestTokens.signedByOtherKey(clock.instant())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutSubjectIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients").header("Authorization", "Bearer " + TestTokens.withoutSubject(clock.instant())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---- envelope and correlation ---------------------------------------------------------

    @Test
    void malformedIdIsAValidationErrorNamingTheField() throws Exception {
        as(get("/api/v1/patients/not-a-uuid"), ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void traceIdRepeatsTheReceivedCorrelationIdAndIsReturnedInTheHeader() throws Exception {
        as(get("/api/v1/patients/" + UUID.randomUUID()).header("X-Correlation-Id", "e2e-test-1"), ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-Id", "e2e-test-1"))
                .andExpect(jsonPath("$.traceId").value("e2e-test-1"))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void correlationIdIsGeneratedWhenMissingOrUnsafe() throws Exception {
        as(get("/api/v1/patients"), ADMIN)
                .andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")));
        as(get("/api/v1/patients").header("X-Correlation-Id", "bad id with spaces\t"), ADMIN)
                .andExpect(header().string("X-Correlation-Id", not(containsString(" "))));
    }

    @Test
    void unknownRouteAnswersWithTheEnvelope() throws Exception {
        as(get("/api/v1/nothing-here"), ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void malformedJsonIsAValidationError() throws Exception {
        as(create("{ this is not json", "key-malformed-1"), ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownBodyFieldAndWrongEnumNameTheField() throws Exception {
        as(create("""
                {"documentType":"DNI","documentNumber":"1234567","surname":"x"}""", "key-unknown-01"), ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("documentType")));
        as(create("""
                {"documentType":"CC","documentNumber":"1234567","surname":"x"}""", "key-unknown-02"), ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("surname")));
    }

    @Test
    void invalidBodyNamesEveryFieldIncludingTheIdempotencyKeyHeader() throws Exception {
        as(post("/api/v1/patients").contentType(MediaType.APPLICATION_JSON).content("""
                {"documentType":"CC","documentNumber":"12","firstName":"A","lastName":"Ortega","phone":"abc",
                 "eps":"S","email":"nope"}"""), ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("Idempotency-Key")))
                .andExpect(jsonPath("$.details[*].field", hasItem("documentNumber")))
                .andExpect(jsonPath("$.details[*].field", hasItem("firstName")))
                .andExpect(jsonPath("$.details[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.details[*].field", hasItem("eps")))
                .andExpect(jsonPath("$.details[*].field", hasItem("email")));
    }

    // ---- idempotent creation and representation -------------------------------------------

    @Test
    void createReturns201WithLocationAndRetryReturns200WithTheSameId() throws Exception {
        String key = "key-" + UUID.randomUUID();
        String body = patientJson(nextDocument());

        String id = as(create(body, key), ADMIN)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/patients/")))
                .andExpect(jsonPath("$.id", matchesPattern("[0-9a-f-]{36}")))
                .andReturn().getResponse().getContentAsString();

        as(create(body, key), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOf(id)));
    }

    @Test
    void representationUsesCamelCaseAndRfc3339() throws Exception {
        String document = nextDocument();
        String id = idOf(as(create(patientJson(document), "key-" + UUID.randomUUID()), ADMIN)
                .andReturn().getResponse().getContentAsString());

        as(get("/api/v1/patients/" + id), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value(document))
                .andExpect(jsonPath("$.firstName").value("Laura Marcela"))
                .andExpect(jsonPath("$.fullName").value("Laura Marcela Ortega Ruiz"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-20"))
                .andExpect(jsonPath("$.createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z")))
                .andExpect(jsonPath("$.first_name").doesNotExist());
    }

    @Test
    void duplicateDocumentWithAnotherKeyIsABusinessRuleViolation() throws Exception {
        String body = patientJson(nextDocument());
        as(create(body, "key-" + UUID.randomUUID()), ADMIN).andExpect(status().isCreated());

        as(create(body, "key-" + UUID.randomUUID()), ADMIN)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    // ---- roles ----------------------------------------------------------------------------

    @Test
    void tokenWithoutTheRightRoleIsForbidden() throws Exception {
        as(create(patientJson(nextDocument()), "key-" + UUID.randomUUID()), "SOMEONE_ELSE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void onlyAdminOrServiceCanFlagAnOverdueControl() throws Exception {
        String id = idOf(as(create(patientJson(nextDocument()), "key-" + UUID.randomUUID()), ADMIN)
                .andReturn().getResponse().getContentAsString());

        as(post("/api/v1/patients/" + id + "/control-overdue"), "SELLER").andExpect(status().isForbidden());
        as(post("/api/v1/patients/" + id + "/control-overdue"), "SERVICE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTROL_OVERDUE"));
    }

    // ---- listings -------------------------------------------------------------------------

    @Test
    void listIsBoundedAndNewestFirstWithMeta() throws Exception {
        String prefix = "77" + SEQUENCE.incrementAndGet();
        for (int i = 0; i < 3; i++) {
            as(create(patientJson(prefix + i), "key-" + UUID.randomUUID()), ADMIN).andExpect(status().isCreated());
            clock.advance(Duration.ofMinutes(1));
        }

        as(get("/api/v1/patients?q=" + prefix + "&limit=2&page=1"), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].documentNumber").value(prefix + 2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void listWithoutLimitUsesTwentyAndAtMostThatMany() throws Exception {
        as(get("/api/v1/patients"), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    void limitAbove100OrPageZeroOrUnknownFilterIsRejected() throws Exception {
        as(get("/api/v1/patients?limit=101"), ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("limit"));
        as(get("/api/v1/patients?page=0"), ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("page"));
        as(get("/api/v1/patients?limit=abc"), ADMIN).andExpect(status().isBadRequest());
        as(get("/api/v1/patients?colour=red"), ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("colour"));
        as(get("/api/v1/patients?status=UNKNOWN"), ADMIN).andExpect(status().isBadRequest());
    }

    // ---- summary --------------------------------------------------------------------------

    @Test
    void summaryCountsTotalActiveAndPendingControls() throws Exception {
        String id = idOf(as(create(patientJson(nextDocument()), "key-" + UUID.randomUUID()), ADMIN)
                .andReturn().getResponse().getContentAsString());
        as(post("/api/v1/patients/" + id + "/control-overdue"), "SERVICE").andExpect(status().isOk());

        as(get("/api/v1/patients/summary"), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.active").isNumber())
                .andExpect(jsonPath("$.pendingControls").isNumber());
    }

    // ---- formulas -------------------------------------------------------------------------

    @Test
    void formulaCreationIsIdempotentAndBecomesTheCurrentOne() throws Exception {
        String patientId = idOf(as(create(patientJson(nextDocument()), "key-" + UUID.randomUUID()), ADMIN)
                .andReturn().getResponse().getContentAsString());
        String key = "formula-" + UUID.randomUUID();

        String formulaId = idOf(as(formula(patientId, validFormulaJson(), key), "OPTOMETRIST")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/formulas/")))
                .andReturn().getResponse().getContentAsString());
        as(formula(patientId, validFormulaJson(), key), "OPTOMETRIST")
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(formulaId));

        as(get("/api/v1/patients/" + patientId + "/formulas/current"), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(formulaId))
                .andExpect(jsonPath("$.od.axis").value(90))
                .andExpect(jsonPath("$.pupillaryDistance").value(62.5))
                .andExpect(jsonPath("$.current").value(true));
    }

    @Test
    void invalidFormulaNamesNestedFields() throws Exception {
        String patientId = idOf(as(create(patientJson(nextDocument()), "key-" + UUID.randomUUID()), ADMIN)
                .andReturn().getResponse().getContentAsString());

        as(formula(patientId, """
                {"od":{"sphere":-1.5,"cylinder":-0.75},"oi":{"sphere":-1.3,"axis":181},
                 "pupillaryDistance":90,"lensType":"MONOFOCAL","optometristName":"Ana Torres",
                 "formulaDate":"2026-09-20"}""", "formula-" + UUID.randomUUID()), "OPTOMETRIST")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("od.axis")))
                .andExpect(jsonPath("$.details[*].field", hasItem("oi.sphere")))
                .andExpect(jsonPath("$.details[*].field", hasItem("oi.axis")))
                .andExpect(jsonPath("$.details[*].field", hasItem("pupillaryDistance")));
    }

    @Test
    void axisWithDecimalsIsRejectedAsAnInvalidType() throws Exception {
        String patientId = idOf(as(create(patientJson(nextDocument()), "key-" + UUID.randomUUID()), ADMIN)
                .andReturn().getResponse().getContentAsString());

        as(formula(patientId, """
                {"od":{"sphere":-1.5,"cylinder":-0.75,"axis":90.5},"oi":{"sphere":-1.25},
                 "pupillaryDistance":62,"lensType":"MONOFOCAL","optometristName":"Ana Torres",
                 "formulaDate":"2026-09-20"}""", "formula-" + UUID.randomUUID()), "OPTOMETRIST")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("od.axis")));
    }

    @Test
    void patientWithoutFormulaHasNoCurrentOne() throws Exception {
        String patientId = idOf(as(create(patientJson(nextDocument()), "key-" + UUID.randomUUID()), ADMIN)
                .andReturn().getResponse().getContentAsString());

        as(get("/api/v1/patients/" + patientId + "/formulas/current"), ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ---- helpers --------------------------------------------------------------------------

    private ResultActions as(MockHttpServletRequestBuilder request, String... roles) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + TestTokens.valid(clock.instant(), roles)));
    }

    private static MockHttpServletRequestBuilder create(String json, String key) {
        return post("/api/v1/patients").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder formula(String patientId, String json, String key) {
        return post("/api/v1/patients/" + patientId + "/formulas").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String nextDocument() {
        return String.valueOf(SEQUENCE.incrementAndGet() * 1000L);
    }

    private static String patientJson(String document) {
        return """
                {"documentType":"CC","documentNumber":"%s","firstName":"Laura Marcela","lastName":"Ortega Ruiz",
                 "phone":"3104582291","email":"laura.ortega@gmail.com","eps":"EPS Sanitas","city":"Neiva",
                 "birthDate":"1990-05-20"}""".formatted(document);
    }

    private static String validFormulaJson() {
        return """
                {"od":{"sphere":-1.5,"cylinder":-0.75,"axis":90},"oi":{"sphere":-1.25},
                 "pupillaryDistance":62.5,"lensType":"MONOFOCAL","optometristName":"Ana Torres",
                 "formulaDate":"2026-09-20"}""";
    }

    private static String idOf(String json) {
        int start = json.indexOf("\"id\":\"") + 6;
        return json.substring(start, json.indexOf('"', start));
    }
}
