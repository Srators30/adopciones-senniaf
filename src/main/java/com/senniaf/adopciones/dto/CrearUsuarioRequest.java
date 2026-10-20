package com.senniaf.adopciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Datos que llegan desde la pantalla "Crear usuario" (solo Administrador).
 * Las reglas repiten las del formulario: el navegador y el PHP ya validaron,
 * pero el servidor es quien decide.
 */
public class CrearUsuarioRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto;

    @NotBlank(message = "El usuario es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9._]{4,40}$",
             message = "Usa de 4 a 40 caracteres: letras, numeros, punto o guion bajo")
    private String username;

    @NotBlank(message = "La contrasena es obligatoria")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$",
             message = "Minimo 8 caracteres, con una mayuscula, un numero y un simbolo")
    private String password;

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "ADMINISTRADOR|TECNICO|JUEZ", message = "Rol no valido")
    private String rol;

    private boolean habilitado = true;

    public CrearUsuarioRequest() {
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }
}
