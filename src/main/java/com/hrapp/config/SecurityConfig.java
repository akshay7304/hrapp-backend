package com.hrapp.config;

import com.hrapp.security.JwtAccessDeniedHandler;
import com.hrapp.security.JwtAuthenticationEntryPoint;
import com.hrapp.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the HR app.
 * <p>
 * Stateless JWT-based auth: every request is authenticated by
 * {@link JwtAuthenticationFilter} which runs before the standard
 * {@link UsernamePasswordAuthenticationFilter}.
 * <p>
 * Path matchers below are relative to {@code server.servlet.context-path=/api/v1},
 * so {@code /auth/**} here corresponds to {@code /api/v1/auth/**} from the client's
 * point of view.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Role hierarchy so higher-privileged roles automatically satisfy
     * lower-privileged role checks without having to list every role in
     * every {@code @PreAuthorize}. Read as "X implies Y" — a holder of X
     * passes any check that requires Y.
     * <ul>
     *   <li>SUPERADMIN → ADMIN, HR, EMPLOYEE</li>
     *   <li>ADMIN → HR, EMPLOYEE</li>
     *   <li>HR → EMPLOYEE</li>
     * </ul>
     * The {@code withDefaultRolePrefix()} builder prepends {@code ROLE_} so
     * the entries match the authorities granted by
     * {@code CustomUserDetailsService}.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPERADMIN").implies("ADMIN", "HR", "EMPLOYEE")
                .role("ADMIN").implies("HR", "EMPLOYEE")
                .role("HR").implies("EMPLOYEE")
                .build();
    }

    /**
     * Wires the {@link RoleHierarchy} into the {@code @PreAuthorize} /
     * {@code @PostAuthorize} expression evaluator. Without this, the
     * hierarchy is only consulted by URL-rule checks
     * ({@code .hasRole(...)} in the DSL).
     *
     * <p>Note: the old {@code RoleHierarchyVoter} no longer exists — voters
     * were removed in Spring Security 6.0. For URL-level rules, exposing a
     * {@link RoleHierarchy} bean is sufficient; the modern
     * {@code AuthorityAuthorizationManager} picks it up automatically.
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                // Biometric devices push punches with a shared secret in
                                // the body — no JWT is possible from a fingerprint reader.
                                "/thumb/zkteco",
                                "/thumb/essl",
                                "/thumb/generic"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
