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
import co.edu.corhuila.opti.customers.application.port.out.Created;
import co.edu.corhuila.opti.customers.application.port.out.FormulaRepository;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;

/** Fake of the formula store: keeps the "only one current formula per patient" rule like the database does. */
public class InMemoryFormulaRepository implements FormulaRepository {

    private final List<OpticalFormula> all = new ArrayList<>();
    private final Map<String, UUID> byKey = new HashMap<>();

    @Override
    public Created<OpticalFormula> saveAsCurrent(OpticalFormula formula, String idempotencyKey) {
        UUID existing = byKey.get(idempotencyKey);
        if (existing != null) {
            return new Created<>(all.stream().filter(f -> f.id().equals(existing)).findFirst().orElseThrow(), false);
        }
        byKey.put(idempotencyKey, formula.id());
        all.replaceAll(f -> f.patientId().equals(formula.patientId()) && f.current() ? superseded(f) : f);
        all.add(formula);
        return new Created<>(formula, true);
    }

    @Override
    public Optional<OpticalFormula> findCurrent(UUID patientId) {
        return all.stream().filter(f -> f.patientId().equals(patientId) && f.current()).findFirst();
    }

    @Override
    public PageResult<OpticalFormula> list(UUID patientId, PageQuery page) {
        List<OpticalFormula> mine = all.stream().filter(f -> f.patientId().equals(patientId))
                .sorted(Comparator.comparing(OpticalFormula::createdAt).reversed()).toList();
        int from = Math.min(page.offset(), mine.size());
        int to = Math.min(from + page.limit(), mine.size());
        return new PageResult<>(mine.subList(from, to), page.page(), page.limit(), mine.size());
    }

    private static OpticalFormula superseded(OpticalFormula f) {
        return OpticalFormula.rehydrate(f.id(), f.patientId(), f.od(), f.oi(), f.pupillaryDistance(), f.lensType(),
                f.optometristName(), f.formulaDate(), false, f.createdAt());
    }
}
