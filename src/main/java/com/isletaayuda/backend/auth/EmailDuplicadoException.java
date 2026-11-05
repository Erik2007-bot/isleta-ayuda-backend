package com.isletaayuda.backend.auth;

public class EmailDuplicadoException extends RuntimeException {

    public EmailDuplicadoException() {
        super("El email ya está registrado");
    }
}
