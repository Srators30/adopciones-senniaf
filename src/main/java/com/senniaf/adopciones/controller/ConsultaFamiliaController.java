package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.service.FamiliaService;
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
 * Consulta y trazabilidad de las familias solicitantes (HU-05).
 *
 * Cuelga de /api/familias, que NO esta en las reglas por rol de
 * SecurityConfig y por lo tanto cae en .anyRequest().authenticated():
 * los tres roles pueden consultar, pero nadie sin token. Mismo criterio
 * que /api/expedientes en el Sprint 3.
 */
@RestController
@RequestMapping("/api/familias")
public class ConsultaFamiliaController {

    private final FamiliaService servicio;

    public ConsultaFamiliaController(FamiliaService servicio) {
        this.servicio = servicio;
    }

    /**
     * Listado con filtros. Todos los parametros son opcionales:
     *   q          texto libre (nombres, apellidos, numero o documento)
     *   estado     REGISTRADA | EN_EVALUACION | CERRADA
     *   modalidad  adopcion | acogimiento_familiar
     *   provincia  nombre de la provincia de residencia
     */
    @GetMapping
    public Map<String, Object> listar(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String estado,
                                       @RequestParam(required = false) String modalidad,
                                       @RequestParam(required = false) String provincia,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "10") int tamano) {

        Page<FamiliaSolicitante> resultado =
                servicio.buscar(q, estado, modalidad, provincia, pagina, tamano);

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
                .map(f -> ResponseEntity.ok(servicio.aDetalle(f)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Historial completo: quien hizo que y cuando. */
    @GetMapping("/{numero}/historial")
    public ResponseEntity<List<Map<String, Object>>> historial(@PathVariable String numero) {
        Optional<FamiliaSolicitante> encontrada = servicio.porNumero(numero);
        if (encontrada.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(servicio.historial(encontrada.get()).stream()
                .map(servicio::aEvento)
                .toList());
    }
}
