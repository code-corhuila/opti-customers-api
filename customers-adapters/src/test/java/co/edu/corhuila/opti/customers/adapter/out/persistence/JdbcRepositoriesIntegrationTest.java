package co.edu.corhuila.opti.customers.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientFilter;
import co.edu.corhuila.opti.customers.domain.model.DomainException;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;
import co.edu.corhuila.opti.customers.domain.model.Patient;
import co.edu.corhuila.opti.customers.domain.model.PatientStatus;
import co.edu.corhuila.opti.customers.testsupport.Fixtures;

/**
 * Talks to a real PostgreSQL carrying the schema of opti-customers-db.
 * {@code TEST_DATABASE_URL} example: {@code jdbc:postgresql://localhost:5432/customers?user=x&password=y}.
 * Without it the test is skipped, not failed.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcRepositoriesIntegrationTest {

    private static final AtomicLong DOCUMENT = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    private static JdbcPatientRepository patients;
    private static JdbcFormulaRepository formulas;

    @BeforeAll
    static void connect() {
        var dataSource = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"));
        dataSource.setSchema("customers");
        var jdbc = JdbcClient.create(dataSource);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var keys = new IdempotencyKeys(jdbc);
        patients = new JdbcPatientRepository(jdbc, tx, keys);
        formulas = new JdbcFormulaRepository(jdbc, tx, keys);
    }

    @Test
    void savesAndReadsBackAPatientWithAllItsFields() {
        Patient saved = newPatient();

        patients.saveIdempotent(saved, key());
        Patient read = patients.findById(saved.id()).orElseThrow();

        assertThat(read.documentNumber()).isEqualTo(saved.documentNumber());
        assertThat(read.fullName()).isEqualTo("Laura Marcela Ortega Ruiz");
        assertThat(read.birthDate()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(read.status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(read.lastControlDate()).isEqualTo(TODAY);
        assertThat(read.createdAt()).isEqualTo(saved.createdAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void sameKeyStoresNothingNewAndReturnsTheFirstPatient() {
        String key = key();
        Patient first = newPatient();
        Patient second = newPatient();

        var created = patients.saveIdempotent(first, key);
        var replay = patients.saveIdempotent(second, key);

        assertThat(created.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.value().id()).isEqualTo(first.id());
        assertThat(patients.findById(second.id())).isEmpty();
        assertThat(patients.findByIdempotencyKey(key)).map(Patient::id).contains(first.id());
    }

    @Test
    void duplicateDocumentRollsBackTheKeyToo() {
        Patient first = newPatient();
        patients.saveIdempotent(first, key());
        Patient sameDocument = Patient.register(UUID.randomUUID(),
                new Patient.RegisterData(first.documentType(), first.documentNumber(), "Otra", "Persona", "3001112233",
                        null, "EPS Sanitas", null, null), TODAY, Instant.now());
        String lostKey = key();

        assertThatThrownBy(() -> patients.saveIdempotent(sameDocument, lostKey)).isInstanceOf(DomainException.class);

        assertThat(patients.findByIdempotencyKey(lostKey)).isEmpty();
        assertThat(patients.existsByDocument(first.documentType(), first.documentNumber())).isTrue();
    }

    @Test
    void searchFiltersPagesAndOrdersNewestFirst() {
        String prefix = String.valueOf(700_000_000L + DOCUMENT.incrementAndGet() % 100_000L);
        for (int i = 0; i < 3; i++) {
            Patient p = Patient.register(UUID.randomUUID(), Fixtures.patient(prefix + i), TODAY,
                    Instant.parse("2026-09-29T15:00:00Z").plusSeconds(i));
            patients.saveIdempotent(p, key());
        }

        var page = patients.search(new PatientFilter(prefix, null, null), new PageQuery(1, 2));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.data()).hasSize(2);
        assertThat(page.data().get(0).documentNumber()).isEqualTo(prefix + 2);
        assertThat(patients.search(new PatientFilter(prefix + "%", null, null), PageQuery.first(5)).total())
                .as("LIKE wildcards are escaped").isZero();
    }

    @Test
    void updateChangesContactAndStatus() {
        Patient p = newPatient();
        patients.saveIdempotent(p, key());

        patients.update(p.updateContact(new Patient.ContactData("3009998877", null, "Nueva EPS", "Pitalito"))
                .flagControlOverdue());

        Patient read = patients.findById(p.id()).orElseThrow();
        assertThat(read.phone()).isEqualTo("3009998877");
        assertThat(read.eps()).isEqualTo("Nueva EPS");
        assertThat(read.status()).isEqualTo(PatientStatus.CONTROL_OVERDUE);
    }

    @Test
    void newFormulaSupersedesTheCurrentOneAndStaysUniqueInTheDatabase() {
        Patient p = newPatient();
        patients.saveIdempotent(p, key());
        OpticalFormula first = OpticalFormula.register(UUID.randomUUID(), p.id(), Fixtures.validFormula(), TODAY, Instant.now());
        OpticalFormula second = OpticalFormula.register(UUID.randomUUID(), p.id(), Fixtures.validFormula(), TODAY,
                Instant.now().plusSeconds(5));

        formulas.saveAsCurrent(first, key());
        formulas.saveAsCurrent(second, key());

        assertThat(formulas.findCurrent(p.id())).map(OpticalFormula::id).contains(second.id());
        var history = formulas.list(p.id(), PageQuery.first(10));
        assertThat(history.total()).isEqualTo(2);
        assertThat(history.data()).filteredOn(OpticalFormula::current).hasSize(1);
        assertThat(history.data().get(0).od().axis()).isEqualTo(90);
        assertThat(history.data().get(0).pupillaryDistance()).isEqualByComparingTo("62.5");
    }

    @Test
    void repeatedFormulaKeyDoesNotSupersedeAnything() {
        Patient p = newPatient();
        patients.saveIdempotent(p, key());
        String key = key();
        OpticalFormula first = OpticalFormula.register(UUID.randomUUID(), p.id(), Fixtures.validFormula(), TODAY, Instant.now());
        OpticalFormula retry = OpticalFormula.register(UUID.randomUUID(), p.id(), Fixtures.validFormula(), TODAY, Instant.now());

        formulas.saveAsCurrent(first, key);
        var replay = formulas.saveAsCurrent(retry, key);

        assertThat(replay.created()).isFalse();
        assertThat(replay.value().id()).isEqualTo(first.id());
        assertThat(formulas.list(p.id(), PageQuery.first(10)).total()).isEqualTo(1);
    }

    @Test
    void summaryCountsTotalActiveAndPendingControls() {
        Patient active = newPatient();
        Patient overdue = newPatient();
        patients.saveIdempotent(active, key());
        patients.saveIdempotent(overdue, key());
        patients.update(overdue.flagControlOverdue());

        var summary = patients.summary();

        assertThat(summary.total()).isGreaterThanOrEqualTo(2);
        assertThat(summary.active()).isGreaterThanOrEqualTo(1);
        assertThat(summary.pendingControls()).isGreaterThanOrEqualTo(1);
    }

    private static Patient newPatient() {
        return Patient.register(UUID.randomUUID(), Fixtures.patient(String.valueOf(DOCUMENT.incrementAndGet())),
                TODAY, Instant.now());
    }

    private static String key() {
        return "it-" + UUID.randomUUID();
    }
}
