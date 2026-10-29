package adliya.uz.functioncatalogservice.config;

import adliya.uz.functioncatalogservice.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Completion of an already authorized server-sent event stream (support chat) is an async dispatch.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/functions/translation-capabilities", "/api/functions/admin", "/api/functions/*/admin", "/api/functions/pending-review", "/api/functions/*/audit", "/api/functions/*/review",
                                "/api/functions/assignments", "/api/functions/*/assignee/candidates").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/functions/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reports", "/api/analytics/events", "/api/feedback").permitAll()
                        // Support chat for visitors: identity comes from the HttpOnly guest cookie or the signed-in account,
                        // checked in SupportIdentityService; cross-site requests are refused by ChatOriginGuard.
                        .requestMatchers("/api/help/options", "/api/help/support", "/api/help/support/**").permitAll()
                        // Appointments: organization calendars and bookings for staff; booking and managing one's own
                        // booking (reference + secret token header, checked in AppointmentService) for visitors.
                        .requestMatchers("/api/appointments/staff", "/api/appointments/staff/**", "/api/appointments/schedules/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/appointments/services/*/availability", "/api/appointments/*",
                                "/api/appointments/*/availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/appointments", "/api/appointments/*/cancel", "/api/appointments/*/reschedule").permitAll()
                        .requestMatchers("/swagger", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
