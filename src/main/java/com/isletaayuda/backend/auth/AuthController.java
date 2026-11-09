package com.isletaayuda.backend.auth;

import com.isletaayuda.backend.auth.dto.LoginRequest;
import com.isletaayuda.backend.auth.dto.LoginResponse;
import com.isletaayuda.backend.auth.dto.PerfilUsuarioResponse;
import com.isletaayuda.backend.auth.dto.RegistroRequest;
import com.isletaayuda.backend.auth.dto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginService loginService;

    public AuthController(AuthService authService, LoginService loginService) {
        this.authService = authService;
        this.loginService = loginService;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest registro) {
        return authService.registrar(registro);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest login) {
        return loginService.login(login);
    }

    @GetMapping("/me")
    public PerfilUsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return loginService.perfil(jwt.getSubject());
    }
}
