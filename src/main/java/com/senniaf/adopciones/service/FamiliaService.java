package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.EventoFamilia;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.EventoFamiliaRepository;
import com.senniaf.adopciones.repository.FamiliaRepository;
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
 * Logica de la familia solicitante: registrar, consultar, modificar y
 * dejar rastro (HU-05).
 *
 * Mismo esqueleto que ExpedienteService del Sprint 3. Si algo cambia en
 * uno, conviene revisar el otro.
 */
@Service
public class FamiliaService {

    /**
     * Edad minima del solicitante. Hoy es la mayoria de edad.
     *
     * OJO: si la normativa de adopcion exige una edad minima distinta
     * (varias legislaciones piden 25 anos y una diferencia minima de
     * edad con el menor), este es el unico numero que hay que cambiar.
     * Confirmenlo contra la Ley 46 de 2013 antes de la entrega.
     */
    private static final int EDAD_MINIMA_SOLICITANTE = 18;

    private static final List<String> OBLIGATORIOS = List.of(
            "nombres", "apellidos", "tipo_documento", "numero_documento",
            "fecha_nacimiento", "ocupacion", "telefono",
            "tiene_segundo", "estado_civil",
            "provincia_residencia", "distrito_residencia", "direccion",
            "tipo_vivienda", "habitaciones", "personas_hogar", "tiene_hijos",
            "ingreso_rango", "fuente_ingreso",
            "modalidad_solicitada", "edad_minima", "edad_maxima",
            "sexo_preferencia", "acepta_hermanos", "acepta_necesidades",
            "motivacion");

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FamiliaRepository familias;
    private final EventoFamiliaRepository eventos;

    public FamiliaService(FamiliaRepository familias, EventoFamiliaRepository eventos) {
        this.familias = familias;
        this.eventos = eventos;
    }

    // =====================================================================
    //  Registrar
    // =====================================================================

    @Transactional
    public FamiliaSolicitante crear(Map<String, Object> datos, Usuario usuario) {
        FamiliaSolicitante f = new FamiliaSolicitante();
        aplicarDatos(f, datos);

        f.setEstado(EstadoFamilia.REGISTRADA);
        f.setFechaCreacion(LocalDateTime.now());
        f.setCreadoPor(usuario.getUsername());

        // Igual que el expediente: se guarda primero para que la base
        // asigne el id, y con ese id se arma el numero visible.
        familias.save(f);
        f.setNumeroSolicitud(String.format("FAM-%d-%04d",
                f.getFechaCreacion().getYear(), f.getId()));
        familias.save(f);

        registrar(f, TipoEvento.CREACION,
                "Solicitud registrada con los datos iniciales de la familia.", usuario);
        return f;
    }

    /** Un documento ya registrado casi siempre es un duplicado por error. */
    @Transactional(readOnly = true)
    public boolean documentoYaRegistrado(String numeroDocumento) {
        return numeroDocumento != null && !numeroDocumento.isBlank()
                && familias.existsByNumeroDocumento(numeroDocumento.trim());
    }

