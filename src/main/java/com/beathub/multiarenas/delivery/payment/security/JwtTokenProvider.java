package com.beathub.multiarenas.delivery.payment.security;

import com.beathub.multiarenas.delivery.payment.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validarToken(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (SignatureException ex) {
            log.error("Firma JWT no válida: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Token JWT malformado: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("Token JWT expirado: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Token JWT no soportado: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("Claims JWT vacíos o nulos: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("Error al validar token JWT: {}", ex.getMessage());
        }
        return false;
    }

    public Long extraerUsuarioId(String token) {
        return Long.parseLong(extraerClaims(token).getSubject());
    }

    public UserPrincipal extraerUserPrincipal(String token) {
        Claims claims = extraerClaims(token);
        return UserPrincipal.create(claims);
    }

    public String generarInternalServiceToken(Long usuarioId) {
        java.util.Date ahora = new java.util.Date();
        java.util.Date expiracion = new java.util.Date(ahora.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(usuarioId != null ? String.valueOf(usuarioId) : "1")
                .claim("usuarioId", usuarioId != null ? usuarioId : 1L)
                .claim("username", "system-payment-service")
                .claim("email", "payment-service@beathub.internal")
                .claim("roles", java.util.List.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN_ARENA"))
                .claim("scopes", java.util.List.of("api:access"))
                .issuer(jwtProperties.getIssuer() != null ? jwtProperties.getIssuer() : "beathub-auth-service")
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(getSigningKey())
                .compact();
    }
}
