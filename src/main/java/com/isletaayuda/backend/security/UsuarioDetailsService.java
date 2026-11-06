package com.isletaayuda.backend.security;

import com.isletaayuda.backend.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var usuario = usuarios.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas"));
        return User.withUsername(usuario.getIdUsuario().toString())
                .password(usuario.getPasswordHash())
                .roles(usuario.getRol().name())
                .build();
    }
}
