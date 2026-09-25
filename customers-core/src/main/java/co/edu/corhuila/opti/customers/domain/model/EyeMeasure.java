package co.edu.corhuila.opti.customers.domain.model;

import java.math.BigDecimal;

/**
 * Measures of one eye (OD or OI). Values are in diopters, multiples of 0.25; the axis is in degrees.
 * The axis is only meaningful, and then mandatory, when there is a cylinder.
 */
public record EyeMeasure(BigDecimal sphere, BigDecimal cylinder, Integer axis, BigDecimal addition) {

    private static final BigDecimal QUARTER = new BigDecimal("0.25");

    /** Validates one eye; {@code prefix} is "od" or "oi" so each error names the exact field. */
    public static EyeMeasure of(String prefix, BigDecimal sphere, BigDecimal cylinder, Integer axis,
                                BigDecimal addition) {
        Violations v = new Violations();
        BigDecimal sph = v.check(() -> diopters(sphere, prefix + ".sphere", -20, 20));
        BigDecimal cyl = v.check(() -> diopters(cylinder, prefix + ".cylinder", -10, 10));
        BigDecimal add = v.check(() -> diopters(addition, prefix + ".addition", 0, 4));
        v.check(() -> checkAxis(prefix, cyl, axis));
        v.throwIfAny();
        return new EyeMeasure(sph, cyl, axis, add);
    }

    private static Integer checkAxis(String prefix, BigDecimal cylinder, Integer axis) {
        boolean hasCylinder = cylinder != null && cylinder.signum() != 0;
        if (hasCylinder && axis == null) {
            throw DomainException.validation(prefix + ".axis", "is required when there is a cylinder");
        }
        if (axis != null) {
            Validation.intBetween(axis, prefix + ".axis", 0, 180);
        }
        return axis;
    }

    private static BigDecimal diopters(BigDecimal value, String field, int min, int max) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw DomainException.validation(field, "must be between " + min + " and " + max);
        }
        if (value.remainder(QUARTER).signum() != 0) {
            throw DomainException.validation(field, "must be a multiple of 0.25");
        }
        return value;
    }
}
