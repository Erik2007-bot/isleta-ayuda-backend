package com.isletaayuda.backend.auth;

import com.isletaayuda.backend.auth.dto.LoginRequest;
import com.isletaayuda.backend.auth.dto.LoginResponse;
import com.isletaayuda.backend.auth.dto.PerfilUsuarioResponse;
import com.isletaayuda.backend.repository.UsuarioRepository;
import com.isletaayuda.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarios;

    public LoginService(AuthenticationManager authenticationManager, JwtService jwtService,
                        UsuarioRepository usuarios) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarios = usuarios;
    }

    public LoginResponse login(LoginRequest login) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
        return jwtService.emitir(authentication);
    }

    @Transactional(readOnly = true)
    public PerfilUsuarioResponse perfil(String subject) {
        var usuario = usuarios.findById(Integer.valueOf(subject))
                .orElseThrow(() -> new BadCredentialsException("Usuario no disponible"));
        return new PerfilUsuarioResponse(usuario.getIdUsuario(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRol());
    }
}
