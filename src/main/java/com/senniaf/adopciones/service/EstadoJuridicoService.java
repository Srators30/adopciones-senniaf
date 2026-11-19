package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.ExpedienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Etapa juridica del menor y reapertura de expedientes (Sprint 8).
 *
 * HU-07.2 y decisiones del PO:
 *   1. Con el expediente Cerrado no se cambia la etapa: primero se reabre.
 *   2. Solo se avanza; no se vuelve a una etapa anterior.
 *   3. Cambiar la etapa exige una observacion de al menos 20 caracteres.
 *   4. Solo el Administrador reabre un expediente cerrado, con motivo.
 *
 * Quien puede llamar a cada metodo lo decide la ruta (SecurityConfig):
 * cambiarEtapa esta bajo /api/juez/** y reabrir bajo /api/admin/**.
 */
@Service
public class EstadoJuridicoService {

    static final int MINIMO_TEXTO = 20;
    static final int MAXIMO_TEXTO = 1000;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** El controlador traduce cada motivo a su codigo HTTP: 404, 400 o 409. */
    public enum Motivo { NO_ENCONTRADO, DATOS_INVALIDOS, CONFLICTO }

    public static class Rechazo extends RuntimeException {
        private final Motivo motivo;
        private final List<String> campos;

        public Rechazo(Motivo motivo, String mensaje, List<String> campos) {
            super(mensaje);
            this.motivo = motivo;
            this.campos = campos == null ? List.of() : List.copyOf(campos);
        }

        public Motivo getMotivo() { return motivo; }
        public List<String> getCampos() { return campos; }
    }

    private final ExpedienteRepository expedientes;
    private final ExpedienteService expedienteService;

    public EstadoJuridicoService(ExpedienteRepository expedientes, ExpedienteService expedienteService) {
        this.expedientes = expedientes;
        this.expedienteService = expedienteService;
    }

    // =====================================================================
    //  HU-07.2 · Cambiar la etapa (solo Juez)
    // =====================================================================

    @Transactional
    public Expediente cambiarEtapa(String numero, Map<String, Object> datos, Usuario usuario) {
        Expediente e = expedientes.buscarParaCambio(numero)
                .orElseThrow(() -> new Rechazo(Motivo.NO_ENCONTRADO,
                        "No existe un expediente con el número " + numero, null));

        // Decision 1: un expediente cerrado no admite cambios.
        if (e.getEstado() == EstadoExpediente.CERRADO) {
            throw new Rechazo(Motivo.CONFLICTO,
                    "El expediente está cerrado: el Administrador debe reabrirlo antes de cambiar la etapa jurídica.",
                    null);
        }

        // Datos: etapa valida y observacion suficiente (decision 3).
        List<String> malos = new ArrayList<>();
        EstadoJuridico nueva = leerEtapa(datos.get("etapa"));
        if (nueva == null) {
            malos.add("etapa");
        }
        String observacion = texto(datos.get("observacion"));
        if (observacion.length() < MINIMO_TEXTO || observacion.length() > MAXIMO_TEXTO) {
            malos.add("observacion");
        }
        if (!malos.isEmpty()) {
            String mensaje = malos.contains("etapa")
                    ? "La etapa indicada no existe."
                    : "El cambio de etapa exige una observación de entre " + MINIMO_TEXTO
                      + " y " + MAXIMO_TEXTO + " caracteres.";
            throw new Rechazo(Motivo.DATOS_INVALIDOS, mensaje, malos);
        }

        // HU-07.2, criterios 2 y 3: solo transiciones hacia adelante.
        EstadoJuridico actual = e.getEstadoJuridico();
        if (actual.esFinal()) {
            throw new Rechazo(Motivo.CONFLICTO,
                    actual.getEtiqueta() + " es una etapa final: ya no admite cambios.", null);
        }
        if (!actual.puedePasarA(nueva)) {
            String permitidas = actual.siguientes().stream()
                    .map(EstadoJuridico::getEtiqueta).collect(Collectors.joining(" o "));
            throw new Rechazo(Motivo.CONFLICTO,
                    "Desde " + actual.getEtiqueta() + " solo se puede pasar a " + permitidas + ".", null);
        }

        LocalDateTime ahora = LocalDateTime.now();
        e.setEstadoJuridico(nueva);
        e.setFechaEstadoJuridico(ahora);
        e.setFechaActualizacion(ahora);
        e.setActualizadoPor(usuario.getUsername());
        expedientes.save(e);

        expedienteService.registrar(e, TipoEvento.ESTADO_JURIDICO,
                recortar("Etapa jurídica: " + actual.getEtiqueta() + " -> " + nueva.getEtiqueta()
                        + ". Observación: " + observacion), usuario);
        return e;
    }

    // =====================================================================
    //  Decision 4 · Reabrir (solo Administrador)
    // =====================================================================

    @Transactional
    public Expediente reabrir(String numero, Map<String, Object> datos, Usuario usuario) {
        Expediente e = expedientes.buscarParaCambio(numero)
                .orElseThrow(() -> new Rechazo(Motivo.NO_ENCONTRADO,
                        "No existe un expediente con el número " + numero, null));

        if (e.getEstado() != EstadoExpediente.CERRADO) {
            throw new Rechazo(Motivo.CONFLICTO, "Solo un expediente cerrado se puede reabrir.", null);
        }
        String motivo = texto(datos.get("motivo"));
        if (motivo.length() < MINIMO_TEXTO || motivo.length() > MAXIMO_TEXTO) {
            throw new Rechazo(Motivo.DATOS_INVALIDOS, "Reabrir un expediente exige un motivo de entre "
                    + MINIMO_TEXTO + " y " + MAXIMO_TEXTO + " caracteres.", List.of("motivo"));
        }

        LocalDateTime cierreAutomatico = e.getFechaCierreAutomatico();
        LocalDateTime ahora = LocalDateTime.now();
        e.setEstado(EstadoExpediente.EN_REVISION);
        e.setFechaCierreAutomatico(null);
        e.setFechaActualizacion(ahora);
        e.setActualizadoPor(usuario.getUsername());
        expedientes.save(e);

        expedienteService.registrar(e, TipoEvento.CAMBIO_ESTADO,
                recortar("Expediente reabierto: Cerrado -> En revisión"
                        + (cierreAutomatico != null
                           ? " (lo había cerrado el sistema el " + cierreAutomatico.format(FECHA_HORA) + ")"
                           : "")
                        + ". Motivo: " + motivo), usuario);
        return e;
    }

    // =====================================================================
    //  Ajuste de datos al arrancar (HU-07.1, criterio 2)
    // =====================================================================

    @Transactional
    public int asignarEtapaInicial() {
        return expedientes.asignarEtapaInicial(EstadoJuridico.EN_PROTECCION);
    }

    // =====================================================================

    private static EstadoJuridico leerEtapa(Object valor) {
        if (valor == null) return null;
        try {
            return EstadoJuridico.valueOf(valor.toString().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String texto(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static String recortar(String s) {
        return s.length() <= 1000 ? s : s.substring(0, 999) + "…";
    }
}
