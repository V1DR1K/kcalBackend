package com.scalegrams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScaleGramsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScaleGramsApplication.class, args);
	}

}
