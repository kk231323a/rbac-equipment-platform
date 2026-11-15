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
                        .requestMatchers(HttpMethod.GET,
                                "/api/borrow/borrowedList")//允许借用人查看自己的借用申请列表
                        .authenticated()
                        .requestMatchers(//需要equipment:read权限的get接口
                                   HttpMethod.GET,
                                "/api/equipment/**"
                        )
                        .hasAnyAuthority(PermissionConstants.EQUIPMENT_READ)
                        .requestMatchers(//需要拥有借用权限borrow:create权限的post接口
                                HttpMethod.POST,
                                "/api/borrow/request",
                                "/api/borrow/request/cancel")
                        .hasAuthority(PermissionConstants.BORROW_CREATE)
                        .requestMatchers(//需要拥有审批权限borrow:approve权限的post接口
                                HttpMethod.POST,
                                "/api/borrow/approve",
                                "/api/borrow/reject")
                        .hasAuthority(PermissionConstants.BORROW_APPROVED)
                        .requestMatchers(HttpMethod.GET, //需要拥有审批权限borrow:approve权限的get接口
                                "/api/borrow/pending")
                        .hasAnyAuthority(PermissionConstants.BORROW_APPROVED)
                        .requestMatchers(HttpMethod.POST,
                                "/api/borrow/return")
                        .hasAuthority(PermissionConstants.BORROW_RETURN)
                        .requestMatchers(HttpMethod.GET,//需要拥有归还权限borrow:return权限的get接口
                                "/api/borrow/received")
                        .hasAuthority(PermissionConstants.BORROW_RETURN)
                        .requestMatchers(HttpMethod.POST,
                                "/api/equipment/addNew")
                        .hasAnyAuthority(PermissionConstants.EQUIPMENT_CREATE)
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