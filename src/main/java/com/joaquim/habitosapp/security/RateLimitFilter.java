package com.joaquim.habitosapp.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket crearBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket getBucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> crearBucket());
    }

    /**
     * Obtiene la IP real del cliente. Detrás del proxy de Railway,
     * getRemoteAddr() devuelve la IP del proxy (igual para todos los
     * usuarios), así que leemos X-Forwarded-For. Cogemos la ÚLTIMA IP
     * de la lista: es la que añade el proxy de confianza y el cliente
     * no puede falsificarla (las primeras sí podrían venir inventadas).
     * En local no hay cabecera y se usa getRemoteAddr() como siempre.
     */
    private String obtenerIpCliente(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] ips = xForwardedFor.split(",");
        return ips[ips.length - 1].trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (uri.equals("/api/usuarios/login")
                || uri.equals("/api/usuarios/recuperar")
                || uri.equals("/api/usuarios/restablecer")) {
            String ip = obtenerIpCliente(request);
            Bucket bucket = getBucket(ip);

            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        "\"Demasiados intentos. Espera 1 minuto.\""
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}