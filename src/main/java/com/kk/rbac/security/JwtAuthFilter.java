package com.kk.rbac.security;

import com.kk.rbac.service.JwtService;
import com.kk.rbac.service.PermissionService;
import com.kk.rbac.service.TokenRevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthEntryPoint authEntryPoint;
    private final TokenRevocationService tokenRevocationService;
    private final PermissionService permissionService;


    @Override
    protected void doFilterInternal(//仅对token不合法进行处理，不携带token或合法的token不在此处处理，由SpringSecurity的认证机制处理
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain
    ) throws ServletException, IOException {
        //从这次请求中，读取名为 Authorization 的请求头，即获取jwt
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
        //jwt符合格式
        //去掉bearer前缀
        String token = authorization.substring(bearerPrefix.length()).trim();
        Claims claims;
        int userId;
//判断token是否有效
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
        boolean revoked;
//        检查Token是否被撤销
        try {
            revoked = tokenRevocationService.isRevoked(claims.getId());
        } catch (DataAccessException | IllegalStateException exception) {
            logger.error("查询 Token 撤销状态失败", exception);
            SecurityContextHolder.clearContext();
            authEntryPoint.serviceUnavailable(response);
            return;
        }

        if (revoked) {//token已被撤销，认证已退出
            SecurityContextHolder.clearContext();
            authEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("登录凭证已撤销")
            );
            return;
        }
//        查询用户权限
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
//        将用户权限代码转换为Spring Security的SimpleGrantedAuthority对象，并添加到权限列表中
        try {
            List<String> permissionCodes =
                    permissionService.getPermissionCodes(userId);

            for (String code : permissionCodes) {
                authorities.add(new SimpleGrantedAuthority(code));
            }
        } catch (DataAccessException exception) {
            logger.error("查询用户权限失败", exception);
            SecurityContextHolder.clearContext();
            authEntryPoint.serviceUnavailable(response);
            return;
        }

//        创建一个登录身份对象，包含用户ID、JWT ID和过期时间
        LoginPrincipal principal = new LoginPrincipal(
                userId,
                claims.getId(),
                claims.getExpiration().toInstant()
        );

        // 创建一个已认证身份，将登录身份对象作为主体，权限列表为空
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );
//        创建用户上下文
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);

    }
}