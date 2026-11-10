package com.kk.rbac.security;

import com.kk.rbac.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthEntryPoint authEntryPoint;
    @Override
    protected void doFilterInternal(//仅对token不合法进行处理，不携带token或合法的token不做任何处理
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        //从这次请求中，读取名为 Authorization 的请求头
        String authorization = request.getHeader("Authorization");
        String bearerPrefix = "Bearer ";
        //判断Authorization请求头是否存在，且是否以Bearer开头
        if (authorization == null
                || !authorization.regionMatches(
                true, 0, bearerPrefix, 0, bearerPrefix.length()
        )) {
            filterChain.doFilter(request, response);
            return;
        }
        //如果jwt符合格式
        //去掉bearer前缀
        String token = authorization.substring(bearerPrefix.length()).trim();
        Claims claims;
        int  userId;

        try {
            //解析token，获取claims
            claims = jwtService.parseToken(token);
            userId = Integer.parseInt(claims.getSubject());
            if (userId <= 0
                    || claims.getId() == null
                    || claims.getId().isBlank()
                    || claims.getExpiration() == null) {
                throw new IllegalArgumentException("登录凭证缺少有效的必要声明");
            }
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            authEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("登录凭证无效", exception)
            );
            return;
        }
        //创建一个已认证身份
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                );
//        创建用户上下文
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);

    }
}