package com.ksp.agent.auth.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/login",
            "/health"
    );

    /** SSE chat stream — only path that may authenticate via {@code access_token} query. */
    private static final String CHAT_STREAM_PATH = "/api/chat/stream";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS preflight must not be touched — no Authorization header on OPTIONS.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.parseClaims(token);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String upn = claims.getSubject();
                var authorities = new ArrayList<SimpleGrantedAuthority>();
                Object rawRoles = claims.get("roles");
                if (rawRoles instanceof List<?> roleList) {
                    for (Object r : roleList) {
                        if (r != null) {
                            authorities.add(new SimpleGrantedAuthority(
                                    "ROLE_" + r.toString().trim().toUpperCase()));
                        }
                    }
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(upn, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Bearer header everywhere; {@code access_token} query only on {@code /api/chat/stream}.
     * Cross-origin SSE cannot send {@code Authorization} without a CORS preflight, and Catalyst's
     * edge answers OPTIONS with no {@code Access-Control-*} headers — so the browser never reaches
     * Spring. Query auth keeps the stream request "simple" and avoids that preflight.
     */
    private static String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String bearer = authHeader.substring(7).trim();
            if (!bearer.isEmpty()) {
                return bearer;
            }
        }
        if (CHAT_STREAM_PATH.equals(request.getRequestURI())) {
            String queryToken = request.getParameter("access_token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }
        return null;
    }
}
