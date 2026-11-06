package com.isletaayuda.backend.security;

import com.isletaayuda.backend.auth.dto.LoginResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JwtService {

    public static final String ISSUER = "isleta-ayuda";
    public static final String AUDIENCE = "isleta-ayuda-api";
    private static final Duration DURACION = Duration.ofMinutes(15);

    private final JwtEncoder encoder;

    public JwtService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public LoginResponse emitir(Authentication authentication) {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant caducidad = ahora.plus(DURACION);
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(authentication.getName())
                .issuedAt(ahora)
                .expiresAt(caducidad)
                .claim("roles", authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", DURACION.toSeconds(), caducidad);
    }
}
