package br.com.api.footfirma;

import org.springframework.boot.SpringApplication;

public class TestFootfirmaApplication {

    public static void main(String[] args) {
        SpringApplication.from(FootfirmaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
