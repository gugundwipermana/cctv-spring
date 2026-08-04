package com.homeserver.cctv.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Value("${cctv.api-key}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
        HttpServletResponse response, 
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestApiKey = request.getHeader("X-API-key");

        if (requestApiKey != null &&apiKey.equals(requestApiKey)) {
            // Tandai request sebagai valid dan lanjutkan ke filter berikutnya
            var auth = new UsernamePasswordAuthenticationToken("device", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
