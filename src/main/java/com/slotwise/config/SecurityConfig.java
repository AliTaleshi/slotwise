package com.slotwise.config;

import com.slotwise.auth.security.JwtAuthenticationFilter;
import com.slotwise.auth.security.ProblemDetailSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtFilter, ProblemDetailSecurityHandlers handlers)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/v1/auth/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/v1/businesses/*/public", "/v1/businesses/*/availability")
                    .permitAll()
                    // booking access is decided per booking in the service (account or manage
                    // token)
                    .requestMatchers(HttpMethod.POST, "/v1/holds", "/v1/bookings")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/bookings/*")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST, "/v1/bookings/*/reschedule", "/v1/bookings/*/cancel")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
