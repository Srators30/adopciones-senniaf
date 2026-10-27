package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.DictamenIdoneidad;
import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.model.ResultadoIdoneidad;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.DictamenIdoneidadRepository;
import com.senniaf.adopciones.repository.FamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dictamen de idoneidad de una familia (HU-06.1 y HU-06.2).
 *
 * Cada regla de este servicio corresponde a un criterio de aceptacion de
 * las historias; el numero va al lado para poder ir y volver entre el
 * codigo y el documento de planificacion.
 */
@Service
public class DictamenService {

    /** HU-06.2, criterio 2. */
    static final int MINIMO_OBSERVACION_NO_IDONEA = 30;
    static final int MAXIMO_OBSERVACION = 2000;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Por que se rechazo un dictamen. El controlador lo traduce a codigo
     * HTTP: DATOS_INVALIDOS es 400, los otros dos son 409.
     */
    public enum Motivo { NO_ENCONTRADA, DATOS_INVALIDOS, ESTADO_NO_PERMITIDO, YA_DICTAMINADA }

    public static class DictamenRechazado extends RuntimeException {
        private final Motivo motivo;
        private final List<String> campos;

        public DictamenRechazado(Motivo motivo, String mensaje, List<String> campos) {
            super(mensaje);
            this.motivo = motivo;
            this.campos = campos == null ? List.of() : List.copyOf(campos);
        }

        public Motivo getMotivo() { return motivo; }
        public List<String> getCampos() { return campos; }
    }

    private final DictamenIdoneidadRepository dictamenes;
    private final FamiliaRepository familias;
    private final FamiliaService familiaService;

    public DictamenService(DictamenIdoneidadRepository dictamenes, FamiliaRepository familias,
                           FamiliaService familiaService) {
        this.dictamenes = dictamenes;
        this.familias = familias;
        this.familiaService = familiaService;
    }

    // =====================================================================
    //  Emitir
    // =====================================================================

    @Transactional
    public DictamenIdoneidad emitir(String numeroSolicitud, Map<String, Object> datos, Usuario usuario) {

        // Con bloqueo de fila: ver FamiliaRepository.buscarParaDictamen.
        FamiliaSolicitante familia = familias.buscarParaDictamen(numeroSolicitud)
                .orElseThrow(() -> new DictamenRechazado(Motivo.NO_ENCONTRADA,
                        "No existe una solicitud con el número " + numeroSolicitud, null));

        // HU-06.1, criterio 1: solo una solicitud en evaluacion admite dictamen.
        if (familia.getEstado() != EstadoFamilia.EN_EVALUACION) {
            throw new DictamenRechazado(Motivo.ESTADO_NO_PERMITIDO,
                    "Solo una solicitud En evaluación admite dictamen. Estado actual: "
                            + familia.getEstado().getEtiqueta() + ".", null);
        }

        // Un dictamen no se sobrescribe. Sustituirlo con justificacion es HU-06.4 (Sprint 7).
        if (dictamenes.existsByFamiliaIdAndVigenteTrue(familia.getId())) {
            throw new DictamenRechazado(Motivo.YA_DICTAMINADA,
                    "La solicitud ya tiene un dictamen vigente y no se puede sobrescribir.", null);
        }

        // HU-06.1, criterio 2, y HU-06.2, criterios 1 y 2.
        ResultadoIdoneidad resultado = leerResultado(datos.get("resultado"));
        String observacion = texto(datos.get("observacion"));
        List<String> malos = validar(resultado, observacion);
        if (!malos.isEmpty()) {
            throw new DictamenRechazado(Motivo.DATOS_INVALIDOS, mensajeDeError(resultado, malos), malos);
        }

        DictamenIdoneidad dictamen = dictamenes.save(new DictamenIdoneidad(
                familia, resultado, observacion, usuario.getUsername(), usuario.getRol().name()));

        // Trazabilidad: el dictamen queda en el historial de la solicitud,
        // igual que cualquier otra operacion desde el Sprint 3.
        String descripcion = "Dictamen emitido: " + resultado.getEtiqueta()
                + (dictamen.getFechaIdoneidad() != null
                   ? " (fecha de idoneidad " + dictamen.getFechaIdoneidad() + ")." : ".");
        familiaService.registrar(familia, TipoEvento.DICTAMEN, descripcion, usuario);

        return dictamen;
    }

    /** Valida resultado y observacion; devuelve los campos con problema. */
    List<String> validar(ResultadoIdoneidad resultado, String observacion) {
        List<String> malos = new ArrayList<>();
        if (resultado == null) {
            malos.add("resultado");
        }
        if (observacion.isEmpty() || observacion.length() > MAXIMO_OBSERVACION) {
            malos.add("observacion");
        } else if (resultado == ResultadoIdoneidad.NO_IDONEA
                && observacion.length() < MINIMO_OBSERVACION_NO_IDONEA) {
            malos.add("observacion");
        }
        return malos;
    }

    private String mensajeDeError(ResultadoIdoneidad resultado, List<String> malos) {
        if (malos.contains("resultado")) {
            return "El resultado debe ser IDONEA o NO_IDONEA.";
        }
        if (resultado == ResultadoIdoneidad.NO_IDONEA) {
            return "Un dictamen No idónea exige una observación de al menos "
                    + MINIMO_OBSERVACION_NO_IDONEA + " caracteres que explique el motivo.";
        }
        return "Todo dictamen exige una observación (máximo " + MAXIMO_OBSERVACION + " caracteres).";
    }

    // =====================================================================
    //  Consultar
    // =====================================================================

    @Transactional(readOnly = true)
    public Optional<DictamenIdoneidad> vigente(FamiliaSolicitante familia) {
        return dictamenes.findFirstByFamiliaIdAndVigenteTrue(familia.getId());
    }

    public Map<String, Object> aMapa(DictamenIdoneidad d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resultado", d.getResultado().name());
        m.put("resultadoEtiqueta", d.getResultado().getEtiqueta());
        m.put("observacion", d.getObservacion());
        m.put("fechaIdoneidad", d.getFechaIdoneidad() == null ? "" : d.getFechaIdoneidad().toString());
        m.put("fechaDictamen", d.getFechaDictamen().format(FECHA_HORA));
        m.put("emitidoPor", d.getEmitidoPor());
        m.put("rolEmisor", d.getRolEmisor());
        return m;
    }

    // ---------------------------- utilidades ----------------------------

    private static ResultadoIdoneidad leerResultado(Object v) {
        if (v == null) return null;
        try {
            return ResultadoIdoneidad.valueOf(v.toString().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String texto(Object v) {
        return v == null ? "" : v.toString().trim();
    }
}
