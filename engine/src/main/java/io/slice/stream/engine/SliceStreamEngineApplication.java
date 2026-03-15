package io.slice.stream.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableResilientMethods
@EnableScheduling
@SpringBootApplication
public class SliceStreamEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(SliceStreamEngineApplication.class, args);
	}
}
