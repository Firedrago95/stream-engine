package io.slice.stream.apiserver.global.config;

import io.slice.stream.apiserver.global.security.EngineTokenFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<EngineTokenFilter> engineTokenFilterRegistration(EngineTokenFilter filter) {
        FilterRegistrationBean<EngineTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
