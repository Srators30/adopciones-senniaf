package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.EventoExpediente;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.EventoExpedienteRepository;
import com.senniaf.adopciones.repository.ExpedienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Logica del expediente: crear, consultar, modificar y dejar rastro.
 *
 * Lo que el Sprint 3 agrega sobre el Sprint 2:
 *   - El expediente se guarda en base de datos, no en memoria.
 *   - Cada operacion escribe una linea en el historial (HU-03).
 *   - Al modificar se compara campo por campo para poder decir que
 *     cambio exactamente, no solo que "hubo un cambio".
 */
@Service
public class ExpedienteService {

    /** Campos que el formulario exige siempre. */
    private static final List<String> OBLIGATORIOS = List.of(
            "nombres", "apellidos", "sexo", "fecha_nacimiento", "tipo_documento",
            "provincia_nacimiento", "distrito_nacimiento", "fecha_ingreso",
            "modalidad_acogida", "autoridad_remitente", "motivo_ingreso",
            "hermanos", "necesidades_especiales");

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExpedienteRepository expedientes;
    private final EventoExpedienteRepository eventos;

    public ExpedienteService(ExpedienteRepository expedientes, EventoExpedienteRepository eventos) {
        this.expedientes = expedientes;
        this.eventos = eventos;
    }

    // =====================================================================
    //  Crear (HU-02, ahora persistente)
    // =====================================================================

    @Transactional
    public Expediente crear(Map<String, Object> datos, Usuario usuario) {
        Expediente e = new Expediente();
        aplicarDatos(e, datos);

        e.setEstado(EstadoExpediente.REGISTRADO);
        e.setFechaCreacion(LocalDateTime.now());
        // HU-07.1, criterio 2: todo expediente nuevo arranca En proteccion.
        e.setEstadoJuridico(EstadoJuridico.EN_PROTECCION);
        e.setFechaEstadoJuridico(e.getFechaCreacion());
        e.setCreadoPor(usuario.getUsername());

        // Se guarda primero para que la base asigne el id, y con ese id se
        // arma el numero visible: asi no hay dos expedientes con el mismo.
        expedientes.save(e);
        e.setNumeroExpediente(String.format("EXP-%d-%04d",
                e.getFechaCreacion().getYear(), e.getId()));
        expedientes.save(e);

        registrar(e, TipoEvento.CREACION,
                "Expediente creado con los datos iniciales del menor.", usuario);
        return e;
    }

    // =====================================================================
    //  Consultar (HU-03)
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<Expediente> buscar(String texto, String estado, String modalidad,
                                    int pagina, int tamano) {
        return buscar(texto, estado, modalidad, null, pagina, tamano);
    }

