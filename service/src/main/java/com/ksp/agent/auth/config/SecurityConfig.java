package com.ksp.agent.auth.config;

import com.ksp.agent.auth.web.CorsProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, CorsProperties corsProperties) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // Catalyst Cron endpoints: no user JWT; authenticated by a shared secret
                        // header inside InternalCronController.
                        .requestMatchers("/api/v1/internal/cron/**").permitAll()
                        .requestMatchers("/", "/health", "/api/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS source consumed by Spring Security's CorsFilter (registered via .cors()).
     * Without this bean, 401 responses produced by JwtAuthFilter would carry no CORS
     * headers and the browser would block the response from reaching the SPA's fetch
     * handler — preventing the auto re-auth redirect on token expiry.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // All /api/** — restrict to configured SPA origins.
        // Always use origin patterns (not setAllowedOrigins) so a single AppSail
        // env value binds cleanly and Authorization-bearing SSE preflights succeed.
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(corsProperties.getAllowedOrigins());
        cfg.setAllowedMethods(corsProperties.getAllowedMethods());
        cfg.setAllowedHeaders(corsProperties.getAllowedHeaders());
        cfg.setExposedHeaders(corsProperties.getExposedHeaders());
        cfg.setAllowCredentials(corsProperties.isAllowCredentials());
        cfg.setMaxAge(corsProperties.getMaxAge());
        source.registerCorsConfiguration(corsProperties.getPathPattern(), cfg);

        return source;
    }
}
