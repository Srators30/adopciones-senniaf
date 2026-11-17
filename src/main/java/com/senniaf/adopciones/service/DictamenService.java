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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dictamen de idoneidad de una familia (HU-06.1, HU-06.2, HU-06.3 y HU-06.4).
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

    /** HU-06.4, criterio 2: un cambio de dictamen se justifica con algo mas que un "error". */
    static final int MINIMO_JUSTIFICACION = 20;
    static final int MAXIMO_JUSTIFICACION = 1000;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Por que se rechazo un dictamen. El controlador lo traduce a codigo
     * HTTP: NO_ENCONTRADA es 404, DATOS_INVALIDOS es 400 y los demas son 409.
     * SIN_DICTAMEN (Sprint 7): se pidio cambiar un dictamen que no existe.
     */
    public enum Motivo { NO_ENCONTRADA, DATOS_INVALIDOS, ESTADO_NO_PERMITIDO, YA_DICTAMINADA, SIN_DICTAMEN }

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

        // Un dictamen no se sobrescribe. Para sustituirlo esta cambiar(), solo para el Administrador.
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

        // Sprint 7 (decision del PO): el estado de la solicitud sigue al dictamen.
        EstadoFamilia estadoAnterior = familia.getEstado();
        moverEstado(familia, EstadoFamilia.segunDictamen(resultado), usuario);

        // Trazabilidad: el dictamen queda en el historial de la solicitud,
        // igual que cualquier otra operacion desde el Sprint 3.
        String descripcion = "Dictamen emitido: " + resultado.getEtiqueta()
                + (dictamen.getFechaIdoneidad() != null
                   ? " (fecha de idoneidad " + dictamen.getFechaIdoneidad() + ")." : ".")
                + " Estado: " + estadoAnterior.getEtiqueta() + " -> " + familia.getEstado().getEtiqueta() + ".";
        familiaService.registrar(familia, TipoEvento.DICTAMEN, descripcion, usuario);

        return dictamen;
    }

    // =====================================================================
    //  Cambiar (HU-06.4, Sprint 7)
    // =====================================================================

    /**
     * Sustituye el dictamen vigente por uno nuevo. Decisiones del PO:
     *   - Solo el Administrador (lo garantiza la ruta /api/admin/**).
     *   - Si el nuevo resultado es IDONEA, la fecha de idoneidad es la del
     *     cambio, y el dictamen anterior conserva desde cuando y hasta
     *     cuando estuvo vigente.
     *   - Una solicitud cerrada no admite cambios: primero se reabre.
     *
     * El anterior no se borra ni se edita: queda marcado como sustituido
     * (criterio 1) y el cambio queda en el historial (criterio 3).
     */
    @Transactional
    public DictamenIdoneidad cambiar(String numeroSolicitud, Map<String, Object> datos, Usuario usuario) {

        FamiliaSolicitante familia = familias.buscarParaDictamen(numeroSolicitud)
                .orElseThrow(() -> new DictamenRechazado(Motivo.NO_ENCONTRADA,
                        "No existe una solicitud con el número " + numeroSolicitud, null));

        if (familia.getEstado() == EstadoFamilia.CERRADA) {
            throw new DictamenRechazado(Motivo.ESTADO_NO_PERMITIDO,
                    "Una solicitud cerrada no admite cambios de dictamen. Primero hay que reabrirla.", null);
        }

        DictamenIdoneidad anterior = dictamenes.findFirstByFamiliaIdAndVigenteTrue(familia.getId())
                .orElseThrow(() -> new DictamenRechazado(Motivo.SIN_DICTAMEN,
                        "La solicitud todavía no tiene dictamen: primero hay que emitirlo.", null));

        ResultadoIdoneidad resultado = leerResultado(datos.get("resultado"));
        String observacion = texto(datos.get("observacion"));
        String justificacion = texto(datos.get("justificacion"));

        List<String> malos = validar(resultado, observacion);
        String mensaje = malos.isEmpty() ? null : mensajeDeError(resultado, malos);

        // Un "cambio" al mismo resultado no cambia nada, y si era IDONEA le
        // pondria fecha nueva a la familia y la mandaria al final de la fila.
        if (resultado != null && resultado == anterior.getResultado()) {
            malos.add(0, "resultado");
            mensaje = "El dictamen vigente ya es " + resultado.getEtiqueta()
                    + ": un cambio tiene que cambiar el resultado.";
        }

        // HU-06.4, criterio 2.
        if (justificacion.length() < MINIMO_JUSTIFICACION || justificacion.length() > MAXIMO_JUSTIFICACION) {
            malos.add("justificacion");
            if (mensaje == null) {
                mensaje = "Todo cambio de dictamen exige una justificación de entre "
                        + MINIMO_JUSTIFICACION + " y " + MAXIMO_JUSTIFICACION + " caracteres.";
            }
        }
        if (!malos.isEmpty()) {
            throw new DictamenRechazado(Motivo.DATOS_INVALIDOS, mensaje, malos);
        }

        anterior.sustituir(usuario.getUsername());
        dictamenes.save(anterior);
        EstadoFamilia estadoAnterior = familia.getEstado();
        moverEstado(familia, EstadoFamilia.segunDictamen(resultado), usuario);

        DictamenIdoneidad nuevo = dictamenes.save(new DictamenIdoneidad(
                familia, resultado, observacion, usuario.getUsername(), usuario.getRol().name(), justificacion));

        // Criterio 3: el cambio queda en el historial con los dos periodos.
        String descripcion = "Dictamen cambiado: " + anterior.getResultado().getEtiqueta()
                + " -> " + resultado.getEtiqueta() + ". "
                + "Fue " + anterior.getResultado().getEtiqueta() + " del "
                + anterior.getFechaDictamen().toLocalDate() + " al "
                + anterior.getFechaSustitucion().toLocalDate() + "."
                + (nuevo.getFechaIdoneidad() != null
                   ? " Nueva fecha de idoneidad: " + nuevo.getFechaIdoneidad() + "." : "")
                + " Estado: " + estadoAnterior.getEtiqueta() + " -> " + familia.getEstado().getEtiqueta() + "."
                + " Justificación: " + justificacion;
        familiaService.registrar(familia, TipoEvento.DICTAMEN, recortar(descripcion, 1000), usuario);

        return nuevo;
    }

    // =====================================================================
    //  Reabrir una solicitud cerrada (Sprint 7, decision del PO)
    // =====================================================================

    /** El motivo de una reapertura sigue la misma regla que la justificacion de un cambio. */
    static final int MINIMO_MOTIVO_REAPERTURA = MINIMO_JUSTIFICACION;

    /**
     * Solo el Administrador (ruta /api/admin/**) reabre una solicitud
     * cerrada, y siempre con motivo. Vuelve al estado que marca su dictamen
     * vigente; si nunca tuvo dictamen, vuelve a En evaluacion.
     */
    @Transactional
    public FamiliaSolicitante reabrir(String numeroSolicitud, Map<String, Object> datos, Usuario usuario) {
        FamiliaSolicitante familia = familias.buscarParaDictamen(numeroSolicitud)
                .orElseThrow(() -> new DictamenRechazado(Motivo.NO_ENCONTRADA,
                        "No existe una solicitud con el número " + numeroSolicitud, null));

        if (familia.getEstado() != EstadoFamilia.CERRADA) {
            throw new DictamenRechazado(Motivo.ESTADO_NO_PERMITIDO,
                    "Solo una solicitud cerrada se puede reabrir. Estado actual: "
                            + familia.getEstado().getEtiqueta() + ".", null);
        }

        String motivo = texto(datos.get("motivo"));
        if (motivo.length() < MINIMO_MOTIVO_REAPERTURA || motivo.length() > MAXIMO_JUSTIFICACION) {
            throw new DictamenRechazado(Motivo.DATOS_INVALIDOS,
                    "Reabrir una solicitud exige un motivo de entre " + MINIMO_MOTIVO_REAPERTURA
                            + " y " + MAXIMO_JUSTIFICACION + " caracteres.", List.of("motivo"));
        }

        EstadoFamilia destino = dictamenes.findFirstByFamiliaIdAndVigenteTrue(familia.getId())
                .map(d -> EstadoFamilia.segunDictamen(d.getResultado()))
                .orElse(EstadoFamilia.EN_EVALUACION);
        moverEstado(familia, destino, usuario);

        familiaService.registrar(familia, TipoEvento.CAMBIO_ESTADO,
                recortar("Solicitud reabierta: Cerrada -> " + destino.getEtiqueta()
                        + ". Motivo: " + motivo, 1000), usuario);
        return familia;
    }

    /** Cambia el estado y deja constancia de quien y cuando en la propia familia. */
    private void moverEstado(FamiliaSolicitante familia, EstadoFamilia nuevo, Usuario usuario) {
        familia.setEstado(nuevo);
        familia.setFechaActualizacion(LocalDateTime.now());
        familia.setActualizadoPor(usuario.getUsername());
        familias.save(familia);
    }

    /**
     * Ajuste de datos de una sola vez: las solicitudes dictaminadas antes de
     * este cambio quedaron En evaluacion. Al arrancar se ponen en el estado
     * de su dictamen vigente (ver config/SincronizarEstadosDictamen).
     * Devuelve cuantas se ajustaron.
     */
    @Transactional
    public int sincronizarEstados() {
        int ajustadas = 0;
        for (DictamenIdoneidad d : dictamenes.findByVigenteTrue()) {
            FamiliaSolicitante f = d.getFamilia();
            if (f.getEstado() == EstadoFamilia.EN_EVALUACION) {
                EstadoFamilia nuevo = EstadoFamilia.segunDictamen(d.getResultado());
                f.setEstado(nuevo);
                familias.save(f);
                familiaService.registrar(f, TipoEvento.CAMBIO_ESTADO,
                        "Estado ajustado al dictamen vigente: En evaluación -> " + nuevo.getEtiqueta()
                                + " (ajuste automático del Sprint 7).",
                        d.getEmitidoPor(), d.getRolEmisor());
                ajustadas++;
            }
        }
        return ajustadas;
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

    /** HU-06.4, criterio 5: todos los dictamenes, del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    public List<DictamenIdoneidad> historial(FamiliaSolicitante familia) {
        return dictamenes.findByFamiliaIdOrderByFechaDictamenDescIdDesc(familia.getId());
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
        // Sprint 7: periodo de vigencia y motivo del cambio.
        m.put("vigente", d.isVigente());
        m.put("fechaSustitucion", d.getFechaSustitucion() == null ? "" : d.getFechaSustitucion().format(FECHA_HORA));
        m.put("sustituidoPor", d.getSustituidoPor() == null ? "" : d.getSustituidoPor());
        m.put("justificacionCambio", d.getJustificacionCambio() == null ? "" : d.getJustificacionCambio());
        return m;
    }

    // =====================================================================
    //  Lista de espera (HU-06.3, Sprint 7)
    // =====================================================================

    /**
     * Familias idoneas en orden de espera. La posicion no se guarda en la
     * base: se calcula en cada consulta a partir de la regla (criterio 2).
     * Asi nunca puede quedar desactualizada, y si una familia deja de ser
     * idonea sale de la lista en ese mismo momento (HU-06.4, criterio 4).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listaDeEspera() {
        List<DictamenIdoneidad> filas =
                dictamenes.listaDeEspera(ResultadoIdoneidad.IDONEA, EstadoFamilia.CERRADA);
        LocalDate hoy = LocalDate.now();

        List<Map<String, Object>> lista = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            DictamenIdoneidad d = filas.get(i);
            FamiliaSolicitante f = d.getFamilia();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("posicion", i + 1);
            m.put("numeroSolicitud", f.getNumeroSolicitud());
            m.put("familia", f.getNombreFamilia());
            m.put("modalidad", f.getModalidadSolicitada() == null ? "" : f.getModalidadSolicitada());
            m.put("fechaIdoneidad", d.getFechaIdoneidad().toString());
            m.put("diasEnEspera", ChronoUnit.DAYS.between(d.getFechaIdoneidad(), hoy));
            lista.add(m);
        }
        return lista;
    }

    /** HU-06.3, criterio 5: posicion de la familia en la lista, o vacio si no esta. */
    @Transactional(readOnly = true)
    public Optional<Integer> posicion(FamiliaSolicitante familia) {
        List<DictamenIdoneidad> filas =
                dictamenes.listaDeEspera(ResultadoIdoneidad.IDONEA, EstadoFamilia.CERRADA);
        for (int i = 0; i < filas.size(); i++) {
            if (Objects.equals(filas.get(i).getFamilia().getId(), familia.getId())) {
                return Optional.of(i + 1);
            }
        }
        return Optional.empty();
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

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
