package co.edu.corhuila.opti.customers.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PageResult;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientFilter;
import co.edu.corhuila.opti.customers.application.port.out.Created;
import co.edu.corhuila.opti.customers.application.port.out.PatientRepository;
import co.edu.corhuila.opti.customers.domain.model.DocumentType;
import co.edu.corhuila.opti.customers.domain.model.DomainException;
import co.edu.corhuila.opti.customers.domain.model.Patient;
import co.edu.corhuila.opti.customers.domain.model.PatientStatus;

/** PostgreSQL implementation of {@link PatientRepository}. The schema belongs to customers-db. */
public class JdbcPatientRepository implements PatientRepository {

    private static final String TYPE = "PATIENT";
    private static final String COLUMNS = """
            id, document_type, document_number, first_name, last_name, phone, email, eps, city,
            birth_date, status, last_control_date, created_at""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final IdempotencyKeys keys;

    public JdbcPatientRepository(JdbcClient jdbc, TransactionTemplate tx, IdempotencyKeys keys) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.keys = keys;
    }

    @Override
    public Created<Patient> saveIdempotent(Patient patient, String idempotencyKey) {
        return tx.execute(status -> {
            if (!keys.claim(idempotencyKey, TYPE, patient.id())) {
                UUID existing = keys.find(idempotencyKey, TYPE).orElseThrow();
                return new Created<>(findById(existing).orElseThrow(), false);
            }
            try {
                insert(patient);
            } catch (DuplicateKeyException e) {
                throw DomainException.rule("a patient with this document already exists");
            }
            return new Created<>(patient, true);
        });
    }

    @Override
    public Optional<Patient> findByIdempotencyKey(String idempotencyKey) {
        return keys.find(idempotencyKey, TYPE).flatMap(this::findById);
    }

    @Override
    public boolean existsByDocument(DocumentType type, String number) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM patient WHERE document_type = :type AND document_number = :number)")
                .param("type", type.name()).param("number", number)
                .query(Boolean.class).single();
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM patient WHERE id = :id")
                .param("id", id).query(JdbcPatientRepository::map).optional();
    }

    @Override
    public PageResult<Patient> search(PatientFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>();
        var params = new java.util.HashMap<String, Object>();
        if (filter.query() != null && !filter.query().isBlank()) {
            conditions.add("(lower(document_number) LIKE :q OR lower(first_name || ' ' || last_name) LIKE :q)");
            params.put("q", Sql.contains(filter.query()));
        }
        if (filter.status() != null) {
            conditions.add("status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.controlDueBefore() != null) {
            conditions.add("last_control_date < :controlDueBefore");
            params.put("controlDueBefore", filter.controlDueBefore());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT count(*) FROM patient" + where).params(params).query(Long.class).single();
        List<Patient> rows = jdbc.sql("SELECT " + COLUMNS + " FROM patient" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcPatientRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    @Override
    public void update(Patient p) {
        jdbc.sql("""
                UPDATE patient SET phone = :phone, email = :email, eps = :eps, city = :city,
                       status = :status, last_control_date = :lastControl
                WHERE id = :id
                """)
                .param("phone", p.phone()).param("email", p.email()).param("eps", p.eps()).param("city", p.city())
                .param("status", p.status().name()).param("lastControl", p.lastControlDate()).param("id", p.id())
                .update();
    }

    private void insert(Patient p) {
        jdbc.sql("INSERT INTO patient (" + COLUMNS + ") VALUES (:id, :type, :number, :first, :last, :phone, :email,"
                        + " :eps, :city, :birth, :status, :lastControl, :createdAt)")
                .param("id", p.id()).param("type", p.documentType().name()).param("number", p.documentNumber())
                .param("first", p.firstName()).param("last", p.lastName()).param("phone", p.phone())
                .param("email", p.email()).param("eps", p.eps()).param("city", p.city())
                .param("birth", p.birthDate()).param("status", p.status().name())
                .param("lastControl", p.lastControlDate())
                .param("createdAt", Sql.ts(p.createdAt()))
                .update();
    }

    private static Patient map(ResultSet rs, int row) throws SQLException {
        return Patient.rehydrate(rs.getObject("id", UUID.class), DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("document_number"), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("phone"), rs.getString("email"), rs.getString("eps"), rs.getString("city"),
                Sql.date(rs, "birth_date"), PatientStatus.valueOf(rs.getString("status")),
                Sql.date(rs, "last_control_date"), Sql.instant(rs, "created_at"));
    }
}
