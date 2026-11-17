package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.DictamenIdoneidad;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.service.DictamenService;
import com.senniaf.adopciones.service.DictamenService.DictamenRechazado;
import com.senniaf.adopciones.service.FamiliaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dictamen de idoneidad y lista de espera (HU-06.1 a HU-06.4).
 *
 * Mismo reparto de rutas de todos los sprints anteriores, sin tocar la
 * configuracion de seguridad:
 *   - Emitir (escribe)   -> /api/tecnico/** : Administrador y Tecnico.
 *   - Cambiar y reabrir  -> /api/admin/**   : solo Administrador
 *                           (decisiones del PO, Sprint 7). Tecnico y Juez: 403.
 *   - Consultar (lee)    -> /api/familias/**: los tres roles, con token.
 */
@RestController
public class DictamenController {

    private final DictamenService dictamenes;
    private final FamiliaService familias;

    public DictamenController(DictamenService dictamenes, FamiliaService familias) {
        this.dictamenes = dictamenes;
        this.familias = familias;
    }

    /**
     * Emite el primer dictamen. Cuerpo: {"resultado": "IDONEA" | "NO_IDONEA", "observacion": "..."}
     *
     *   201  dictamen emitido
     *   400  resultado invalido u observacion que no cumple (lista de campos)
     *   404  no existe la solicitud
     *   409  la solicitud no esta En evaluacion, o ya tiene dictamen
     */
    @PostMapping("/api/tecnico/familias/{numero}/dictamen")
    public ResponseEntity<Map<String, Object>> emitir(@PathVariable String numero,
                                                       @RequestBody Map<String, Object> cuerpo,
                                                       @AuthenticationPrincipal Usuario usuario) {
        try {
            DictamenIdoneidad d = dictamenes.emitir(numero, cuerpo, usuario);
            return ResponseEntity.status(HttpStatus.CREATED).body(dictamenes.aMapa(d));
        } catch (DictamenRechazado ex) {
            return rechazo(ex);
        }
    }

    /**
     * HU-06.4: sustituye el dictamen vigente por uno nuevo.
     * Cuerpo: {"resultado": "...", "observacion": "...", "justificacion": "..."}
     *
     *   201  dictamen nuevo creado; el anterior queda como sustituido
     *   400  datos invalidos, mismo resultado que el vigente, o falta la justificacion
     *   404  no existe la solicitud
     *   409  la solicitud esta cerrada, o todavia no tiene dictamen
     */
    @PostMapping("/api/admin/familias/{numero}/dictamen/cambio")
    public ResponseEntity<Map<String, Object>> cambiar(@PathVariable String numero,
                                                        @RequestBody Map<String, Object> cuerpo,
                                                        @AuthenticationPrincipal Usuario usuario) {
        try {
            DictamenIdoneidad d = dictamenes.cambiar(numero, cuerpo, usuario);
            return ResponseEntity.status(HttpStatus.CREATED).body(dictamenes.aMapa(d));
        } catch (DictamenRechazado ex) {
            return rechazo(ex);
        }
    }

    /**
     * Sprint 7 (decision del PO): solo el Administrador reabre una solicitud
     * cerrada, con motivo. Cuerpo: {"motivo": "..."}
     *
     *   200  ficha de la familia con su nuevo estado
     *   400  falta el motivo o es muy corto
     *   404  no existe la solicitud
     *   409  la solicitud no esta cerrada
     */
    @PostMapping("/api/admin/familias/{numero}/reapertura")
    public ResponseEntity<Map<String, Object>> reabrir(@PathVariable String numero,
                                                        @RequestBody Map<String, Object> cuerpo,
                                                        @AuthenticationPrincipal Usuario usuario) {
        try {
            FamiliaSolicitante f = dictamenes.reabrir(numero, cuerpo, usuario);
            return ResponseEntity.ok(familias.aDetalle(f));
        } catch (DictamenRechazado ex) {
            return rechazo(ex);
        }
    }

    /**
     * Dictamen vigente (o null), su posicion en la lista de espera (o null)
     * y el historial completo de dictamenes, del mas reciente al mas antiguo.
     * Responde 200 aunque no haya dictamen, para que la pantalla no tenga
     * que distinguir "no hay dictamen" de un error.
     */
    @GetMapping("/api/familias/{numero}/dictamen")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable String numero) {
        Optional<FamiliaSolicitante> familia = familias.porNumero(numero);
        if (familia.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FamiliaSolicitante f = familia.get();
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("dictamen", dictamenes.vigente(f).map(dictamenes::aMapa).orElse(null));
        respuesta.put("posicionLista", dictamenes.posicion(f).orElse(null));
        respuesta.put("historial", dictamenes.historial(f).stream().map(dictamenes::aMapa).toList());
        return ResponseEntity.ok(respuesta);
    }

    /** HU-06.3: lista de espera de familias idoneas, para los tres roles. */
    @GetMapping("/api/familias/lista-espera")
    public Map<String, Object> listaDeEspera() {
        List<Map<String, Object>> filas = dictamenes.listaDeEspera();
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("fechaConsulta", LocalDate.now().toString());
        respuesta.put("total", filas.size());
        respuesta.put("familias", filas);
        return respuesta;
    }

    private ResponseEntity<Map<String, Object>> rechazo(DictamenRechazado ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage());
        if (!ex.getCampos().isEmpty()) {
            error.put("campos", ex.getCampos());
        }
        HttpStatus codigo = switch (ex.getMotivo()) {
            case NO_ENCONTRADA -> HttpStatus.NOT_FOUND;
            case DATOS_INVALIDOS -> HttpStatus.BAD_REQUEST;
            case ESTADO_NO_PERMITIDO, YA_DICTAMINADA, SIN_DICTAMEN -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(codigo).body(error);
    }
}
