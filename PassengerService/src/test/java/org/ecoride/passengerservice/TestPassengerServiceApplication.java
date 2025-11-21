package org.ecoride.passengerservice;

import org.springframework.boot.SpringApplication;

public class TestPassengerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(PassengerServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
