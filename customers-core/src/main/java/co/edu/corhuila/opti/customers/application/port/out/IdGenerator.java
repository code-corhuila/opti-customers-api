package co.edu.corhuila.opti.customers.application.port.out;

import java.util.UUID;

/** Source of identifiers, so use cases stay deterministic under test. */
public interface IdGenerator {

    UUID next();
}
