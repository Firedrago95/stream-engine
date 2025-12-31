package io.slice.stream.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@EnableResilientMethods
@SpringBootApplication
public class SliceStreamEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(SliceStreamEngineApplication.class, args);
	}

}
