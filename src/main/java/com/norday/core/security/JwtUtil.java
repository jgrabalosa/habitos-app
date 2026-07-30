package com.norday.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String SECRET;

    @Value("${jwt.expiration}")
    private long EXPIRATION;

    /** Claim con la clave primaria del usuario: es lo que se usa para autorizar. */
    public static final String CLAIM_USUARIO_ID = "usuarioId";

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    /**
     * El email sigue siendo el subject (útil en logs y compatible con lo que
     * ya había), pero el id viaja como claim aparte porque el email es
     * editable y el id no.
     */
    public String generateToken(int usuarioId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_USUARIO_ID, usuarioId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(getKey())
                .compact();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Devuelve null si el token no trae el claim — caso de los tokens emitidos
     * antes de este cambio. Se tratan como no autenticados a propósito: hay
     * que volver a iniciar sesión.
     */
    public Integer extractUsuarioId(String token) {
        Object valor = getClaims(token).get(CLAIM_USUARIO_ID);
        return valor instanceof Number numero ? numero.intValue() : null;
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}