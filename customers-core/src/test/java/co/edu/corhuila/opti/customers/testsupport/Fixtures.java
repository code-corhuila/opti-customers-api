package co.edu.corhuila.opti.customers.testsupport;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases;
import co.edu.corhuila.opti.customers.application.usecase.PatientService;
import co.edu.corhuila.opti.customers.domain.model.DocumentType;
import co.edu.corhuila.opti.customers.domain.model.EyeMeasure;
import co.edu.corhuila.opti.customers.domain.model.LensType;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;
import co.edu.corhuila.opti.customers.domain.model.Patient;

/** Ready-made valid inputs and a fully wired service over the fakes. */
public final class Fixtures {

    public static final String START = "2026-09-29T15:00:00Z";

    private Fixtures() {
    }

    public static PatientUseCases service(TestClock clock) {
        return new PatientService(new InMemoryPatientRepository(), new InMemoryFormulaRepository(),
                new SequentialIds(), new DirectUnitOfWork(), clock);
    }

    public static Patient.RegisterData validPatient() {
        return patient("1075243890");
    }

    public static Patient.RegisterData patient(String document) {
        return new Patient.RegisterData(DocumentType.CC, document, "Laura Marcela", "Ortega Ruiz", "3104582291",
                "laura.ortega@gmail.com", "Sanitas", "Neiva", LocalDate.of(1990, 5, 20));
    }

    public static OpticalFormula.Data validFormula() {
        return new OpticalFormula.Data(
                new EyeMeasure(new BigDecimal("-1.50"), new BigDecimal("-0.75"), 90, null),
                new EyeMeasure(new BigDecimal("-1.25"), null, null, null),
                new BigDecimal("62.5"), LensType.MONOFOCAL, "Dra. Ana Torres", LocalDate.of(2026, 9, 20));
    }
}
