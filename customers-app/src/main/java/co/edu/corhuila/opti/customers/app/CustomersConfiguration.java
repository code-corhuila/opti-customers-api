package co.edu.corhuila.opti.customers.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.customers.adapter.in.http.PublicPaths;
import co.edu.corhuila.opti.customers.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.opti.customers.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.opti.customers.adapter.out.persistence.JdbcFormulaRepository;
import co.edu.corhuila.opti.customers.adapter.out.persistence.JdbcPatientRepository;
import co.edu.corhuila.opti.customers.adapter.out.persistence.JdbcUnitOfWork;
import co.edu.corhuila.opti.customers.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases;
import co.edu.corhuila.opti.customers.application.port.out.FormulaRepository;
import co.edu.corhuila.opti.customers.application.port.out.IdGenerator;
import co.edu.corhuila.opti.customers.application.port.out.PatientRepository;
import co.edu.corhuila.opti.customers.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.customers.application.usecase.PatientService;

/**
 * Composition root: the only place that knows every concrete type. The numeric limits (server
 * timeouts, pool size, statement timeout, graceful shutdown) are declared with their value in
 * {@code application.yml}, next to this class.
 */
@Configuration
class CustomersConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Rs256Verifier tokenVerifier(ObjectMapper json, Clock clock,
                                @Value("${jwt.public-key:}") String publicKey,
                                @Value("${jwt.public-key-file:}") String publicKeyFile) throws IOException {
        String pem = publicKey.isBlank() && !publicKeyFile.isBlank()
                ? Files.readString(Path.of(publicKeyFile)) : publicKey;
        if (pem.isBlank()) {
            throw new IllegalStateException("Set JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE (identity service public key)");
        }
        return new Rs256Verifier(pem, json, clock);
    }

    @Bean
    PublicPaths publicPaths() {
        return PublicPaths.with();
    }

    @Bean
    IdempotencyKeys idempotencyKeys(JdbcClient jdbc) {
        return new IdempotencyKeys(jdbc);
    }

    @Bean
    UnitOfWork unitOfWork(TransactionTemplate transaction) {
        return new JdbcUnitOfWork(transaction);
    }

    @Bean
    IdGenerator idGenerator() {
        return new UuidGenerator();
    }

    @Bean
    PatientRepository patientRepository(JdbcClient jdbc, TransactionTemplate tx, IdempotencyKeys keys) {
        return new JdbcPatientRepository(jdbc, tx, keys);
    }

    @Bean
    FormulaRepository formulaRepository(JdbcClient jdbc, TransactionTemplate tx, IdempotencyKeys keys) {
        return new JdbcFormulaRepository(jdbc, tx, keys);
    }

    @Bean
    PatientUseCases patientUseCases(PatientRepository patients, FormulaRepository formulas, IdGenerator ids,
                                    UnitOfWork unitOfWork, Clock clock) {
        return new PatientService(patients, formulas, ids, unitOfWork, clock);
    }
}
