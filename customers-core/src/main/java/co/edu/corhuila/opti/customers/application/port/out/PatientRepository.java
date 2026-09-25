package co.edu.corhuila.opti.customers.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PageResult;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientFilter;
import co.edu.corhuila.opti.customers.domain.model.DocumentType;
import co.edu.corhuila.opti.customers.domain.model.Patient;

/** Persistence the patient use cases need. */
public interface PatientRepository {

    /**
     * Stores the patient and its idempotency key in the same unit of work. When the key was
     * already used, nothing is stored and the patient created the first time is returned.
     */
    Created<Patient> saveIdempotent(Patient patient, String idempotencyKey);

    /** The patient created with this key, if the key was already used. */
    Optional<Patient> findByIdempotencyKey(String idempotencyKey);

    boolean existsByDocument(DocumentType type, String number);

    Optional<Patient> findById(UUID id);

    PageResult<Patient> search(PatientFilter filter, PageQuery page);

    void update(Patient patient);
}
