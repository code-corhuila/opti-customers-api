package co.edu.corhuila.opti.customers.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Patient aggregate root. Pure domain object: no framework, no persistence.
 * A patient is unique by (document type, document number).
 */
public final class Patient {

    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");
    /** Letters (including accents and Ñ) and spaces only: no digits, no punctuation. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L} ]{2,100}$");
    /** The EPS a patient can be registered under; a short, explicit catalog, not free text. */
    private static final List<String> EPS_OPTIONS = List.of("Nueva EPS", "EPS Sanitas", "Pijaos Salud EPSI");
    private static final int MAX_AGE_YEARS = 120;

    private final UUID id;
    private final DocumentType documentType;
    private final String documentNumber;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final String email;
    private final String eps;
    private final String city;
    private final LocalDate birthDate;
    private final PatientStatus status;
    private final LocalDate lastControlDate;
    private final Instant createdAt;

    private Patient(UUID id, DocumentType documentType, String documentNumber, String firstName, String lastName,
                    String phone, String email, String eps, String city, LocalDate birthDate,
                    PatientStatus status, LocalDate lastControlDate, Instant createdAt) {
        this.id = id;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.email = email;
        this.eps = eps;
        this.city = city;
        this.birthDate = birthDate;
        this.status = status;
        this.lastControlDate = lastControlDate;
        this.createdAt = createdAt;
    }

    /** Registers a new patient; every rule reports the field that broke it. */
    public static Patient register(UUID id, RegisterData data, LocalDate today, Instant now) {
        Violations v = new Violations();
        DocumentType type = v.check(() -> Validation.required(data.documentType(), "documentType"));
        String number = v.check(() -> Validation.matching(data.documentNumber(), "documentNumber",
                DOCUMENT_NUMBER, "must have 5 to 20 letters or digits"));
        String first = v.check(() -> Validation.matching(data.firstName(), "firstName", NAME,
                "must have 2 to 100 letters, no numbers or special characters"));
        String last = v.check(() -> Validation.matching(data.lastName(), "lastName", NAME,
                "must have 2 to 100 letters, no numbers or special characters"));
        Contact contact = contact(v, data.phone(), data.email(), data.eps(), data.city());
        LocalDate birth = v.check(() -> validBirthDate(data.birthDate(), today));
        v.throwIfAny();
        return new Patient(id, type, number, first, last, contact.phone(), contact.email(), contact.eps(),
                contact.city(), birth, PatientStatus.ACTIVE, today, now);
    }

    private record Contact(String phone, String email, String eps, String city) {
    }

    private static Contact contact(Violations v, String phone, String email, String eps, String city) {
        return new Contact(
                v.check(() -> Validation.matching(phone, "phone", PHONE,
                        "must have 7 to 15 digits, optionally starting with +")),
                v.check(() -> Validation.optionalEmail(email, "email")),
                v.check(() -> Validation.oneOf(eps, "eps", EPS_OPTIONS)),
                v.check(() -> Validation.optionalMatching(city, "city", NAME,
                        "must have only letters and spaces")));
    }

    public static Patient rehydrate(UUID id, DocumentType documentType, String documentNumber, String firstName,
                                    String lastName, String phone, String email, String eps, String city,
                                    LocalDate birthDate, PatientStatus status, LocalDate lastControlDate,
                                    Instant createdAt) {
        return new Patient(id, documentType, documentNumber, firstName, lastName, phone, email, eps, city,
                birthDate, status, lastControlDate, createdAt);
    }

    /** Updates the contact data; identity (document) never changes. */
    public Patient updateContact(ContactData data) {
        Violations v = new Violations();
        Contact contact = contact(v, data.phone(), data.email(), data.eps(), data.city());
        v.throwIfAny();
        return new Patient(id, documentType, documentNumber, firstName, lastName, contact.phone(), contact.email(),
                contact.eps(), contact.city(), birthDate, status, lastControlDate, createdAt);
    }

    /** A patient whose last control is too old is flagged; only an active patient can be flagged. */
    public Patient flagControlOverdue() {
        if (status == PatientStatus.CONTROL_OVERDUE) {
            return this;
        }
        if (status != PatientStatus.ACTIVE) {
            throw DomainException.rule("only an active patient can be flagged with an overdue control");
        }
        return withStatus(PatientStatus.CONTROL_OVERDUE, lastControlDate);
    }

    /** A new formula is a control: the patient is up to date again. */
    public Patient controlDone(LocalDate date) {
        PatientStatus next = status == PatientStatus.INACTIVE ? status : PatientStatus.ACTIVE;
        return withStatus(next, date);
    }

    public Patient deactivate() {
        return withStatus(PatientStatus.INACTIVE, lastControlDate);
    }

    public Patient activate() {
        return withStatus(PatientStatus.ACTIVE, lastControlDate);
    }

    private Patient withStatus(PatientStatus next, LocalDate control) {
        return new Patient(id, documentType, documentNumber, firstName, lastName, phone, email, eps, city,
                birthDate, next, control, createdAt);
    }

    private static LocalDate validBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) {
            return null;
        }
        Validation.pastOrToday(birthDate, "birthDate", today);
        if (birthDate.isBefore(today.minusYears(MAX_AGE_YEARS))) {
            throw DomainException.validation("birthDate", "is not a plausible date");
        }
        return birthDate;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public UUID id() {
        return id;
    }

    public DocumentType documentType() {
        return documentType;
    }

    public String documentNumber() {
        return documentNumber;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    public String eps() {
        return eps;
    }

    public String city() {
        return city;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public PatientStatus status() {
        return status;
    }

    public LocalDate lastControlDate() {
        return lastControlDate;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Raw input to register a patient, before validation. */
    public record RegisterData(DocumentType documentType, String documentNumber, String firstName, String lastName,
                               String phone, String email, String eps, String city, LocalDate birthDate) {
    }

    /** Raw input to update the contact data of a patient, before validation. */
    public record ContactData(String phone, String email, String eps, String city) {
    }
}
