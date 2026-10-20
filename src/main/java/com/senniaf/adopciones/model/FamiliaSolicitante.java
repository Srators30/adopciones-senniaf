package com.senniaf.adopciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Familia solicitante de adopcion o acogimiento (HU-05).
 *
 * Espeja la estructura de Expediente a proposito: mismo criterio de
 * numeracion, mismo estado administrativo, mismos campos de auditoria.
 * Quien entienda uno entiende el otro.
 *
 * Lo que NO guarda, por minimizacion de datos (Ley 81 de 2019): el
 * monto exacto del ingreso (solo un rango), y nada sobre el resultado
 * de la evaluacion, que pertenece a otra historia de usuario.
 */
@Entity
@Table(name = "familias_solicitantes")
public class FamiliaSolicitante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FAM-2026-0001. Lo arma el servidor con el id de la base. */
    @Column(name = "numero_solicitud", unique = true, length = 20)
    private String numeroSolicitud;

    // ---------------------- 1. Solicitante principal ----------------------
    @Column(length = 80) private String nombres;
    @Column(length = 80) private String apellidos;
    @Column(name = "tipo_documento", length = 40) private String tipoDocumento;
    @Column(name = "numero_documento", length = 40) private String numeroDocumento;
    @Column(name = "fecha_nacimiento") private LocalDate fechaNacimiento;
    @Column(length = 80) private String ocupacion;
    @Column(length = 40) private String telefono;
    @Column(length = 120) private String correo;

    // ---------------------- 2. Segundo solicitante ------------------------
    /** "si" | "no" — una familia puede ser de una sola persona. */
    @Column(name = "tiene_segundo", length = 4) private String tieneSegundo;
    @Column(name = "segundo_nombres", length = 80) private String segundoNombres;
    @Column(name = "segundo_apellidos", length = 80) private String segundoApellidos;
    @Column(name = "segundo_tipo_documento", length = 40) private String segundoTipoDocumento;
    @Column(name = "segundo_numero_documento", length = 40) private String segundoNumeroDocumento;
    @Column(name = "segundo_fecha_nacimiento") private LocalDate segundoFechaNacimiento;
    @Column(name = "segundo_ocupacion", length = 80) private String segundoOcupacion;

    // ---------------------- 3. Hogar y vivienda ---------------------------
    @Column(name = "estado_civil", length = 40) private String estadoCivil;
    @Column(name = "anios_convivencia") private Integer aniosConvivencia;
    @Column(name = "provincia_residencia", length = 60) private String provinciaResidencia;
    @Column(name = "distrito_residencia", length = 60) private String distritoResidencia;
    @Column(name = "corregimiento_residencia", length = 60) private String corregimientoResidencia;
    @Lob @Column(length = 400) private String direccion;
    @Column(name = "tipo_vivienda", length = 40) private String tipoVivienda;
    @Column(name = "habitaciones") private Integer habitaciones;
    @Column(name = "personas_hogar") private Integer personasHogar;
    /** "si" | "no" */
    @Column(name = "tiene_hijos", length = 4) private String tieneHijos;
    @Column(name = "hijos_cantidad") private Integer hijosCantidad;

    // ---------------------- 4. Situacion socioeconomica -------------------
    /** Rango, nunca el monto exacto: para evaluar basta el tramo. */
    @Column(name = "ingreso_rango", length = 40) private String ingresoRango;
    @Column(name = "fuente_ingreso", length = 40) private String fuenteIngreso;

    // ---------------------- 5. Disponibilidad de acogimiento --------------
    @Column(name = "modalidad_solicitada", length = 40) private String modalidadSolicitada;
    @Column(name = "edad_minima") private Integer edadMinima;
    @Column(name = "edad_maxima") private Integer edadMaxima;
    /** "indistinto" | "F" | "M" */
    @Column(name = "sexo_preferencia", length = 20) private String sexoPreferencia;
    @Column(name = "acepta_hermanos", length = 4) private String aceptaHermanos;
    @Column(name = "acepta_necesidades", length = 4) private String aceptaNecesidades;
    @Lob @Column(name = "detalle_aceptacion", length = 600) private String detalleAceptacion;
    @Lob @Column(length = 1000) private String motivacion;

    // ---------------------- 6. Declaraciones ------------------------------
    @Column(name = "declaracion_veracidad") private Boolean declaracionVeracidad;
    @Column(name = "declaracion_requisitos") private Boolean declaracionRequisitos;
    @Column(name = "declaracion_trazabilidad") private Boolean declaracionTrazabilidad;

    // ---------------------- Asignado por el servidor ----------------------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFamilia estado = EstadoFamilia.REGISTRADA;

    @Column(name = "fecha_creacion") private LocalDateTime fechaCreacion;
    @Column(name = "creado_por", length = 60) private String creadoPor;
    @Column(name = "fecha_actualizacion") private LocalDateTime fechaActualizacion;
    @Column(name = "actualizado_por", length = 60) private String actualizadoPor;

    // ---------------------- Derivados -------------------------------------

    public String getNombreCompleto() {
        return ((nombres == null ? "" : nombres) + " " + (apellidos == null ? "" : apellidos)).trim();
    }

    /** Como se identifica la familia en el listado: una o dos personas. */
    public String getNombreFamilia() {
        String principal = getNombreCompleto();
        if (!"si".equals(tieneSegundo)) {
            return principal;
        }
        String segundo = ((segundoNombres == null ? "" : segundoNombres) + " "
                + (segundoApellidos == null ? "" : segundoApellidos)).trim();
        return segundo.isEmpty() ? principal : principal + " y " + segundo;
    }

    // ---------------------- Getters y setters -----------------------------

    public Long getId() { return id; }

    public String getNumeroSolicitud() { return numeroSolicitud; }
    public void setNumeroSolicitud(String v) { this.numeroSolicitud = v; }

    public String getNombres() { return nombres; }
    public void setNombres(String v) { this.nombres = v; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String v) { this.apellidos = v; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String v) { this.tipoDocumento = v; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String v) { this.numeroDocumento = v; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate v) { this.fechaNacimiento = v; }

    public String getOcupacion() { return ocupacion; }
    public void setOcupacion(String v) { this.ocupacion = v; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String v) { this.telefono = v; }

    public String getCorreo() { return correo; }
    public void setCorreo(String v) { this.correo = v; }

    public String getTieneSegundo() { return tieneSegundo; }
    public void setTieneSegundo(String v) { this.tieneSegundo = v; }

    public String getSegundoNombres() { return segundoNombres; }
    public void setSegundoNombres(String v) { this.segundoNombres = v; }

    public String getSegundoApellidos() { return segundoApellidos; }
    public void setSegundoApellidos(String v) { this.segundoApellidos = v; }

    public String getSegundoTipoDocumento() { return segundoTipoDocumento; }
    public void setSegundoTipoDocumento(String v) { this.segundoTipoDocumento = v; }

    public String getSegundoNumeroDocumento() { return segundoNumeroDocumento; }
    public void setSegundoNumeroDocumento(String v) { this.segundoNumeroDocumento = v; }

    public LocalDate getSegundoFechaNacimiento() { return segundoFechaNacimiento; }
    public void setSegundoFechaNacimiento(LocalDate v) { this.segundoFechaNacimiento = v; }

    public String getSegundoOcupacion() { return segundoOcupacion; }
    public void setSegundoOcupacion(String v) { this.segundoOcupacion = v; }

    public String getEstadoCivil() { return estadoCivil; }
    public void setEstadoCivil(String v) { this.estadoCivil = v; }

    public Integer getAniosConvivencia() { return aniosConvivencia; }
    public void setAniosConvivencia(Integer v) { this.aniosConvivencia = v; }

    public String getProvinciaResidencia() { return provinciaResidencia; }
    public void setProvinciaResidencia(String v) { this.provinciaResidencia = v; }

    public String getDistritoResidencia() { return distritoResidencia; }
    public void setDistritoResidencia(String v) { this.distritoResidencia = v; }

    public String getCorregimientoResidencia() { return corregimientoResidencia; }
    public void setCorregimientoResidencia(String v) { this.corregimientoResidencia = v; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String v) { this.direccion = v; }

    public String getTipoVivienda() { return tipoVivienda; }
    public void setTipoVivienda(String v) { this.tipoVivienda = v; }

    public Integer getHabitaciones() { return habitaciones; }
    public void setHabitaciones(Integer v) { this.habitaciones = v; }

    public Integer getPersonasHogar() { return personasHogar; }
    public void setPersonasHogar(Integer v) { this.personasHogar = v; }

    public String getTieneHijos() { return tieneHijos; }
    public void setTieneHijos(String v) { this.tieneHijos = v; }

    public Integer getHijosCantidad() { return hijosCantidad; }
    public void setHijosCantidad(Integer v) { this.hijosCantidad = v; }

    public String getIngresoRango() { return ingresoRango; }
    public void setIngresoRango(String v) { this.ingresoRango = v; }

    public String getFuenteIngreso() { return fuenteIngreso; }
    public void setFuenteIngreso(String v) { this.fuenteIngreso = v; }

    public String getModalidadSolicitada() { return modalidadSolicitada; }
    public void setModalidadSolicitada(String v) { this.modalidadSolicitada = v; }

    public Integer getEdadMinima() { return edadMinima; }
    public void setEdadMinima(Integer v) { this.edadMinima = v; }

    public Integer getEdadMaxima() { return edadMaxima; }
    public void setEdadMaxima(Integer v) { this.edadMaxima = v; }

    public String getSexoPreferencia() { return sexoPreferencia; }
    public void setSexoPreferencia(String v) { this.sexoPreferencia = v; }

    public String getAceptaHermanos() { return aceptaHermanos; }
    public void setAceptaHermanos(String v) { this.aceptaHermanos = v; }

    public String getAceptaNecesidades() { return aceptaNecesidades; }
    public void setAceptaNecesidades(String v) { this.aceptaNecesidades = v; }

    public String getDetalleAceptacion() { return detalleAceptacion; }
    public void setDetalleAceptacion(String v) { this.detalleAceptacion = v; }

    public String getMotivacion() { return motivacion; }
    public void setMotivacion(String v) { this.motivacion = v; }

    public Boolean getDeclaracionVeracidad() { return declaracionVeracidad; }
    public void setDeclaracionVeracidad(Boolean v) { this.declaracionVeracidad = v; }

    public Boolean getDeclaracionRequisitos() { return declaracionRequisitos; }
    public void setDeclaracionRequisitos(Boolean v) { this.declaracionRequisitos = v; }

    public Boolean getDeclaracionTrazabilidad() { return declaracionTrazabilidad; }
    public void setDeclaracionTrazabilidad(Boolean v) { this.declaracionTrazabilidad = v; }

    public EstadoFamilia getEstado() { return estado; }
    public void setEstado(EstadoFamilia v) { this.estado = v; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime v) { this.fechaCreacion = v; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String v) { this.creadoPor = v; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime v) { this.fechaActualizacion = v; }

    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String v) { this.actualizadoPor = v; }
}
