package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.repository.EventoExpedienteRepository;
import com.senniaf.adopciones.repository.ExpedienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cierre automatico de expedientes (decision del PO, Sprint 8).
 *
 * REGLA
 * Un expediente se cierra solo cuando cumple las dos condiciones:
 *   - el menor esta en una etapa final (Adoptado o Reintegrado a su
 *     familia), es decir, los tramites importantes ya terminaron, y
 *   - nadie lo ha trabajado en los ultimos N dias (7 por defecto).
 *
 * "Trabajar" es cualquier evento del historial menos las consultas:
 * modificar datos, adjuntar o descargar un documento, cambiar el estado o
 * la etapa. Cada actividad reinicia el plazo, asi que un expediente que se
 * sigue trabajando despues de la sentencia nunca se cierra solo. Antes de
 * la etapa final no hay plazo: nunca se cierra solo.
 *
 * El plazo se cambia en application.properties, sin tocar el codigo:
 *     app.expedientes.dias-cierre-automatico=7
 *
 * Cada cierre queda en el historial como hecho por "Sistema" y en la
 * seccion Cierres automaticos. Si no debia cerrarse, el Administrador lo
 * reabre con motivo.
 */
@Service
public class CierreAutomaticoService {

    public static final String USUARIO_SISTEMA = "Sistema";
    public static final String ROL_SISTEMA = "SISTEMA";

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ExpedienteRepository expedientes;
    private final EventoExpedienteRepository eventos;
    private final ExpedienteService expedienteService;
    private final int plazoDias;

    public CierreAutomaticoService(ExpedienteRepository expedientes,
                                   EventoExpedienteRepository eventos,
                                   ExpedienteService expedienteService,
                                   @Value("${app.expedientes.dias-cierre-automatico:7}") int plazoDias) {
        this.expedientes = expedientes;
        this.eventos = eventos;
        this.expedienteService = expedienteService;
        this.plazoDias = Math.max(plazoDias, 1);
    }

    public int getPlazoDias() {
        return plazoDias;
    }

    /** Ultima vez que alguien trabajo el expediente (sin contar consultas). */
    public LocalDateTime ultimaActividad(Expediente e) {
        LocalDateTime ultima = eventos.ultimaActividad(e.getId(), TipoEvento.CONSULTA);
        if (ultima != null) return ultima;
        if (e.getFechaEstadoJuridico() != null) return e.getFechaEstadoJuridico();
        return e.getFechaCreacion();
    }

    /** Momento en que se cerraria solo, o null si no le corre el plazo. */
    public LocalDateTime fechaLimite(Expediente e) {
        if (!e.getEstadoJuridico().esFinal() || e.getEstado() == EstadoExpediente.CERRADO) {
            return null;
        }
        return ultimaActividad(e).plusDays(plazoDias);
    }

    /**
     * Cierra los expedientes vencidos. Lo llama TareasExpedientes al
     * arrancar y una vez al dia. Devuelve cuantos cerro.
     */
    @Transactional
    public int cerrarVencidos() {
        LocalDateTime ahora = LocalDateTime.now();
        int cerrados = 0;
        for (Expediente e : expedientes.findByEstadoJuridicoInAndEstadoNot(
                EstadoJuridico.finales(), EstadoExpediente.CERRADO)) {
            LocalDateTime ultima = ultimaActividad(e);
            if (ultima.plusDays(plazoDias).isAfter(ahora)) {
                continue;   // todavia en plazo
            }
            e.setEstado(EstadoExpediente.CERRADO);
            e.setFechaCierreAutomatico(ahora);
            e.setFechaActualizacion(ahora);
            e.setActualizadoPor(USUARIO_SISTEMA);
            expedientes.save(e);
            expedienteService.registrar(e, TipoEvento.CAMBIO_ESTADO,
                    "Cierre automático: " + plazoDias + " días sin actividad en la etapa "
                            + e.getEstadoJuridico().getEtiqueta() + " (última actividad: "
                            + ultima.format(FECHA_HORA) + "). Si no debía cerrarse, el Administrador puede reabrirlo.",
                    USUARIO_SISTEMA, ROL_SISTEMA);
            cerrados++;
        }
        return cerrados;
    }

    /** Lo que muestra la seccion Cierres automaticos. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        LocalDate hoy = LocalDate.now();

        List<Map<String, Object>> porCerrarse = new ArrayList<>();
        for (Expediente e : expedientes.findByEstadoJuridicoInAndEstadoNot(
                EstadoJuridico.finales(), EstadoExpediente.CERRADO)) {
            LocalDateTime ultima = ultimaActividad(e);
            LocalDateTime limite = ultima.plusDays(plazoDias);
            Map<String, Object> m = base(e);
            m.put("ultimaActividad", ultima.format(FECHA_HORA));
            m.put("fechaLimite", limite.format(FECHA_HORA));
            m.put("diasRestantes", Math.max(0, ChronoUnit.DAYS.between(hoy, limite.toLocalDate())));
            porCerrarse.add(m);
        }
        porCerrarse.sort(Comparator.comparing(m -> (String) m.get("fechaLimite")));

        List<Map<String, Object>> cerrados = new ArrayList<>();
        for (Expediente e : expedientes.findByFechaCierreAutomaticoIsNotNullAndEstadoOrderByFechaCierreAutomaticoDesc(
                EstadoExpediente.CERRADO)) {
            Map<String, Object> m = base(e);
            m.put("fechaCierre", e.getFechaCierreAutomatico().format(FECHA_HORA));
            cerrados.add(m);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("plazoDias", plazoDias);
        r.put("porCerrarse", porCerrarse);
        r.put("cerrados", cerrados);
        return r;
    }

    /** Datos para la ficha: cuantos dias le quedan, o cuando lo cerro el sistema. */
    @Transactional(readOnly = true)
    public Map<String, Object> estadoEn(Expediente e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plazoDias", plazoDias);
        LocalDateTime limite = fechaLimite(e);
        if (limite != null) {
            m.put("fechaLimite", limite.format(FECHA_HORA));
            m.put("diasRestantes", Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), limite.toLocalDate())));
        }
        return m;
    }

    private static Map<String, Object> base(Expediente e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numeroExpediente", e.getNumeroExpediente());
        m.put("nombreCompleto", e.getNombreCompleto());
        m.put("estadoJuridico", e.getEstadoJuridico().name());
        m.put("estadoJuridicoEtiqueta", e.getEstadoJuridico().getEtiqueta());
        return m;
    }
}
