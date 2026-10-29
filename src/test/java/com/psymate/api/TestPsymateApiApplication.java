package com.psymate.api;

import org.springframework.boot.SpringApplication;

public class TestPsymateApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PsymateApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
