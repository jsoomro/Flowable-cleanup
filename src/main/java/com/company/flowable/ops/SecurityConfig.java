package com.company.flowable.ops;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
            .antMatchers("/css/**", "/js/**").permitAll()
            .antMatchers("/ops/**", "/api/ops/**").authenticated()
            .anyRequest().authenticated()
            .and()
            .httpBasic()
            .and()
            .formLogin();
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(OpsSecurityProperties props) {
        validatePasswords(props);
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername(props.getAdminUsername())
            .password(props.getAdminPassword())
            .roles("FLOWABLE_OPS_ADMIN")
            .build());
        manager.createUser(User.withUsername(props.getViewerUsername())
            .password(props.getViewerPassword())
            .roles("FLOWABLE_OPS_VIEWER")
            .build());
        return manager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private void validatePasswords(OpsSecurityProperties props) {
        validatePassword("ops.security.adminPassword", props.getAdminPassword());
        validatePassword("ops.security.viewerPassword", props.getViewerPassword());
    }

    private void validatePassword(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(key + " must be set to a bcrypt hash");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{noop}")) {
            throw new IllegalStateException(key + " must not use {noop} (plain text) encoding");
        }
        boolean bcryptPrefix = trimmed.startsWith("{bcrypt}");
        boolean bcryptHash = trimmed.startsWith("$2a$") || trimmed.startsWith("$2b$") || trimmed.startsWith("$2y$");
        if (!bcryptPrefix && !bcryptHash) {
            throw new IllegalStateException(key + " must be a bcrypt hash (e.g., {bcrypt}... or $2b$...)");
        }
    }
}
