package co.edu.corhuila.opti.customers.testsupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PageResult;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientFilter;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientSummary;
import co.edu.corhuila.opti.customers.application.port.out.Created;
import co.edu.corhuila.opti.customers.application.port.out.PatientRepository;
import co.edu.corhuila.opti.customers.domain.model.DocumentType;
import co.edu.corhuila.opti.customers.domain.model.Patient;
import co.edu.corhuila.opti.customers.domain.model.PatientStatus;

/** Fake of the patient store, used to test the core and the HTTP adapter without a database. */
public class InMemoryPatientRepository implements PatientRepository {

    private final Map<UUID, Patient> byId = new HashMap<>();
    private final Map<String, UUID> byKey = new HashMap<>();

    @Override
    public Created<Patient> saveIdempotent(Patient patient, String idempotencyKey) {
        UUID existing = byKey.get(idempotencyKey);
        if (existing != null) {
            return new Created<>(byId.get(existing), false);
        }
        byKey.put(idempotencyKey, patient.id());
        byId.put(patient.id(), patient);
        return new Created<>(patient, true);
    }

    @Override
    public Optional<Patient> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(byKey.get(idempotencyKey)).map(byId::get);
    }

    @Override
    public boolean existsByDocument(DocumentType type, String number) {
        return byId.values().stream()
                .anyMatch(p -> p.documentType() == type && p.documentNumber().equals(number));
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public PageResult<Patient> search(PatientFilter filter, PageQuery page) {
        List<Patient> matches = new ArrayList<>(byId.values().stream()
                .filter(p -> filter.status() == null || p.status() == filter.status())
                .filter(p -> filter.controlDueBefore() == null || p.lastControlDate().isBefore(filter.controlDueBefore()))
                .filter(p -> filter.query() == null || filter.query().isBlank() || matches(p, filter.query()))
                .sorted(Comparator.comparing(Patient::createdAt).reversed().thenComparing(Patient::id))
                .toList());
        int from = Math.min(page.offset(), matches.size());
        int to = Math.min(from + page.limit(), matches.size());
        return new PageResult<>(matches.subList(from, to), page.page(), page.limit(), matches.size());
    }

    @Override
    public void update(Patient patient) {
        byId.put(patient.id(), patient);
    }

    @Override
    public PatientSummary summary() {
        long active = byId.values().stream().filter(p -> p.status() == PatientStatus.ACTIVE).count();
        long pending = byId.values().stream().filter(p -> p.status() == PatientStatus.CONTROL_OVERDUE).count();
        return new PatientSummary(byId.size(), active, pending);
    }

    private static boolean matches(Patient p, String query) {
        String q = query.trim().toLowerCase();
        return p.documentNumber().toLowerCase().contains(q) || p.fullName().toLowerCase().contains(q);
    }
}
