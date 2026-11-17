package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.service.FamiliaService;
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
 * Operaciones que ESCRIBEN sobre la familia solicitante (HU-05).
 *
 * Cuelga de /api/tecnico/**, asi que SecurityConfig solo deja pasar a
 * ADMINISTRADOR y TECNICO: un JUEZ recibe 403 y quien no manda token
 * recibe 401. Las consultas van en ConsultaFamiliaController.
 */
@RestController
@RequestMapping("/api/tecnico/familias")
public class FamiliaController {

    private final FamiliaService servicio;

    public FamiliaController(FamiliaService servicio) {
        this.servicio = servicio;
    }

    /** Registra la solicitud. Responde 201 con el numero que asigno el servidor. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> datos,
                                                      @AuthenticationPrincipal Usuario usuario) {
        List<String> malos = servicio.validar(datos);
        if (!malos.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Datos invalidos", "campos", malos));
        }

        // Una misma persona no deberia tener dos solicitudes abiertas:
        // casi siempre es el mismo formulario enviado dos veces.
        String documento = String.valueOf(datos.getOrDefault("numero_documento", "")).trim();
        if (servicio.documentoYaRegistrado(documento)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe una solicitud registrada con ese documento",
                                 "campos", List.of("numero_documento")));
        }

        FamiliaSolicitante creada = servicio.crear(datos, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.aDetalle(creada));
    }

    /** Modifica la solicitud y deja el detalle del cambio en el historial. */
    @PutMapping("/{numero}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable String numero,
                                                           @RequestBody Map<String, Object> datos,
                                                           @AuthenticationPrincipal Usuario usuario) {
        Optional<FamiliaSolicitante> encontrada = servicio.porNumero(numero);
        if (encontrada.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FamiliaSolicitante f = encontrada.get();
        if (f.getEstado() == EstadoFamilia.CERRADA) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "La solicitud esta cerrada y no admite cambios"));
        }

        List<String> malos = servicio.validar(datos);
        if (!malos.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Datos invalidos", "campos", malos));
        }

        return ResponseEntity.ok(servicio.aDetalle(servicio.actualizar(f, datos, usuario)));
    }

    /** Cambia el estado administrativo de la solicitud. */
    @PatchMapping("/{numero}/estado")
    public ResponseEntity<Map<String, Object>> cambiarEstado(@PathVariable String numero,
                                                              @RequestBody Map<String, Object> cuerpo,
                                                              @AuthenticationPrincipal Usuario usuario) {
        Optional<FamiliaSolicitante> encontrada = servicio.porNumero(numero);
        if (encontrada.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        EstadoFamilia nuevo;
        try {
            nuevo = EstadoFamilia.valueOf(String.valueOf(cuerpo.get("estado")).toUpperCase());
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Estado no valido",
                                 "permitidos", List.of("REGISTRADA", "EN_EVALUACION", "CERRADA")));
        }

        // Sprint 7: Idonea y No idonea los pone el dictamen, no una persona.
        if (nuevo.esDeDictamen()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ese estado lo asigna el dictamen de idoneidad; no se elige a mano.",
                                 "permitidos", List.of("REGISTRADA", "EN_EVALUACION", "CERRADA")));
        }
        String rechazo = servicio.rechazoCambioManual(encontrada.get(), nuevo);
        if (rechazo != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", rechazo));
        }

        return ResponseEntity.ok(servicio.aDetalle(
                servicio.cambiarEstado(encontrada.get(), nuevo, usuario)));
    }
}
