package io.slice.stream.apiserver.global.config;

import io.slice.stream.apiserver.admin.auth.AdminAuthFilter;
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

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminAuthFilterRegistration(AdminAuthFilter filter) {
        FilterRegistrationBean<AdminAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/v1/admin/*", "/api/v1/admin/*/*", "/api/v1/admin/*/*/*");
        registration.setOrder(2);
        return registration;
    }
}
