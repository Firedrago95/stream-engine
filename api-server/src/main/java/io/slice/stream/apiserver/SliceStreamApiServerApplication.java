package io.slice.stream.apiserver;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@EnableResilientMethods
@SpringBootApplication
public class SliceStreamApiServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SliceStreamApiServerApplication.class, args);
	}
}