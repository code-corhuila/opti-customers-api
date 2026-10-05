package co.edu.corhuila.opti.customers.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientFilter;
import co.edu.corhuila.opti.customers.domain.model.DocumentType;
import co.edu.corhuila.opti.customers.domain.model.DomainException;
import co.edu.corhuila.opti.customers.domain.model.ErrorKind;
import co.edu.corhuila.opti.customers.domain.model.EyeMeasure;
import co.edu.corhuila.opti.customers.domain.model.FieldError;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;
import co.edu.corhuila.opti.customers.domain.model.Patient;
import co.edu.corhuila.opti.customers.domain.model.PatientStatus;
import co.edu.corhuila.opti.customers.testsupport.Fixtures;
import co.edu.corhuila.opti.customers.testsupport.TestClock;

class PatientServiceTest {

    private static final String KEY = "register-0001";

    private TestClock clock;
    private PatientUseCases service;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.START);
        service = Fixtures.service(clock);
    }

    @Test
    void registersAnActivePatient() {
        var result = service.register(Fixtures.validPatient(), KEY);

        assertThat(result.created()).isTrue();
        assertThat(result.value().status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(result.value().fullName()).isEqualTo("Laura Marcela Ortega Ruiz");
        assertThat(service.get(result.value().id())).isEqualTo(result.value());
    }

    @Test
    void repeatingTheKeyReturnsTheSamePatientWithoutCreatingAnother() {
        var first = service.register(Fixtures.validPatient(), KEY);
        var second = service.register(Fixtures.validPatient(), KEY);

        assertThat(second.created()).isFalse();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        assertThat(service.search(new PatientFilter(null, null, null), PageQuery.first(20)).total()).isEqualTo(1);
    }

    @Test
    void rejectsADuplicateDocumentWithADifferentKey() {
        service.register(Fixtures.validPatient(), KEY);

        assertThatThrownBy(() -> service.register(Fixtures.validPatient(), "register-0002"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        var invalid = new Patient.RegisterData(null, "12", "A", " ", "abc", "not-an-email", "S", null,
                LocalDate.of(2999, 1, 1));

        assertThatThrownBy(() -> service.register(invalid, "short"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(e.fields()).extracting(FieldError::field).containsExactlyInAnyOrder(
                            "Idempotency-Key", "documentType", "documentNumber", "firstName", "lastName",
                            "phone", "email", "eps", "birthDate");
                });
    }

    @Test
    void unknownPatientIsNotFound() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void searchesByDocumentOrName() {
        service.register(Fixtures.patient("1075243890"), "register-0001");
        service.register(Fixtures.patient("55238471"), "register-0002");

        var byDocument = service.search(new PatientFilter("55238471", null, null), PageQuery.first(20));
        var byName = service.search(new PatientFilter("ortega", null, null), PageQuery.first(20));
        var none = service.search(new PatientFilter("zzz", null, null), PageQuery.first(20));

        assertThat(byDocument.total()).isEqualTo(1);
        assertThat(byName.total()).isEqualTo(2);
        assertThat(none.data()).isEmpty();
    }

    @Test
    void listsNewestFirstAndBoundsThePage() {
        for (int i = 0; i < 3; i++) {
            service.register(Fixtures.patient("10000000" + i), "register-000" + i);
            clock.advance(Duration.ofMinutes(1));
        }

        var page = service.search(new PatientFilter(null, null, null), new PageQuery(1, 2));

        assertThat(page.data()).hasSize(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.data().get(0).documentNumber()).isEqualTo("100000002");
    }

    @Test
    void newFormulaSupersedesThePreviousOne() {
        UUID patientId = service.register(Fixtures.validPatient(), KEY).value().id();
        var first = service.addFormula(patientId, Fixtures.validFormula(), "formula-0001").value();
        clock.advance(Duration.ofMinutes(1));
        var second = service.addFormula(patientId, Fixtures.validFormula(), "formula-0002").value();

        assertThat(service.currentFormula(patientId).id()).isEqualTo(second.id());
        var history = service.formulas(patientId, PageQuery.first(20));
        assertThat(history.total()).isEqualTo(2);
        assertThat(history.data()).filteredOn(OpticalFormula::current).hasSize(1);
        assertThat(history.data()).extracting(OpticalFormula::id).contains(first.id());
    }

    @Test
    void repeatedFormulaKeyDoesNotAddAnotherFormula() {
        UUID patientId = service.register(Fixtures.validPatient(), KEY).value().id();
        service.addFormula(patientId, Fixtures.validFormula(), "formula-0001");
        var replay = service.addFormula(patientId, Fixtures.validFormula(), "formula-0001");

        assertThat(replay.created()).isFalse();
        assertThat(service.formulas(patientId, PageQuery.first(20)).total()).isEqualTo(1);
    }

    @Test
    void formulaNeedsAxisWhenThereIsCylinderAndKeepsAxisInRange() {
        UUID patientId = service.register(Fixtures.validPatient(), KEY).value().id();
        var data = new OpticalFormula.Data(
                new EyeMeasure(new BigDecimal("-1.50"), new BigDecimal("-0.75"), null, null),
                new EyeMeasure(new BigDecimal("-1.30"), null, 181, null),
                new BigDecimal("90"), null, "Dr", LocalDate.of(2999, 1, 1));

        assertThatThrownBy(() -> service.addFormula(patientId, data, "formula-0001"))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.fields()).extracting(FieldError::field).containsExactlyInAnyOrder(
                                "od.axis", "oi.sphere", "oi.axis", "pupillaryDistance", "lensType",
                                "optometristName", "formulaDate"));
    }

    @Test
    void patientWithoutFormulaHasNoCurrentOne() {
        UUID patientId = service.register(Fixtures.validPatient(), KEY).value().id();

        assertThatThrownBy(() -> service.currentFormula(patientId))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void flaggingIsIdempotentAndANewFormulaClearsIt() {
        UUID patientId = service.register(Fixtures.validPatient(), KEY).value().id();

        assertThat(service.flagControlOverdue(patientId).status()).isEqualTo(PatientStatus.CONTROL_OVERDUE);
        assertThat(service.flagControlOverdue(patientId).status()).isEqualTo(PatientStatus.CONTROL_OVERDUE);

        service.addFormula(patientId, Fixtures.validFormula(), "formula-0001");
        assertThat(service.get(patientId).status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(service.get(patientId).lastControlDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void controlDueFilterFindsPatientsWhoseLastControlIsOld() {
        service.register(Fixtures.validPatient(), KEY);

        var due = service.search(new PatientFilter(null, PatientStatus.ACTIVE, LocalDate.of(2026, 9, 30)),
                PageQuery.first(20));
        var notDue = service.search(new PatientFilter(null, PatientStatus.ACTIVE, LocalDate.of(2026, 9, 29)),
                PageQuery.first(20));

        assertThat(due.total()).isEqualTo(1);
        assertThat(notDue.total()).isZero();
    }

    @Test
    void summaryCountsTotalActiveAndPendingControls() {
        service.register(Fixtures.validPatient(), KEY);
        UUID overdueId = service.register(Fixtures.patient("1075243891"), "register-0002").value().id();
        service.register(Fixtures.patient("1075243892"), "register-0003");
        service.flagControlOverdue(overdueId);

        var summary = service.summary();

        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.active()).isEqualTo(2);
        assertThat(summary.pendingControls()).isEqualTo(1);
    }

    @Test
    void contactUpdateKeepsIdentityAndValidatesFields() {
        UUID id = service.register(Fixtures.validPatient(), KEY).value().id();

        Patient updated = service.updateContact(id, new Patient.ContactData("3001112233", null, "Nueva EPS", null));

        assertThat(updated.phone()).isEqualTo("3001112233");
        assertThat(updated.documentType()).isEqualTo(DocumentType.CC);
        assertThatThrownBy(() -> service.updateContact(id, new Patient.ContactData("x", "bad", "N", null)))
                .isInstanceOf(DomainException.class);
    }
}
