package com.julian.iagente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class IAgenteApplication {

	public static void main(String[] args) {
		SpringApplication.run(IAgenteApplication.class, args);
	}

}