    /** Sprint 8: tambien filtra por etapa juridica (HU-07.1, criterio 3). */
    @Transactional(readOnly = true)
    public Page<Expediente> buscar(String texto, String estado, String modalidad, String etapa,
                                    int pagina, int tamano) {
        String patron = (texto == null || texto.isBlank())
                ? null
                : "%" + texto.trim().toLowerCase() + "%";

        EstadoExpediente estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            try {
                estadoFiltro = EstadoExpediente.valueOf(estado.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                estadoFiltro = null;   // un estado desconocido no filtra nada
            }
        }

        String modalidadFiltro = (modalidad == null || modalidad.isBlank()) ? null : modalidad.trim();

        EstadoJuridico etapaFiltro = null;
        if (etapa != null && !etapa.isBlank()) {
            try {
                etapaFiltro = EstadoJuridico.valueOf(etapa.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                etapaFiltro = null;   // una etapa desconocida no filtra nada
            }
        }

        return expedientes.buscar(patron, estadoFiltro, modalidadFiltro, etapaFiltro,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 50),
                        Sort.by(Sort.Direction.DESC, "fechaCreacion")));
    }

    @Transactional(readOnly = true)
    public Optional<Expediente> porNumero(String numero) {
        return expedientes.findByNumeroExpediente(numero);
    }

    /**
     * Detalle del expediente. Deja constancia de la consulta: la pantalla
     * le promete al usuario que "cada consulta queda registrada", asi que
     * tiene que ser verdad.
     */
    @Transactional
    public Optional<Expediente> verDetalle(String numero, Usuario usuario) {
        Optional<Expediente> encontrado = expedientes.findByNumeroExpediente(numero);
        encontrado.ifPresent(e -> registrar(e, TipoEvento.CONSULTA,
                "Consulta del detalle del expediente.", usuario));
        return encontrado;
    }

    @Transactional(readOnly = true)
    public List<EventoExpediente> historial(Expediente e) {
        return eventos.findByExpedienteIdOrderByFechaHoraDescIdDesc(e.getId());
    }

    // =====================================================================
    //  Modificar (HU-03: "el historial permite identificar los cambios")
    // =====================================================================

    @Transactional
    public Expediente actualizar(Expediente e, Map<String, Object> datos, Usuario usuario) {
        Map<String, String> antes = instantanea(e);
        aplicarDatos(e, datos);
        Map<String, String> despues = instantanea(e);

        List<String> cambios = new ArrayList<>();
        for (String campo : antes.keySet()) {
            String a = antes.get(campo);
            String d = despues.get(campo);
            if (!a.equals(d)) {
                cambios.add(campo + ": \"" + (a.isEmpty() ? "vacio" : a)
                        + "\" -> \"" + (d.isEmpty() ? "vacio" : d) + "\"");
            }
        }

        if (cambios.isEmpty()) {
            return e;   // nada que guardar ni que registrar
        }

        e.setFechaActualizacion(LocalDateTime.now());
        e.setActualizadoPor(usuario.getUsername());
        expedientes.save(e);

        registrar(e, TipoEvento.MODIFICACION, String.join(" · ", cambios), usuario);
        return e;
    }

    @Transactional
    public Expediente cambiarEstado(Expediente e, EstadoExpediente nuevo, Usuario usuario) {
        EstadoExpediente anterior = e.getEstado();
        if (anterior == nuevo) {
            return e;
        }
        e.setEstado(nuevo);
        e.setFechaActualizacion(LocalDateTime.now());
        e.setActualizadoPor(usuario.getUsername());
        expedientes.save(e);

        registrar(e, TipoEvento.CAMBIO_ESTADO,
                "Estado: " + anterior + " -> " + nuevo, usuario);
        return e;
    }

    /** Punto de entrada para que otros modulos (HU-04) escriban en el historial. */
    @Transactional
    public void registrar(Expediente e, TipoEvento tipo, String descripcion, Usuario usuario) {
        eventos.save(new EventoExpediente(e, tipo, descripcion,
                usuario.getUsername(), usuario.getRol().name()));
    }

    /** Para lo que hace el propio sistema, como el cierre automatico (Sprint 8). */
    public void registrar(Expediente e, TipoEvento tipo, String descripcion, String usuario, String rol) {
        eventos.save(new EventoExpediente(e, tipo, descripcion, usuario, rol));
    }

    // =====================================================================
    //  Validacion (la del navegador ayuda; esta es la que decide)
    // =====================================================================

    public List<String> validar(Map<String, Object> datos) {
        List<String> malos = new ArrayList<>();

        for (String campo : OBLIGATORIOS) {
            if (vacio(datos.get(campo))) {
                malos.add(campo);
            }
        }
        if (!esVerdadero(datos.get("declaracion_autorizacion"))) {
            malos.add("declaracion_autorizacion");
        }
        if (!esVerdadero(datos.get("declaracion_trazabilidad"))) {
            malos.add("declaracion_trazabilidad");
        }

        // Menor de 18: no basta con el min/max del <input type=date>.
        LocalDate nacimiento = fecha(datos.get("fecha_nacimiento"));
        if (!vacio(datos.get("fecha_nacimiento"))) {
            LocalDate hoy = LocalDate.now();
            if (nacimiento == null || nacimiento.isAfter(hoy)
                    || Period.between(nacimiento, hoy).getYears() >= 18) {
                malos.add("fecha_nacimiento");
            }
        }

        // El ingreso no puede ser anterior al nacimiento ni futuro.
        LocalDate ingreso = fecha(datos.get("fecha_ingreso"));
        if (!vacio(datos.get("fecha_ingreso"))) {
            if (ingreso == null || ingreso.isAfter(LocalDate.now())
                    || (nacimiento != null && ingreso.isBefore(nacimiento))) {
                malos.add("fecha_ingreso");
            }
        }

        // Campos obligatorios solo cuando aplican, igual que en el formulario.
        if (!"sin_documento".equals(texto(datos.get("tipo_documento")))
                && vacio(datos.get("numero_documento"))) {
            malos.add("numero_documento");
        }
        if ("si".equals(texto(datos.get("hermanos"))) && vacio(datos.get("hermanos_cantidad"))) {
            malos.add("hermanos_cantidad");
        }
        if ("si".equals(texto(datos.get("necesidades_especiales")))
                && vacio(datos.get("detalle_necesidades"))) {
            malos.add("detalle_necesidades");
        }

        return malos;
    }

    // =====================================================================
    //  Conversiones entre el formulario y la entidad
    // =====================================================================

    private void aplicarDatos(Expediente e, Map<String, Object> d) {
        e.setNombres(texto(d.get("nombres")));
        e.setApellidos(texto(d.get("apellidos")));
        e.setSexo(texto(d.get("sexo")));
        e.setFechaNacimiento(fecha(d.get("fecha_nacimiento")));
        e.setTipoDocumento(texto(d.get("tipo_documento")));
        e.setNumeroDocumento(texto(d.get("numero_documento")));
        e.setProvinciaNacimiento(texto(d.get("provincia_nacimiento")));
        e.setDistritoNacimiento(texto(d.get("distrito_nacimiento")));
        e.setCorregimientoNacimiento(texto(d.get("corregimiento_nacimiento")));

        e.setFechaIngreso(fecha(d.get("fecha_ingreso")));
        e.setModalidadAcogida(texto(d.get("modalidad_acogida")));
        e.setNombreCentro(texto(d.get("nombre_centro")));
        e.setAutoridadRemitente(texto(d.get("autoridad_remitente")));
        e.setNumeroOficio(texto(d.get("numero_oficio")));
        e.setMotivoIngreso(texto(d.get("motivo_ingreso")));
        e.setObservaciones(texto(d.get("observaciones")));

        e.setHermanos(texto(d.get("hermanos")));
        e.setHermanosCantidad(entero(d.get("hermanos_cantidad")));
        e.setHermanosReferencia(texto(d.get("hermanos_referencia")));
        e.setFamiliarNombre(texto(d.get("familiar_nombre")));
        e.setFamiliarParentesco(texto(d.get("familiar_parentesco")));
        e.setFamiliarTelefono(texto(d.get("familiar_telefono")));

        e.setNecesidadesEspeciales(texto(d.get("necesidades_especiales")));
        e.setDetalleNecesidades(texto(d.get("detalle_necesidades")));
        e.setPuebloIndigena(texto(d.get("pueblo_indigena")));

        e.setDeclaracionAutorizacion(esVerdadero(d.get("declaracion_autorizacion")));
        e.setDeclaracionTrazabilidad(esVerdadero(d.get("declaracion_trazabilidad")));
    }

    /** Foto de los campos editables, para comparar antes y despues. */
    private Map<String, String> instantanea(Expediente e) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nombres", nn(e.getNombres()));
        m.put("apellidos", nn(e.getApellidos()));
        m.put("sexo", nn(e.getSexo()));
        m.put("fecha_nacimiento", nn(e.getFechaNacimiento()));
        m.put("tipo_documento", nn(e.getTipoDocumento()));
        m.put("numero_documento", nn(e.getNumeroDocumento()));
        m.put("provincia_nacimiento", nn(e.getProvinciaNacimiento()));
        m.put("distrito_nacimiento", nn(e.getDistritoNacimiento()));
        m.put("corregimiento_nacimiento", nn(e.getCorregimientoNacimiento()));
        m.put("fecha_ingreso", nn(e.getFechaIngreso()));
        m.put("modalidad_acogida", nn(e.getModalidadAcogida()));
        m.put("nombre_centro", nn(e.getNombreCentro()));
        m.put("autoridad_remitente", nn(e.getAutoridadRemitente()));
        m.put("numero_oficio", nn(e.getNumeroOficio()));
        m.put("motivo_ingreso", nn(e.getMotivoIngreso()));
        m.put("observaciones", nn(e.getObservaciones()));
        m.put("hermanos", nn(e.getHermanos()));
        m.put("hermanos_cantidad", nn(e.getHermanosCantidad()));
        m.put("hermanos_referencia", nn(e.getHermanosReferencia()));
        m.put("familiar_nombre", nn(e.getFamiliarNombre()));
        m.put("familiar_parentesco", nn(e.getFamiliarParentesco()));
        m.put("familiar_telefono", nn(e.getFamiliarTelefono()));
        m.put("necesidades_especiales", nn(e.getNecesidadesEspeciales()));
        m.put("detalle_necesidades", nn(e.getDetalleNecesidades()));
        m.put("pueblo_indigena", nn(e.getPuebloIndigena()));
        return m;
    }

    /** Fila del listado: lo minimo para identificar el caso. */
    public Map<String, Object> aResumen(Expediente e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numeroExpediente", e.getNumeroExpediente());
        m.put("nombreCompleto", e.getNombreCompleto());
        m.put("sexo", nn(e.getSexo()));
        m.put("edad", edad(e.getFechaNacimiento()));
        m.put("estado", e.getEstado().name());
        // Sprint 8 · HU-07.1: la etapa juridica va en el listado y en la ficha.
        m.put("estadoJuridico", e.getEstadoJuridico().name());
        m.put("estadoJuridicoEtiqueta", e.getEstadoJuridico().getEtiqueta());
        m.put("modalidadAcogida", nn(e.getModalidadAcogida()));
        m.put("fechaIngreso", nn(e.getFechaIngreso()));
        m.put("fechaCreacion", e.getFechaCreacion() == null ? "" : e.getFechaCreacion().format(FECHA_HORA));
        m.put("creadoPor", nn(e.getCreadoPor()));
        return m;
    }

    /** Detalle completo para la ficha. */
    public Map<String, Object> aDetalle(Expediente e) {
        Map<String, Object> m = new LinkedHashMap<>(aResumen(e));
        m.put("fechaNacimiento", nn(e.getFechaNacimiento()));
        m.put("tipoDocumento", nn(e.getTipoDocumento()));
        m.put("numeroDocumento", nn(e.getNumeroDocumento()));
        m.put("provinciaNacimiento", nn(e.getProvinciaNacimiento()));
        m.put("distritoNacimiento", nn(e.getDistritoNacimiento()));
        m.put("corregimientoNacimiento", nn(e.getCorregimientoNacimiento()));
        m.put("nombreCentro", nn(e.getNombreCentro()));
        m.put("autoridadRemitente", nn(e.getAutoridadRemitente()));
        m.put("numeroOficio", nn(e.getNumeroOficio()));
        m.put("motivoIngreso", nn(e.getMotivoIngreso()));
        m.put("observaciones", nn(e.getObservaciones()));
        m.put("hermanos", nn(e.getHermanos()));
        m.put("hermanosCantidad", nn(e.getHermanosCantidad()));
        m.put("hermanosReferencia", nn(e.getHermanosReferencia()));
        m.put("familiarNombre", nn(e.getFamiliarNombre()));
        m.put("familiarParentesco", nn(e.getFamiliarParentesco()));
        m.put("familiarTelefono", nn(e.getFamiliarTelefono()));
        m.put("necesidadesEspeciales", nn(e.getNecesidadesEspeciales()));
        m.put("detalleNecesidades", nn(e.getDetalleNecesidades()));
        m.put("puebloIndigena", nn(e.getPuebloIndigena()));
        m.put("fechaActualizacion", e.getFechaActualizacion() == null ? "" : e.getFechaActualizacion().format(FECHA_HORA));
        m.put("actualizadoPor", nn(e.getActualizadoPor()));
        // Sprint 8
        m.put("fechaEstadoJuridico", e.getFechaEstadoJuridico() == null ? "" : e.getFechaEstadoJuridico().format(FECHA_HORA));
        m.put("etapaFinal", e.getEstadoJuridico().esFinal());
        m.put("siguientesEtapas", e.getEstadoJuridico().siguientes().stream().map(Enum::name).toList());
        m.put("fechaCierreAutomatico", e.getFechaCierreAutomatico() == null ? "" : e.getFechaCierreAutomatico().format(FECHA_HORA));
        return m;
    }

    public Map<String, Object> aEvento(EventoExpediente ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", ev.getTipo().name());
        m.put("descripcion", nn(ev.getDescripcion()));
        m.put("usuario", ev.getUsuario());
        m.put("rol", ev.getRol());
        m.put("fechaHora", ev.getFechaHora().format(FECHA_HORA));
        return m;
    }

    // ---------------------------- utilidades ----------------------------

    private static String texto(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static boolean vacio(Object v) {
        return texto(v).isEmpty();
    }

    private static String nn(Object v) {
        return v == null ? "" : v.toString();
    }

    private static boolean esVerdadero(Object v) {
        if (v instanceof Boolean b) return b;
        String s = texto(v);
        return s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on");
    }

    private static LocalDate fecha(Object v) {
        String s = texto(v);
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer entero(Object v) {
        String s = texto(v);
        if (s.isEmpty()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String edad(LocalDate nacimiento) {
        if (nacimiento == null) return "";
        Period p = Period.between(nacimiento, LocalDate.now());
        if (p.getYears() >= 1) {
            return p.getYears() + (p.getYears() == 1 ? " año" : " años");
        }
        return p.getMonths() + (p.getMonths() == 1 ? " mes" : " meses");
    }
}
