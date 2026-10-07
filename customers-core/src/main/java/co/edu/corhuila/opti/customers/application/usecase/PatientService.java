package co.edu.corhuila.opti.customers.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PageResult;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientSummary;
import co.edu.corhuila.opti.customers.application.port.out.Created;
import co.edu.corhuila.opti.customers.application.port.out.FormulaRepository;
import co.edu.corhuila.opti.customers.application.port.out.IdGenerator;
import co.edu.corhuila.opti.customers.application.port.out.PatientRepository;
import co.edu.corhuila.opti.customers.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.customers.domain.model.DomainException;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;
import co.edu.corhuila.opti.customers.domain.model.Patient;
import co.edu.corhuila.opti.customers.domain.model.Validation;
import co.edu.corhuila.opti.customers.domain.model.Violations;

/** Implements the patient and formula operations on top of the outbound ports. */
public class PatientService implements PatientUseCases {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    private final PatientRepository patients;
    private final FormulaRepository formulas;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PatientService(PatientRepository patients, FormulaRepository formulas, IdGenerator ids,
                          UnitOfWork unitOfWork, Clock clock) {
        this.patients = patients;
        this.formulas = formulas;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Created<Patient> register(Patient.RegisterData data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Patient patient = v.check(() -> Patient.register(ids.next(), data, today(), clock.instant()));
        v.throwIfAny();
        return patients.findByIdempotencyKey(key)
                .map(existing -> new Created<>(existing, false))
                .orElseGet(() -> {
                    if (patients.existsByDocument(patient.documentType(), patient.documentNumber())) {
                        throw DomainException.rule("a patient with this document already exists");
                    }
                    return patients.saveIdempotent(patient, key);
                });
    }

    @Override
    public Patient get(UUID id) {
        return patients.findById(id).orElseThrow(() -> DomainException.notFound("patient not found"));
    }

    @Override
    public PageResult<Patient> search(PatientFilter filter, PageQuery page) {
        return patients.search(filter, page);
    }

    @Override
    public Patient updateContact(UUID id, Patient.ContactData data) {
        Patient updated = get(id).updateContact(data);
        patients.update(updated);
        return updated;
    }

    @Override
    public Patient flagControlOverdue(UUID id) {
        Patient flagged = get(id).flagControlOverdue();
        patients.update(flagged);
        return flagged;
    }

    @Override
    public PatientSummary summary() {
        return patients.summary();
    }

    @Override
    public Created<OpticalFormula> addFormula(UUID patientId, OpticalFormula.Data data, String idempotencyKey) {
        Patient patient = get(patientId);
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        OpticalFormula formula = v.check(
                () -> OpticalFormula.register(ids.next(), patientId, data, today(), clock.instant()));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            Created<OpticalFormula> result = formulas.saveAsCurrent(formula, key);
            if (result.created()) {
                patients.update(patient.controlDone(formula.formulaDate()));
            }
            return result;
        });
    }

    @Override
    public OpticalFormula currentFormula(UUID patientId) {
        get(patientId);
        return formulas.findCurrent(patientId)
                .orElseThrow(() -> DomainException.notFound("the patient has no current formula"));
    }

    @Override
    public PageResult<OpticalFormula> formulas(UUID patientId, PageQuery page) {
        get(patientId);
        return formulas.list(patientId, page);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }
}
