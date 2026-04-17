package com.uscdip.backend.config;

import com.uscdip.backend.model.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestTraceId = request.getHeader(TraceIdContext.HEADER_NAME);
        String traceId = TraceIdContext.ensureTraceId(requestTraceId);
        response.setHeader(TraceIdContext.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdContext.clear();
        }
    }
}
