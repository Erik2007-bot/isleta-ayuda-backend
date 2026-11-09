package com.isletaayuda.backend.auth;

import com.isletaayuda.backend.auth.dto.RegistroRequest;
import com.isletaayuda.backend.auth.dto.UsuarioResponse;
import com.isletaayuda.backend.model.Rol;
import com.isletaayuda.backend.model.Usuario;
import com.isletaayuda.backend.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponse registrar(RegistroRequest registro) {
        if (usuarios.existsByEmail(registro.email())) {
            throw new EmailDuplicadoException();
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(registro.nombre());
        usuario.setEmail(registro.email());
        usuario.setPasswordHash(passwordEncoder.encode(registro.password()));
        usuario.setRol(Rol.USER);
        usuario.setFechaRegistro(LocalDateTime.now());

        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException exception) {
            if (esViolacionUnique(exception)) {
                throw new EmailDuplicadoException();
            }
            throw exception;
        }

        return new UsuarioResponse(usuario.getIdUsuario(), usuario.getNombre(), usuario.getEmail(),
                usuario.getRol(), usuario.getFechaRegistro());
    }

    private boolean esViolacionUnique(Throwable exception) {
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql
                    && ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 1062)) {
                return true;
            }
        }
        return false;
    }
}
