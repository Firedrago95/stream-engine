package io.slice.stream.apiserver;


import io.slice.stream.apiserver.global.config.HighlightProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableConfigurationProperties(HighlightProperties.class)
@EnableAsync
@EnableResilientMethods
@EnableScheduling
@SpringBootApplication
public class SliceStreamApiServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SliceStreamApiServerApplication.class, args);
	}
}
