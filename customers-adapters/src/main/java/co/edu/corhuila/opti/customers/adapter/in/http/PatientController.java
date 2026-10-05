package co.edu.corhuila.opti.customers.adapter.in.http;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.corhuila.opti.customers.adapter.in.http.PatientDtos.AddFormulaRequest;
import co.edu.corhuila.opti.customers.adapter.in.http.PatientDtos.FormulaResponse;
import co.edu.corhuila.opti.customers.adapter.in.http.PatientDtos.PatientResponse;
import co.edu.corhuila.opti.customers.adapter.in.http.PatientDtos.RegisterPatientRequest;
import co.edu.corhuila.opti.customers.adapter.in.http.PatientDtos.SummaryResponse;
import co.edu.corhuila.opti.customers.adapter.in.http.PatientDtos.UpdateContactRequest;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases.PatientFilter;
import co.edu.corhuila.opti.customers.domain.model.PatientStatus;

import jakarta.servlet.http.HttpServletRequest;

/** HTTP adapter of the patient use cases. Validation of shape and role here; rules in the core. */
@RestController
@RequestMapping("/api/v1/patients")
class PatientController {

    private static final String PATIENTS = "/api/v1/patients";

    private final PatientUseCases useCases;

    PatientController(PatientUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping
    ResponseEntity<Responses.CreatedBody> register(HttpServletRequest http,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterPatientRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER, Roles.OPTOMETRIST);
        var result = useCases.register(body.toData(), key);
        return Responses.created(result, result.value().id(), PATIENTS);
    }

    @GetMapping
    PageResponse<PatientResponse> search(HttpServletRequest http,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) String controlDueBefore,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "q", "status", "controlDueBefore", "page", "limit");
        var filter = new PatientFilter(q, parseStatus(status), parseDate(controlDueBefore, "controlDueBefore"));
        return PageResponse.of(useCases.search(filter, RequestRules.page(page, limit)).map(PatientResponse::from));
    }

    /** Totals for the patients dashboard (HU-17); same visibility as the listing above. */
    @GetMapping("/summary")
    SummaryResponse summary() {
        return SummaryResponse.from(useCases.summary());
    }

    @GetMapping("/{id}")
    PatientResponse get(@PathVariable String id) {
        return PatientResponse.from(useCases.get(RequestRules.uuid(id, "id")));
    }

    @PutMapping("/{id}/contact")
    PatientResponse updateContact(HttpServletRequest http, @PathVariable String id,
            @RequestBody UpdateContactRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER, Roles.OPTOMETRIST);
        return PatientResponse.from(useCases.updateContact(RequestRules.uuid(id, "id"), body.toData()));
    }

    /** Called by the worker when the last control is too old. Idempotent. */
    @PostMapping("/{id}/control-overdue")
    PatientResponse flagControlOverdue(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SERVICE);
        return PatientResponse.from(useCases.flagControlOverdue(RequestRules.uuid(id, "id")));
    }

    @PostMapping("/{id}/formulas")
    ResponseEntity<Responses.CreatedBody> addFormula(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody AddFormulaRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.OPTOMETRIST);
        UUID patientId = RequestRules.uuid(id, "id");
        var result = useCases.addFormula(patientId, body.toData(), key);
        return Responses.created(result, result.value().id(), PATIENTS + "/" + patientId + "/formulas");
    }

    @GetMapping("/{id}/formulas")
    PageResponse<FormulaResponse> formulas(HttpServletRequest http, @PathVariable String id,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "page", "limit");
        return PageResponse.of(useCases.formulas(RequestRules.uuid(id, "id"), RequestRules.page(page, limit))
                .map(FormulaResponse::from));
    }

    @GetMapping("/{id}/formulas/current")
    FormulaResponse currentFormula(@PathVariable String id) {
        return FormulaResponse.from(useCases.currentFormula(RequestRules.uuid(id, "id")));
    }

    private static PatientStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PatientStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status", "must be ACTIVE, CONTROL_OVERDUE or INACTIVE");
        }
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.validation(field, "must be a date (YYYY-MM-DD)");
        }
    }
}
