package co.edu.corhuila.opti.customers.adapter.in.http;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import co.edu.corhuila.opti.customers.domain.model.DocumentType;
import co.edu.corhuila.opti.customers.domain.model.EyeMeasure;
import co.edu.corhuila.opti.customers.domain.model.LensType;
import co.edu.corhuila.opti.customers.domain.model.OpticalFormula;
import co.edu.corhuila.opti.customers.domain.model.Patient;
import co.edu.corhuila.opti.customers.domain.model.PatientStatus;

/**
 * Request and response objects of the public contract. The domain entities are never serialized
 * directly, so renaming an internal field cannot break a client.
 */
final class PatientDtos {

    private PatientDtos() {
    }

    record RegisterPatientRequest(DocumentType documentType, String documentNumber, String firstName,
                                  String lastName, String phone, String email, String eps, String city,
                                  LocalDate birthDate) {

        Patient.RegisterData toData() {
            return new Patient.RegisterData(documentType, documentNumber, firstName, lastName, phone, email, eps,
                    city, birthDate);
        }
    }

    record UpdateContactRequest(String phone, String email, String eps, String city) {

        Patient.ContactData toData() {
            return new Patient.ContactData(phone, email, eps, city);
        }
    }

    record EyeDto(BigDecimal sphere, BigDecimal cylinder, Integer axis, BigDecimal addition) {

        EyeMeasure toRaw() {
            return new EyeMeasure(sphere, cylinder, axis, addition);
        }

        static EyeDto from(EyeMeasure eye) {
            return new EyeDto(eye.sphere(), eye.cylinder(), eye.axis(), eye.addition());
        }
    }

    record AddFormulaRequest(EyeDto od, EyeDto oi, BigDecimal pupillaryDistance, LensType lensType,
                             String optometristName, LocalDate formulaDate) {

        OpticalFormula.Data toData() {
            return new OpticalFormula.Data(od == null ? null : od.toRaw(), oi == null ? null : oi.toRaw(),
                    pupillaryDistance, lensType, optometristName, formulaDate);
        }
    }

    record PatientResponse(UUID id, DocumentType documentType, String documentNumber, String firstName,
                           String lastName, String fullName, String phone, String email, String eps, String city,
                           LocalDate birthDate, PatientStatus status, LocalDate lastControlDate, Instant createdAt) {

        static PatientResponse from(Patient p) {
            return new PatientResponse(p.id(), p.documentType(), p.documentNumber(), p.firstName(), p.lastName(),
                    p.fullName(), p.phone(), p.email(), p.eps(), p.city(), p.birthDate(), p.status(),
                    p.lastControlDate(), p.createdAt());
        }
    }

    record FormulaResponse(UUID id, UUID patientId, EyeDto od, EyeDto oi, BigDecimal pupillaryDistance,
                           LensType lensType, String optometristName, LocalDate formulaDate, boolean current,
                           Instant createdAt) {

        static FormulaResponse from(OpticalFormula f) {
            return new FormulaResponse(f.id(), f.patientId(), EyeDto.from(f.od()), EyeDto.from(f.oi()),
                    f.pupillaryDistance(), f.lensType(), f.optometristName(), f.formulaDate(), f.current(),
                    f.createdAt());
        }
    }
}
