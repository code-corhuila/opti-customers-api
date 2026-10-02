package co.edu.corhuila.opti.customers.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the customers service. */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.opti.customers")
public class CustomersApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomersApplication.class, args);
    }
}
