package com.hrapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HrappBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HrappBackendApplication.class, args);
	}

}
