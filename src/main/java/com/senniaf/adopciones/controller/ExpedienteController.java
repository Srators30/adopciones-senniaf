package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.service.ExpedienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Operaciones que ESCRIBEN sobre el expediente (HU-02 y HU-03).
 *
 * Cuelga de /api/tecnico/**, asi que SecurityConfig solo deja pasar a
 * ADMINISTRADOR y TECNICO: un JUEZ recibe 403 y quien no manda token
 * recibe 401. Las consultas van en ConsultaExpedienteController, que si
 * admite a los tres roles.
 *
 * Cambio del Sprint 3: el expediente ya no se guarda en una lista en
 * memoria. Todo pasa por ExpedienteService, que persiste en base de
 * datos y escribe el historial.
 */
@RestController
@RequestMapping("/api/tecnico/expedientes")
public class ExpedienteController {

    private final ExpedienteService servicio;

    public ExpedienteController(ExpedienteService servicio) {
        this.servicio = servicio;
    }

    /** Crea el expediente. Responde 201 con el numero que asigno el servidor. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> datos,
                                                      @AuthenticationPrincipal Usuario usuario) {
        List<String> malos = servicio.validar(datos);
        if (!malos.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Datos invalidos", "campos", malos));
        }

        Expediente creado = servicio.crear(datos, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.aDetalle(creado));
    }

    /** Modifica un expediente existente y deja el detalle del cambio en el historial. */
    @PutMapping("/{numero}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable String numero,
                                                           @RequestBody Map<String, Object> datos,
                                                           @AuthenticationPrincipal Usuario usuario) {
        Optional<Expediente> encontrado = servicio.porNumero(numero);
        if (encontrado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Expediente e = encontrado.get();
        if (e.getEstado() == EstadoExpediente.CERRADO) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El expediente esta cerrado y no admite cambios"));
        }

        List<String> malos = servicio.validar(datos);
        if (!malos.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Datos invalidos", "campos", malos));
        }

        return ResponseEntity.ok(servicio.aDetalle(servicio.actualizar(e, datos, usuario)));
    }

    /** Cambia el estado administrativo del expediente. */
    @PatchMapping("/{numero}/estado")
    public ResponseEntity<Map<String, Object>> cambiarEstado(@PathVariable String numero,
                                                              @RequestBody Map<String, Object> cuerpo,
                                                              @AuthenticationPrincipal Usuario usuario) {
        Optional<Expediente> encontrado = servicio.porNumero(numero);
        if (encontrado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        EstadoExpediente nuevo;
        try {
            nuevo = EstadoExpediente.valueOf(String.valueOf(cuerpo.get("estado")).toUpperCase());
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Estado no valido",
                                 "permitidos", List.of("REGISTRADO", "EN_REVISION", "CERRADO")));
        }

        return ResponseEntity.ok(servicio.aDetalle(
                servicio.cambiarEstado(encontrado.get(), nuevo, usuario)));
    }
}
