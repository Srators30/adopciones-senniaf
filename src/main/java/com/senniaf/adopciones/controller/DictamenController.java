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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dictamen de idoneidad (HU-06.1 y HU-06.2).
 *
 * Mismo reparto de rutas de todos los sprints anteriores, sin tocar la
 * configuracion de seguridad:
 *   - Emitir (escribe)   -> /api/tecnico/**  : Administrador y Tecnico.
 *                           El Juez recibe 403 (HU-06.1, criterio 4).
 *   - Consultar (lee)    -> /api/familias/** : los tres roles, con token.
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
     * Emite el dictamen. Cuerpo: {"resultado": "IDONEA" | "NO_IDONEA", "observacion": "..."}
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
            Map<String, Object> error = new HashMap<>();
            error.put("error", ex.getMessage());
            if (!ex.getCampos().isEmpty()) {
                error.put("campos", ex.getCampos());
            }
            HttpStatus codigo = switch (ex.getMotivo()) {
                case NO_ENCONTRADA -> HttpStatus.NOT_FOUND;
                case DATOS_INVALIDOS -> HttpStatus.BAD_REQUEST;
                case ESTADO_NO_PERMITIDO, YA_DICTAMINADA -> HttpStatus.CONFLICT;
            };
            return ResponseEntity.status(codigo).body(error);
        }
    }

    /**
     * Dictamen vigente de la familia, o null si todavia no tiene.
     * Responde siempre 200 con {"dictamen": ...} para que la pantalla no
     * tenga que distinguir "no hay dictamen" de un error.
     */
    @GetMapping("/api/familias/{numero}/dictamen")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable String numero) {
        Optional<FamiliaSolicitante> familia = familias.porNumero(numero);
        if (familia.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("dictamen", dictamenes.vigente(familia.get()).map(dictamenes::aMapa).orElse(null));
        return ResponseEntity.ok(respuesta);
    }
}
