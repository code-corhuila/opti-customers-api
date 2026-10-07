package co.edu.corhuila.opti.customers.adapter.out.persistence;

import java.util.UUID;

import co.edu.corhuila.opti.customers.application.port.out.IdGenerator;

/** Random (v4) identifiers. */
public class UuidGenerator implements IdGenerator {

    @Override
    public UUID next() {
        return UUID.randomUUID();
    }
}
