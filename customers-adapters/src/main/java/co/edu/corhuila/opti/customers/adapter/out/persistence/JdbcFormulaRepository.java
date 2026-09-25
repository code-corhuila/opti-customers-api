package co.edu.corhuila.opti.customers.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.corhuila.opti.customers.application.port.in.PageQuery;
import co.edu.corhuila.opti.customers.application.port.in.PageResult;
import co.edu.corhuila.opti.customers.application.port.out.Created;
import co.edu.corhuila.opti.customers.application.port.out.FormulaRepository;
import co.edu.corhuila.opti.customers.domain.model.EyeMeasure;
import co.edu.corhuila.opti.customers.domain.model.LensType;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;

/** PostgreSQL implementation of {@link FormulaRepository}. */
public class JdbcFormulaRepository implements FormulaRepository {

    private static final String TYPE = "OPTICAL_FORMULA";
    private static final String COLUMNS = """
            id, patient_id, od_sphere, od_cylinder, od_axis, od_addition, oi_sphere, oi_cylinder, oi_axis,
            oi_addition, pupillary_distance, lens_type, optometrist_name, formula_date, is_current, created_at""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final IdempotencyKeys keys;

    public JdbcFormulaRepository(JdbcClient jdbc, TransactionTemplate tx, IdempotencyKeys keys) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.keys = keys;
    }

    @Override
    public Created<OpticalFormula> saveAsCurrent(OpticalFormula f, String idempotencyKey) {
        return tx.execute(status -> {
            if (!keys.claim(idempotencyKey, TYPE, f.id())) {
                UUID existing = keys.find(idempotencyKey, TYPE).orElseThrow();
                return new Created<>(findById(existing).orElseThrow(), false);
            }
            jdbc.sql("UPDATE optical_formula SET is_current = FALSE WHERE patient_id = :patient AND is_current")
                    .param("patient", f.patientId()).update();
            insert(f);
            return new Created<>(f, true);
        });
    }

    @Override
    public Optional<OpticalFormula> findCurrent(UUID patientId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM optical_formula WHERE patient_id = :patient AND is_current")
                .param("patient", patientId).query(JdbcFormulaRepository::map).optional();
    }

    @Override
    public PageResult<OpticalFormula> list(UUID patientId, PageQuery page) {
        long total = jdbc.sql("SELECT count(*) FROM optical_formula WHERE patient_id = :patient")
                .param("patient", patientId).query(Long.class).single();
        List<OpticalFormula> rows = jdbc.sql("SELECT " + COLUMNS + " FROM optical_formula WHERE patient_id = :patient"
                        + " ORDER BY formula_date DESC, created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("patient", patientId).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcFormulaRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    private Optional<OpticalFormula> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM optical_formula WHERE id = :id")
                .param("id", id).query(JdbcFormulaRepository::map).optional();
    }

    private void insert(OpticalFormula f) {
        jdbc.sql("INSERT INTO optical_formula (" + COLUMNS + ") VALUES (:id, :patient, :odSph, :odCyl, :odAxis,"
                        + " :odAdd, :oiSph, :oiCyl, :oiAxis, :oiAdd, :pd, :lens, :optometrist, :date, :current,"
                        + " :createdAt)")
                .param("id", f.id()).param("patient", f.patientId())
                .param("odSph", f.od().sphere()).param("odCyl", f.od().cylinder())
                .param("odAxis", f.od().axis()).param("odAdd", f.od().addition())
                .param("oiSph", f.oi().sphere()).param("oiCyl", f.oi().cylinder())
                .param("oiAxis", f.oi().axis()).param("oiAdd", f.oi().addition())
                .param("pd", f.pupillaryDistance()).param("lens", f.lensType().name())
                .param("optometrist", f.optometristName()).param("date", f.formulaDate())
                .param("current", f.current()).param("createdAt", Sql.ts(f.createdAt()))
                .update();
    }

    private static OpticalFormula map(ResultSet rs, int row) throws SQLException {
        EyeMeasure od = new EyeMeasure(rs.getBigDecimal("od_sphere"), rs.getBigDecimal("od_cylinder"),
                Sql.integer(rs, "od_axis"), rs.getBigDecimal("od_addition"));
        EyeMeasure oi = new EyeMeasure(rs.getBigDecimal("oi_sphere"), rs.getBigDecimal("oi_cylinder"),
                Sql.integer(rs, "oi_axis"), rs.getBigDecimal("oi_addition"));
        return OpticalFormula.rehydrate(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                od, oi, rs.getBigDecimal("pupillary_distance"), LensType.valueOf(rs.getString("lens_type")),
                rs.getString("optometrist_name"), Sql.date(rs, "formula_date"), rs.getBoolean("is_current"),
                Sql.instant(rs, "created_at"));
    }
}
