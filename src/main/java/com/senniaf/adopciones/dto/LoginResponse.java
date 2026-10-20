package com.senniaf.adopciones.dto;

public class LoginResponse {

    private final String token;
    private final String username;
    private final String rol;
    private final String nombreCompleto;

    public LoginResponse(String token, String username, String rol, String nombreCompleto) {
        this.token = token;
        this.username = username;
        this.rol = rol;
        this.nombreCompleto = nombreCompleto;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public String getRol() {
        return rol;
    }

    /** Nombre para mostrar en pantalla; el frontend PHP lo usa en la barra superior. */
    public String getNombreCompleto() {
        return nombreCompleto;
    }
}
