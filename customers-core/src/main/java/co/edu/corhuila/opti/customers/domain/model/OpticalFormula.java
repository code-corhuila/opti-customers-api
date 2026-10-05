package co.edu.corhuila.opti.customers.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An optical prescription of a patient. Only one formula per patient is current; registering a
 * new one supersedes the previous (that rule is applied by the use case inside one transaction).
 */
public final class OpticalFormula {

    private static final BigDecimal MIN_PD = new BigDecimal("40");
    private static final BigDecimal MAX_PD = new BigDecimal("80");
    /** Letters (including accents and Ñ) and spaces only: no digits, no punctuation. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L} ]{3,150}$");

    private final UUID id;
    private final UUID patientId;
    private final EyeMeasure od;
    private final EyeMeasure oi;
    private final BigDecimal pupillaryDistance;
    private final LensType lensType;
    private final String optometristName;
    private final LocalDate formulaDate;
    private final boolean current;
    private final Instant createdAt;

    private OpticalFormula(UUID id, UUID patientId, EyeMeasure od, EyeMeasure oi, BigDecimal pupillaryDistance,
                           LensType lensType, String optometristName, LocalDate formulaDate, boolean current,
                           Instant createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.od = od;
        this.oi = oi;
        this.pupillaryDistance = pupillaryDistance;
        this.lensType = lensType;
        this.optometristName = optometristName;
        this.formulaDate = formulaDate;
        this.current = current;
        this.createdAt = createdAt;
    }

    public static OpticalFormula register(UUID id, UUID patientId, Data data, LocalDate today, Instant now) {
        Violations v = new Violations();
        EyeMeasure od = v.check(() -> eye("od", data.od()));
        EyeMeasure oi = v.check(() -> eye("oi", data.oi()));
        BigDecimal pd = v.check(() -> pupillaryDistance(data.pupillaryDistance()));
        LensType lensType = v.check(() -> Validation.required(data.lensType(), "lensType"));
        String optometrist = v.check(() -> Validation.matching(data.optometristName(), "optometristName", NAME,
                "must have 3 to 150 letters, no numbers or special characters"));
        LocalDate date = v.check(() -> Validation.pastOrToday(
                Validation.required(data.formulaDate(), "formulaDate"), "formulaDate", today));
        v.throwIfAny();
        return new OpticalFormula(id, patientId, od, oi, pd, lensType, optometrist, date, true, now);
    }

    private static EyeMeasure eye(String prefix, EyeMeasure raw) {
        Validation.required(raw, prefix);
        return EyeMeasure.of(prefix, raw.sphere(), raw.cylinder(), raw.axis(), raw.addition());
    }

    private static BigDecimal pupillaryDistance(BigDecimal value) {
        BigDecimal pd = Validation.required(value, "pupillaryDistance");
        if (pd.compareTo(MIN_PD) < 0 || pd.compareTo(MAX_PD) > 0) {
            throw DomainException.validation("pupillaryDistance", "must be between 40 and 80 mm");
        }
        return pd;
    }

    public static OpticalFormula rehydrate(UUID id, UUID patientId, EyeMeasure od, EyeMeasure oi,
                                           BigDecimal pupillaryDistance, LensType lensType, String optometristName,
                                           LocalDate formulaDate, boolean current, Instant createdAt) {
        return new OpticalFormula(id, patientId, od, oi, pupillaryDistance, lensType, optometristName, formulaDate,
                current, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public EyeMeasure od() {
        return od;
    }

    public EyeMeasure oi() {
        return oi;
    }

    public BigDecimal pupillaryDistance() {
        return pupillaryDistance;
    }

    public LensType lensType() {
        return lensType;
    }

    public String optometristName() {
        return optometristName;
    }

    public LocalDate formulaDate() {
        return formulaDate;
    }

    public boolean current() {
        return current;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Raw input, already shaped per eye, before the formula-level rules run. */
    public record Data(EyeMeasure od, EyeMeasure oi, BigDecimal pupillaryDistance, LensType lensType,
                       String optometristName, LocalDate formulaDate) {
    }
}
