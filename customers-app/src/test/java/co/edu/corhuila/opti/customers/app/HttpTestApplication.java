package co.edu.corhuila.opti.customers.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.customers.adapter.in.http.PublicPaths;
import co.edu.corhuila.opti.customers.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.opti.customers.application.port.in.PatientUseCases;
import co.edu.corhuila.opti.customers.testsupport.Fixtures;
import co.edu.corhuila.opti.customers.testsupport.TestClock;

/**
 * Boots only the HTTP adapter over the in-memory fakes: no database, same filters, same
 * error handling, same controllers as production.
 */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.opti.customers.adapter.in.http",
        exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
class HttpTestApplication {

    @Bean
    TestClock clock() {
        return TestClock.at(Fixtures.START);
    }

    @Bean
    Rs256Verifier verifier(ObjectMapper json, TestClock clock) {
        return new Rs256Verifier(TestTokens.publicKeyPem(), json, clock);
    }

    @Bean
    PublicPaths publicPaths() {
        return PublicPaths.with();
    }

    @Bean
    PatientUseCases useCases(TestClock clock) {
        return Fixtures.service(clock);
    }
}
