package co.edu.corhuila.opti.customers.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PageResult;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;

/** Persistence the formula use cases need. */
public interface FormulaRepository {

    /**
     * Stores the formula as the patient's current one, superseding the previous, together with
     * its idempotency key, in one unit of work. A repeated key stores nothing.
     */
    Created<OpticalFormula> saveAsCurrent(OpticalFormula formula, String idempotencyKey);

    Optional<OpticalFormula> findCurrent(UUID patientId);

    PageResult<OpticalFormula> list(UUID patientId, PageQuery page);
}
