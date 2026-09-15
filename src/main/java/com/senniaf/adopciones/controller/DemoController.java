package com.senniaf.adopciones.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints de demostracion para verificar el control de acceso por rol (HU-01).
 * En sprints posteriores estos se reemplazan por los endpoints reales de negocio
 * (expedientes, familias, etc.).
 */
@RestController
public class DemoController {

    @GetMapping("/api/admin/ping")
    public Map<String, String> adminPing() {
        return Map.of("mensaje", "Acceso permitido: rol Administrador");
    }

    @GetMapping("/api/tecnico/ping")
    public Map<String, String> tecnicoPing() {
        return Map.of("mensaje", "Acceso permitido: rol Administrador o Tecnico");
    }

    @GetMapping("/api/juez/ping")
    public Map<String, String> juezPing() {
        return Map.of("mensaje", "Acceso permitido: rol Juez");
    }
}
