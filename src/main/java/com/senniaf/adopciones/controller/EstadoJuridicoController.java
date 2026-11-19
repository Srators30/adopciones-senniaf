package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.service.CierreAutomaticoService;
import com.senniaf.adopciones.service.EstadoJuridicoService;
import com.senniaf.adopciones.service.EstadoJuridicoService.Rechazo;
import com.senniaf.adopciones.service.ExpedienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Sprint 8: etapa juridica, reapertura y cierres automaticos.
 *
 * Mismo reparto de rutas de siempre, sin tocar SecurityConfig:
 *   - Cambiar la etapa      -> /api/juez/**    : solo Juez (HU-07.2).
 *                              Tecnico y Administrador reciben 403.
 *   - Reabrir un expediente -> /api/admin/**   : solo Administrador.
 *   - Cierres automaticos   -> /api/tecnico/** : Administrador y Tecnico.
 * La etapa se lee en la ficha y en el listado, que ya estan abiertos a
 * los tres roles (HU-07.1).
 */
@RestController
public class EstadoJuridicoController {

    private final EstadoJuridicoService etapas;
    private final ExpedienteService expedientes;
    private final CierreAutomaticoService cierres;

    public EstadoJuridicoController(EstadoJuridicoService etapas, ExpedienteService expedientes,
                                    CierreAutomaticoService cierres) {
        this.etapas = etapas;
        this.expedientes = expedientes;
        this.cierres = cierres;
    }

    /**
     * HU-07.2. Cuerpo: {"etapa": "EN_PROCESO_JUDICIAL", "observacion": "..."}
     *
     *   200  ficha con la etapa nueva
     *   400  etapa inexistente u observacion de menos de 20 caracteres
     *   404  no existe el expediente
     *   409  expediente cerrado, etapa final o transicion no permitida
     */
    @PatchMapping("/api/juez/expedientes/{numero}/estado-juridico")
    public ResponseEntity<Map<String, Object>> cambiarEtapa(@PathVariable String numero,
                                                             @RequestBody Map<String, Object> cuerpo,
                                                             @AuthenticationPrincipal Usuario usuario) {
        try {
            return ResponseEntity.ok(ficha(etapas.cambiarEtapa(numero, cuerpo, usuario)));
        } catch (Rechazo ex) {
            return rechazo(ex);
        }
    }

    /**
     * Decision del PO: solo el Administrador reabre. Cuerpo: {"motivo": "..."}
     *
     *   200  ficha con el expediente En revision
     *   400  motivo de menos de 20 caracteres
     *   404  no existe el expediente
     *   409  el expediente no esta cerrado
     */
    @PostMapping("/api/admin/expedientes/{numero}/reapertura")
    public ResponseEntity<Map<String, Object>> reabrir(@PathVariable String numero,
                                                        @RequestBody Map<String, Object> cuerpo,
                                                        @AuthenticationPrincipal Usuario usuario) {
        try {
            return ResponseEntity.ok(ficha(etapas.reabrir(numero, cuerpo, usuario)));
        } catch (Rechazo ex) {
            return rechazo(ex);
        }
    }

    /** Seccion Cierres automaticos: los que estan por cerrarse y los que ya cerro el sistema. */
    @GetMapping("/api/tecnico/expedientes/cierres-automaticos")
    public Map<String, Object> cierresAutomaticos() {
        return cierres.resumen();
    }

    private Map<String, Object> ficha(Expediente e) {
        Map<String, Object> m = expedientes.aDetalle(e);
        m.put("cierreAutomatico", cierres.estadoEn(e));
        return m;
    }

    private static ResponseEntity<Map<String, Object>> rechazo(Rechazo ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage());
        if (!ex.getCampos().isEmpty()) {
            error.put("campos", ex.getCampos());
        }
        HttpStatus codigo = switch (ex.getMotivo()) {
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case DATOS_INVALIDOS -> HttpStatus.BAD_REQUEST;
            case CONFLICTO -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(codigo).body(error);
    }
}
