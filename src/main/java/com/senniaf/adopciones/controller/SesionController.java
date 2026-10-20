package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.Usuario;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Devuelve quien es el usuario del token actual.
 *
 * Sirve para que el frontend confirme la sesion contra el servidor: si
 * el token esta vencido o mal firmado responde 401, porque esta ruta
 * cae en .anyRequest().authenticated().
 */
@RestController
public class SesionController {

    @GetMapping("/api/me")
    public Map<String, String> sesionActual(@AuthenticationPrincipal Usuario usuario) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("username", usuario.getUsername());
        datos.put("nombreCompleto", usuario.getNombreCompleto());
        datos.put("rol", usuario.getRol().name());
        return datos;
    }
}
