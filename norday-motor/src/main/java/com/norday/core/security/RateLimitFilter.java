package com.norday.core.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Antes era un ConcurrentHashMap sin expiración: cada IP que tocaba estas
    // rutas dejaba un Bucket residente para siempre, y un escaneo automatizado
    // (los hay constantemente contra cualquier IP pública) hacía crecer el
    // mapa sin techo hasta el OutOfMemoryError. Caffeine limpia solo las
    // entradas que llevan un rato sin usarse.
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    private Bucket crearBucket() {
        // Subido de 5 a 10/min: 5 era agresivo con CGNAT, donde varios
        // usuarios de la misma operadora móvil comparten IP pública.
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket getBucket(String ip) {
        return buckets.get(ip, k -> crearBucket());
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
                || uri.equals("/api/usuarios/restablecer")
                || uri.equals("/api/usuarios/registro")
                // La exportación lleva {id} en medio de la ruta, así que equals
                // nunca casaría: endsWith es la única forma de reconocerla.
                || uri.endsWith("/exportar")) {
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