    // =====================================================================
    //  Consultar
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<FamiliaSolicitante> buscar(String texto, String estado, String modalidad,
                                            String provincia, int pagina, int tamano) {
        String patron = (texto == null || texto.isBlank())
                ? null
                : "%" + texto.trim().toLowerCase() + "%";

        EstadoFamilia estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            try {
                estadoFiltro = EstadoFamilia.valueOf(estado.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                estadoFiltro = null;   // un estado desconocido no filtra nada
            }
        }

        String modalidadFiltro = (modalidad == null || modalidad.isBlank()) ? null : modalidad.trim();
        String provinciaFiltro = (provincia == null || provincia.isBlank()) ? null : provincia.trim();

        return familias.buscar(patron, estadoFiltro, modalidadFiltro, provinciaFiltro,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 50),
                        Sort.by(Sort.Direction.DESC, "fechaCreacion")));
    }

    @Transactional(readOnly = true)
    public Optional<FamiliaSolicitante> porNumero(String numero) {
        return familias.findByNumeroSolicitud(numero);
    }

    /** Ver la ficha deja constancia: la pantalla lo promete, tiene que cumplirse. */
    @Transactional
    public Optional<FamiliaSolicitante> verDetalle(String numero, Usuario usuario) {
        Optional<FamiliaSolicitante> encontrada = familias.findByNumeroSolicitud(numero);
        encontrada.ifPresent(f -> registrar(f, TipoEvento.CONSULTA,
                "Consulta del detalle de la solicitud.", usuario));
        return encontrada;
    }

    @Transactional(readOnly = true)
    public List<EventoFamilia> historial(FamiliaSolicitante f) {
        return eventos.findByFamiliaIdOrderByFechaHoraDescIdDesc(f.getId());
    }

    // =====================================================================
    //  Modificar
    // =====================================================================

    @Transactional
    public FamiliaSolicitante actualizar(FamiliaSolicitante f, Map<String, Object> datos, Usuario usuario) {
        Map<String, String> antes = instantanea(f);
        aplicarDatos(f, datos);
        Map<String, String> despues = instantanea(f);

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
            return f;   // nada que guardar ni que registrar
        }

        f.setFechaActualizacion(LocalDateTime.now());
        f.setActualizadoPor(usuario.getUsername());
        familias.save(f);

        registrar(f, TipoEvento.MODIFICACION, String.join(" · ", cambios), usuario);
        return f;
    }

    @Transactional
    /**
     * Sprint 7: que cambios de estado se pueden hacer a mano (PATCH .../estado).
     * Devuelve el motivo del rechazo, o null si el cambio se permite.
     *   - Una solicitud cerrada solo la reabre el Administrador, con
     *     motivo (POST /api/admin/familias/{n}/reapertura).
     *   - Una solicitud con dictamen solo puede cerrarse: su estado lo
     *     marca el dictamen, y cambiar el resultado es cambiar el dictamen.
     * Que IDONEA y NO_IDONEA no se elijan a mano lo revisa el controlador
     * (es un dato invalido, 400; estos son conflictos de estado, 409).
     */
    public String rechazoCambioManual(FamiliaSolicitante f, EstadoFamilia nuevo) {
        if (f.getEstado() == EstadoFamilia.CERRADA && nuevo != EstadoFamilia.CERRADA) {
            return "Una solicitud cerrada solo la puede reabrir el Administrador, indicando el motivo.";
        }
        if (f.getEstado().esDeDictamen() && nuevo != EstadoFamilia.CERRADA && nuevo != f.getEstado()) {
            return "La solicitud ya tiene dictamen: su estado lo marca el dictamen y solo puede cerrarse. "
                    + "Para cambiar el resultado, el Administrador cambia el dictamen.";
        }
        return null;
    }

    public FamiliaSolicitante cambiarEstado(FamiliaSolicitante f, EstadoFamilia nuevo, Usuario usuario) {
        EstadoFamilia anterior = f.getEstado();
        if (anterior == nuevo) {
            return f;
        }
        f.setEstado(nuevo);
        f.setFechaActualizacion(LocalDateTime.now());
        f.setActualizadoPor(usuario.getUsername());
        familias.save(f);

        registrar(f, TipoEvento.CAMBIO_ESTADO,
                "Estado: " + anterior.getEtiqueta() + " -> " + nuevo.getEtiqueta(), usuario);
        return f;
    }

    @Transactional
    public void registrar(FamiliaSolicitante f, TipoEvento tipo, String descripcion, Usuario usuario) {
        eventos.save(new EventoFamilia(f, tipo, descripcion,
                usuario.getUsername(), usuario.getRol().name()));
    }

    /** Para ajustes que hace el sistema en nombre de quien tomo la decision original. */
    public void registrar(FamiliaSolicitante f, TipoEvento tipo, String descripcion, String usuario, String rol) {
        eventos.save(new EventoFamilia(f, tipo, descripcion, usuario, rol));
    }

    // =====================================================================
    //  Validacion
    // =====================================================================

    public List<String> validar(Map<String, Object> datos) {
        List<String> malos = new ArrayList<>();

        for (String campo : OBLIGATORIOS) {
            if (vacio(datos.get(campo))) {
                malos.add(campo);
            }
        }
        if (!esVerdadero(datos.get("declaracion_veracidad"))) malos.add("declaracion_veracidad");
        if (!esVerdadero(datos.get("declaracion_requisitos"))) malos.add("declaracion_requisitos");
        if (!esVerdadero(datos.get("declaracion_trazabilidad"))) malos.add("declaracion_trazabilidad");

        // Edad del solicitante principal
        LocalDate nacimiento = fecha(datos.get("fecha_nacimiento"));
        if (!vacio(datos.get("fecha_nacimiento"))) {
            if (nacimiento == null || nacimiento.isAfter(LocalDate.now())
                    || Period.between(nacimiento, LocalDate.now()).getYears() < EDAD_MINIMA_SOLICITANTE) {
                malos.add("fecha_nacimiento");
            }
        }

        // Si declara segundo solicitante, sus datos pasan a ser obligatorios
        if ("si".equals(texto(datos.get("tiene_segundo")))) {
            for (String campo : List.of("segundo_nombres", "segundo_apellidos",
                                        "segundo_tipo_documento", "segundo_numero_documento",
                                        "segundo_fecha_nacimiento")) {
                if (vacio(datos.get(campo))) malos.add(campo);
            }
            LocalDate n2 = fecha(datos.get("segundo_fecha_nacimiento"));
            if (!vacio(datos.get("segundo_fecha_nacimiento"))) {
                if (n2 == null || n2.isAfter(LocalDate.now())
                        || Period.between(n2, LocalDate.now()).getYears() < EDAD_MINIMA_SOLICITANTE) {
                    malos.add("segundo_fecha_nacimiento");
                }
            }
            // Dos solicitantes no pueden ser la misma persona
            String d1 = texto(datos.get("numero_documento"));
            String d2 = texto(datos.get("segundo_numero_documento"));
            if (!d1.isEmpty() && d1.equalsIgnoreCase(d2)) {
                malos.add("segundo_numero_documento");
            }
        }

        if ("si".equals(texto(datos.get("tiene_hijos"))) && vacio(datos.get("hijos_cantidad"))) {
            malos.add("hijos_cantidad");
        }

        // Rango de edad del menor que la familia puede acoger
        Integer min = entero(datos.get("edad_minima"));
        Integer max = entero(datos.get("edad_maxima"));
        if (min != null && (min < 0 || min > 17)) malos.add("edad_minima");
        if (max != null && (max < 0 || max > 17)) malos.add("edad_maxima");
        if (min != null && max != null && min > max) malos.add("edad_maxima");

        // La vivienda tiene que poder alojar a quien ya vive y a quien llegaria
        Integer habitaciones = entero(datos.get("habitaciones"));
        Integer personas = entero(datos.get("personas_hogar"));
        if (habitaciones != null && habitaciones < 1) malos.add("habitaciones");
        if (personas != null && personas < 1) malos.add("personas_hogar");

        // Si acepta necesidades especiales, se pide el detalle: aceptar
        // en abstracto no sirve para emparejar despues.
        if ("si".equals(texto(datos.get("acepta_necesidades")))
                && vacio(datos.get("detalle_aceptacion"))) {
            malos.add("detalle_aceptacion");
        }

        String correo = texto(datos.get("correo"));
        if (!correo.isEmpty() && !correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            malos.add("correo");
        }

        return malos;
    }

    // =====================================================================
    //  Conversiones
    // =====================================================================

    private void aplicarDatos(FamiliaSolicitante f, Map<String, Object> d) {
        f.setNombres(texto(d.get("nombres")));
        f.setApellidos(texto(d.get("apellidos")));
        f.setTipoDocumento(texto(d.get("tipo_documento")));
        f.setNumeroDocumento(texto(d.get("numero_documento")));
        f.setFechaNacimiento(fecha(d.get("fecha_nacimiento")));
        f.setOcupacion(texto(d.get("ocupacion")));
        f.setTelefono(texto(d.get("telefono")));
        f.setCorreo(texto(d.get("correo")));

        f.setTieneSegundo(texto(d.get("tiene_segundo")));
        f.setSegundoNombres(texto(d.get("segundo_nombres")));
        f.setSegundoApellidos(texto(d.get("segundo_apellidos")));
        f.setSegundoTipoDocumento(texto(d.get("segundo_tipo_documento")));
        f.setSegundoNumeroDocumento(texto(d.get("segundo_numero_documento")));
        f.setSegundoFechaNacimiento(fecha(d.get("segundo_fecha_nacimiento")));
        f.setSegundoOcupacion(texto(d.get("segundo_ocupacion")));

        f.setEstadoCivil(texto(d.get("estado_civil")));
        f.setAniosConvivencia(entero(d.get("anios_convivencia")));
        f.setProvinciaResidencia(texto(d.get("provincia_residencia")));
        f.setDistritoResidencia(texto(d.get("distrito_residencia")));
        f.setCorregimientoResidencia(texto(d.get("corregimiento_residencia")));
        f.setDireccion(texto(d.get("direccion")));
        f.setTipoVivienda(texto(d.get("tipo_vivienda")));
        f.setHabitaciones(entero(d.get("habitaciones")));
        f.setPersonasHogar(entero(d.get("personas_hogar")));
        f.setTieneHijos(texto(d.get("tiene_hijos")));
        f.setHijosCantidad(entero(d.get("hijos_cantidad")));

        f.setIngresoRango(texto(d.get("ingreso_rango")));
        f.setFuenteIngreso(texto(d.get("fuente_ingreso")));

        f.setModalidadSolicitada(texto(d.get("modalidad_solicitada")));
        f.setEdadMinima(entero(d.get("edad_minima")));
        f.setEdadMaxima(entero(d.get("edad_maxima")));
        f.setSexoPreferencia(texto(d.get("sexo_preferencia")));
        f.setAceptaHermanos(texto(d.get("acepta_hermanos")));
        f.setAceptaNecesidades(texto(d.get("acepta_necesidades")));
        f.setDetalleAceptacion(texto(d.get("detalle_aceptacion")));
        f.setMotivacion(texto(d.get("motivacion")));

        f.setDeclaracionVeracidad(esVerdadero(d.get("declaracion_veracidad")));
        f.setDeclaracionRequisitos(esVerdadero(d.get("declaracion_requisitos")));
        f.setDeclaracionTrazabilidad(esVerdadero(d.get("declaracion_trazabilidad")));
    }

    /** Foto de los campos editables, para comparar antes y despues. */
    private Map<String, String> instantanea(FamiliaSolicitante f) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nombres", nn(f.getNombres()));
        m.put("apellidos", nn(f.getApellidos()));
        m.put("tipo_documento", nn(f.getTipoDocumento()));
        m.put("numero_documento", nn(f.getNumeroDocumento()));
        m.put("fecha_nacimiento", nn(f.getFechaNacimiento()));
        m.put("ocupacion", nn(f.getOcupacion()));
        m.put("telefono", nn(f.getTelefono()));
        m.put("correo", nn(f.getCorreo()));
        m.put("tiene_segundo", nn(f.getTieneSegundo()));
        m.put("segundo_nombres", nn(f.getSegundoNombres()));
        m.put("segundo_apellidos", nn(f.getSegundoApellidos()));
        m.put("segundo_tipo_documento", nn(f.getSegundoTipoDocumento()));
        m.put("segundo_numero_documento", nn(f.getSegundoNumeroDocumento()));
        m.put("segundo_fecha_nacimiento", nn(f.getSegundoFechaNacimiento()));
        m.put("segundo_ocupacion", nn(f.getSegundoOcupacion()));
        m.put("estado_civil", nn(f.getEstadoCivil()));
        m.put("anios_convivencia", nn(f.getAniosConvivencia()));
        m.put("provincia_residencia", nn(f.getProvinciaResidencia()));
        m.put("distrito_residencia", nn(f.getDistritoResidencia()));
        m.put("corregimiento_residencia", nn(f.getCorregimientoResidencia()));
        m.put("direccion", nn(f.getDireccion()));
        m.put("tipo_vivienda", nn(f.getTipoVivienda()));
        m.put("habitaciones", nn(f.getHabitaciones()));
        m.put("personas_hogar", nn(f.getPersonasHogar()));
        m.put("tiene_hijos", nn(f.getTieneHijos()));
        m.put("hijos_cantidad", nn(f.getHijosCantidad()));
        m.put("ingreso_rango", nn(f.getIngresoRango()));
        m.put("fuente_ingreso", nn(f.getFuenteIngreso()));
        m.put("modalidad_solicitada", nn(f.getModalidadSolicitada()));
        m.put("edad_minima", nn(f.getEdadMinima()));
        m.put("edad_maxima", nn(f.getEdadMaxima()));
        m.put("sexo_preferencia", nn(f.getSexoPreferencia()));
        m.put("acepta_hermanos", nn(f.getAceptaHermanos()));
        m.put("acepta_necesidades", nn(f.getAceptaNecesidades()));
        m.put("detalle_aceptacion", nn(f.getDetalleAceptacion()));
        m.put("motivacion", nn(f.getMotivacion()));
        return m;
    }

    /** Fila del listado. */
    public Map<String, Object> aResumen(FamiliaSolicitante f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numeroSolicitud", f.getNumeroSolicitud());
        m.put("nombreFamilia", f.getNombreFamilia());
        m.put("estado", f.getEstado().name());
        m.put("modalidadSolicitada", nn(f.getModalidadSolicitada()));
        m.put("provinciaResidencia", nn(f.getProvinciaResidencia()));
        m.put("distritoResidencia", nn(f.getDistritoResidencia()));
        m.put("rangoEdad", rangoEdad(f.getEdadMinima(), f.getEdadMaxima()));
        m.put("integrantes", "si".equals(f.getTieneSegundo()) ? 2 : 1);
        m.put("fechaCreacion", f.getFechaCreacion() == null ? "" : f.getFechaCreacion().format(FECHA_HORA));
        m.put("creadoPor", nn(f.getCreadoPor()));
        return m;
    }

    /** Ficha completa. */
    public Map<String, Object> aDetalle(FamiliaSolicitante f) {
        Map<String, Object> m = new LinkedHashMap<>(aResumen(f));
        m.put("nombres", nn(f.getNombres()));
        m.put("apellidos", nn(f.getApellidos()));
        m.put("tipoDocumento", nn(f.getTipoDocumento()));
        m.put("numeroDocumento", nn(f.getNumeroDocumento()));
        m.put("fechaNacimiento", nn(f.getFechaNacimiento()));
        m.put("edad", edad(f.getFechaNacimiento()));
        m.put("ocupacion", nn(f.getOcupacion()));
        m.put("telefono", nn(f.getTelefono()));
        m.put("correo", nn(f.getCorreo()));

        m.put("tieneSegundo", nn(f.getTieneSegundo()));
        m.put("segundoNombres", nn(f.getSegundoNombres()));
        m.put("segundoApellidos", nn(f.getSegundoApellidos()));
        m.put("segundoTipoDocumento", nn(f.getSegundoTipoDocumento()));
        m.put("segundoNumeroDocumento", nn(f.getSegundoNumeroDocumento()));
        m.put("segundoFechaNacimiento", nn(f.getSegundoFechaNacimiento()));
        m.put("segundoEdad", edad(f.getSegundoFechaNacimiento()));
        m.put("segundoOcupacion", nn(f.getSegundoOcupacion()));

        m.put("estadoCivil", nn(f.getEstadoCivil()));
        m.put("aniosConvivencia", nn(f.getAniosConvivencia()));
        m.put("corregimientoResidencia", nn(f.getCorregimientoResidencia()));
        m.put("direccion", nn(f.getDireccion()));
        m.put("tipoVivienda", nn(f.getTipoVivienda()));
        m.put("habitaciones", nn(f.getHabitaciones()));
        m.put("personasHogar", nn(f.getPersonasHogar()));
        m.put("tieneHijos", nn(f.getTieneHijos()));
        m.put("hijosCantidad", nn(f.getHijosCantidad()));

        m.put("ingresoRango", nn(f.getIngresoRango()));
        m.put("fuenteIngreso", nn(f.getFuenteIngreso()));

        m.put("edadMinima", nn(f.getEdadMinima()));
        m.put("edadMaxima", nn(f.getEdadMaxima()));
        m.put("sexoPreferencia", nn(f.getSexoPreferencia()));
        m.put("aceptaHermanos", nn(f.getAceptaHermanos()));
        m.put("aceptaNecesidades", nn(f.getAceptaNecesidades()));
        m.put("detalleAceptacion", nn(f.getDetalleAceptacion()));
        m.put("motivacion", nn(f.getMotivacion()));

        m.put("fechaActualizacion", f.getFechaActualizacion() == null ? "" : f.getFechaActualizacion().format(FECHA_HORA));
        m.put("actualizadoPor", nn(f.getActualizadoPor()));
        return m;
    }

    public Map<String, Object> aEvento(EventoFamilia ev) {
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
        return Period.between(nacimiento, LocalDate.now()).getYears() + " años";
    }

    private static String rangoEdad(Integer min, Integer max) {
        if (min == null && max == null) return "";
        if (min == null) return "hasta " + max + " años";
        if (max == null) return "desde " + min + " años";
        if (min.equals(max)) return min + " años";
        return min + " a " + max + " años";
    }
}
