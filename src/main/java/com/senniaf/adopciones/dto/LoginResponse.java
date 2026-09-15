package com.senniaf.adopciones.dto;

public class LoginResponse {

    private final String token;
    private final String username;
    private final String rol;

    public LoginResponse(String token, String username, String rol) {
        this.token = token;
        this.username = username;
        this.rol = rol;
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
}
