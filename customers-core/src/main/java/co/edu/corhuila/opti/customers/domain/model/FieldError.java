package co.edu.corhuila.opti.customers.domain.model;

/** One invalid input field and the reason. */
public record FieldError(String field, String message) {
}
