package com.kk.rbac.config;


import com.kk.rbac.common.constants.PermissionConstants;
import com.kk.rbac.security.AuthDeniedHandler;
import com.kk.rbac.security.AuthEntryPoint;
import com.kk.rbac.security.JwtAuthFilter;
import com.kk.rbac.service.JwtService;
import com.kk.rbac.service.PermissionService;
import com.kk.rbac.service.TokenRevocationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthEntryPoint authEntryPoint,
            JwtService jwtService,
            TokenRevocationService tokenRevocationService,
            PermissionService permissionService,
            AuthDeniedHandler authDeniedHandler
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(//允许匿名访问的接口
                                "/api/auth/login",
                                "/api/health"
                        ).permitAll()
                        .requestMatchers(//需要equipment:read权限的接口
                                   HttpMethod.GET,
                                "/api/equipment/**"
                        )
                        .hasAnyAuthority(PermissionConstants.EQUIPMENT_READ)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(authDeniedHandler)
                )
                .addFilterBefore(
                        new JwtAuthFilter(
                                jwtService,
                                authEntryPoint,
                                tokenRevocationService,
                                permissionService
                        ),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }
}