package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.service.ExpedienteService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consulta y trazabilidad del expediente (HU-03).
 *
 * Cuelga de /api/expedientes, que NO esta en las reglas por rol de
 * SecurityConfig y por lo tanto cae en .anyRequest().authenticated():
 * los tres roles pueden consultar, pero nadie sin token. Es a
 * proposito, porque el Juez necesita leer expedientes aunque no pueda
 * modificarlos; escribir sigue reservado a /api/tecnico/**.
 */
@RestController
@RequestMapping("/api/expedientes")
public class ConsultaExpedienteController {

    private final ExpedienteService servicio;

    public ConsultaExpedienteController(ExpedienteService servicio) {
        this.servicio = servicio;
    }

    /**
     * Listado con filtros basicos. Todos los parametros son opcionales:
     *   q          texto libre (nombres, apellidos, numero o documento)
     *   estado     REGISTRADO | EN_REVISION | CERRADO
     *   modalidad  centro_acogimiento | familia_acogida | ...
     */
    @GetMapping
    public Map<String, Object> listar(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String estado,
                                       @RequestParam(required = false) String modalidad,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "10") int tamano) {

        Page<Expediente> resultado = servicio.buscar(q, estado, modalidad, pagina, tamano);

        List<Map<String, Object>> filas = resultado.getContent().stream()
                .map(servicio::aResumen)
                .toList();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("contenido", filas);
        respuesta.put("pagina", resultado.getNumber());
        respuesta.put("tamano", resultado.getSize());
        respuesta.put("total", resultado.getTotalElements());
        respuesta.put("totalPaginas", resultado.getTotalPages());
        return respuesta;
    }

    /** Ficha completa. La consulta queda registrada en el historial. */
    @GetMapping("/{numero}")
    public ResponseEntity<Map<String, Object>> detalle(@PathVariable String numero,
                                                        @AuthenticationPrincipal Usuario usuario) {
        return servicio.verDetalle(numero, usuario)
                .map(e -> ResponseEntity.ok(servicio.aDetalle(e)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Historial completo: quien hizo que y cuando. */
    @GetMapping("/{numero}/historial")
    public ResponseEntity<List<Map<String, Object>>> historial(@PathVariable String numero) {
        Optional<Expediente> encontrado = servicio.porNumero(numero);
        if (encontrado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(servicio.historial(encontrado.get()).stream()
                .map(servicio::aEvento)
                .toList());
    }
}
