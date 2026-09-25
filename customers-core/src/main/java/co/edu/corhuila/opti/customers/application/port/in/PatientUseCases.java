package co.edu.corhuila.opti.customers.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

import co.edu.corhuila.opti.customers.application.port.out.Created;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;
import co.edu.corhuila.opti.customers.domain.model.Patient;
import co.edu.corhuila.opti.customers.domain.model.PatientStatus;

/** What the customers service offers about patients and their optical formulas. */
public interface PatientUseCases {

    Created<Patient> register(Patient.RegisterData data, String idempotencyKey);

    Patient get(UUID id);

    PageResult<Patient> search(PatientFilter filter, PageQuery page);

    Patient updateContact(UUID id, Patient.ContactData data);

    Patient flagControlOverdue(UUID id);

    Created<OpticalFormula> addFormula(UUID patientId, OpticalFormula.Data data, String idempotencyKey);

    OpticalFormula currentFormula(UUID patientId);

    PageResult<OpticalFormula> formulas(UUID patientId, PageQuery page);

    /** Listing criteria; every field is optional. */
    record PatientFilter(String query, PatientStatus status, LocalDate controlDueBefore) {
    }
}
