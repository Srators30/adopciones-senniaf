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
 * Expediente digital del menor (HU-02) ahora persistido en base de datos,
 * que es lo que HU-03 necesita para poder consultarlo despues.
 *
 * Hasta el Sprint 2 el expediente vivia en una lista en memoria y se
 * perdia al reiniciar. Aqui pasa a ser una entidad JPA: Hibernate crea
 * la tabla 'expedientes' y los datos sobreviven.
 *
 * Los campos corresponden uno a uno con los del formulario
 * registro_menor.html. Los cuatro datos que asigna el sistema
 * (numero, estado, fecha de creacion y responsable) nunca llegan del
 * formulario: los pone el servidor.
 */
@Entity
@Table(name = "expedientes")
public class Expediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador unico visible: EXP-2026-0001. Lo asigna el servidor. */
    @Column(name = "numero_expediente", unique = true, length = 20)
    private String numeroExpediente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoExpediente estado = EstadoExpediente.REGISTRADO;

    // ---------------- Sprint 8 · HU-07 estado juridico ----------------
    // La columna admite nulos solo para que Hibernate pueda agregarla a una
    // base que ya tiene expedientes; al arrancar, TareasExpedientes pone
    // En proteccion a los que no tengan etapa (HU-07.1, criterio 2).

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_juridico", length = 30)
    private EstadoJuridico estadoJuridico = EstadoJuridico.EN_PROTECCION;

    /** Cuando cambio la etapa por ultima vez. */
    @Column(name = "fecha_estado_juridico")
    private LocalDateTime fechaEstadoJuridico;

    /**
     * Si el expediente lo cerro el sistema por inactividad en etapa final
     * (decision del PO, Sprint 8), cuando fue. Se vacia al reabrirlo.
     */
    @Column(name = "fecha_cierre_automatico")
    private LocalDateTime fechaCierreAutomatico;

    // ---------------- 1. Datos del menor ----------------
    @Column(nullable = false, length = 80)
    private String nombres;

    @Column(nullable = false, length = 80)
    private String apellidos;

    @Column(length = 1)
    private String sexo;                       // F | M

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "tipo_documento", length = 40)
    private String tipoDocumento;

    @Column(name = "numero_documento", length = 20)
    private String numeroDocumento;

    @Column(name = "provincia_nacimiento", length = 60)
    private String provinciaNacimiento;

    @Column(name = "distrito_nacimiento", length = 60)
    private String distritoNacimiento;

    @Column(name = "corregimiento_nacimiento", length = 60)
    private String corregimientoNacimiento;

    // ------------- 2. Ingreso al sistema de proteccion -------------
    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(name = "modalidad_acogida", length = 40)
    private String modalidadAcogida;

    @Column(name = "nombre_centro", length = 120)
    private String nombreCentro;

    @Column(name = "autoridad_remitente", length = 40)
    private String autoridadRemitente;

    @Column(name = "numero_oficio", length = 40)
    private String numeroOficio;

    @Column(name = "motivo_ingreso", length = 40)
    private String motivoIngreso;

    @Lob
    @Column(length = 500)
    private String observaciones;

    // ---------------- 3. Familia de origen ----------------
    @Column(length = 20)
    private String hermanos;                   // si | no | desconocido

    @Column(name = "hermanos_cantidad")
    private Integer hermanosCantidad;

    @Column(name = "hermanos_referencia", length = 120)
    private String hermanosReferencia;

    @Column(name = "familiar_nombre", length = 120)
    private String familiarNombre;

    @Column(name = "familiar_parentesco", length = 60)
    private String familiarParentesco;

    @Column(name = "familiar_telefono", length = 20)
    private String familiarTelefono;

    // ------------- 4. Salud e identidad cultural (sensible) -------------
    @Column(name = "necesidades_especiales", length = 20)
    private String necesidadesEspeciales;      // si | no | no_evaluado

    @Lob
    @Column(name = "detalle_necesidades", length = 300)
    private String detalleNecesidades;

    @Column(name = "pueblo_indigena", length = 40)
    private String puebloIndigena;

    // ------------- 5. Declaraciones de quien registra -------------
    @Column(name = "declaracion_autorizacion")
    private boolean declaracionAutorizacion;

    @Column(name = "declaracion_trazabilidad")
    private boolean declaracionTrazabilidad;

    // ---------------- Datos que asigna el servidor ----------------
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "creado_por", nullable = false, length = 60)
    private String creadoPor;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "actualizado_por", length = 60)
    private String actualizadoPor;

    public Expediente() {
    }

    /** Nombre completo, util para el listado y la busqueda. */
    public String getNombreCompleto() {
        return (nombres + " " + apellidos).trim();
    }

    // ---------------- getters y setters ----------------
    public Long getId() { return id; }

    public String getNumeroExpediente() { return numeroExpediente; }
    public void setNumeroExpediente(String numeroExpediente) { this.numeroExpediente = numeroExpediente; }

    public EstadoExpediente getEstado() { return estado; }
    public void setEstado(EstadoExpediente estado) { this.estado = estado; }

    public EstadoJuridico getEstadoJuridico() {
        return estadoJuridico == null ? EstadoJuridico.EN_PROTECCION : estadoJuridico;
    }
    public void setEstadoJuridico(EstadoJuridico estadoJuridico) { this.estadoJuridico = estadoJuridico; }

    public LocalDateTime getFechaEstadoJuridico() { return fechaEstadoJuridico; }
    public void setFechaEstadoJuridico(LocalDateTime fechaEstadoJuridico) { this.fechaEstadoJuridico = fechaEstadoJuridico; }

    public LocalDateTime getFechaCierreAutomatico() { return fechaCierreAutomatico; }
    public void setFechaCierreAutomatico(LocalDateTime fechaCierreAutomatico) { this.fechaCierreAutomatico = fechaCierreAutomatico; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getProvinciaNacimiento() { return provinciaNacimiento; }
    public void setProvinciaNacimiento(String provinciaNacimiento) { this.provinciaNacimiento = provinciaNacimiento; }

    public String getDistritoNacimiento() { return distritoNacimiento; }
    public void setDistritoNacimiento(String distritoNacimiento) { this.distritoNacimiento = distritoNacimiento; }

    public String getCorregimientoNacimiento() { return corregimientoNacimiento; }
    public void setCorregimientoNacimiento(String corregimientoNacimiento) { this.corregimientoNacimiento = corregimientoNacimiento; }

    public LocalDate getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(LocalDate fechaIngreso) { this.fechaIngreso = fechaIngreso; }

    public String getModalidadAcogida() { return modalidadAcogida; }
    public void setModalidadAcogida(String modalidadAcogida) { this.modalidadAcogida = modalidadAcogida; }

    public String getNombreCentro() { return nombreCentro; }
    public void setNombreCentro(String nombreCentro) { this.nombreCentro = nombreCentro; }

    public String getAutoridadRemitente() { return autoridadRemitente; }
    public void setAutoridadRemitente(String autoridadRemitente) { this.autoridadRemitente = autoridadRemitente; }

    public String getNumeroOficio() { return numeroOficio; }
    public void setNumeroOficio(String numeroOficio) { this.numeroOficio = numeroOficio; }

    public String getMotivoIngreso() { return motivoIngreso; }
    public void setMotivoIngreso(String motivoIngreso) { this.motivoIngreso = motivoIngreso; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getHermanos() { return hermanos; }
    public void setHermanos(String hermanos) { this.hermanos = hermanos; }

    public Integer getHermanosCantidad() { return hermanosCantidad; }
    public void setHermanosCantidad(Integer hermanosCantidad) { this.hermanosCantidad = hermanosCantidad; }

    public String getHermanosReferencia() { return hermanosReferencia; }
    public void setHermanosReferencia(String hermanosReferencia) { this.hermanosReferencia = hermanosReferencia; }

    public String getFamiliarNombre() { return familiarNombre; }
    public void setFamiliarNombre(String familiarNombre) { this.familiarNombre = familiarNombre; }

    public String getFamiliarParentesco() { return familiarParentesco; }
    public void setFamiliarParentesco(String familiarParentesco) { this.familiarParentesco = familiarParentesco; }

    public String getFamiliarTelefono() { return familiarTelefono; }
    public void setFamiliarTelefono(String familiarTelefono) { this.familiarTelefono = familiarTelefono; }

    public String getNecesidadesEspeciales() { return necesidadesEspeciales; }
    public void setNecesidadesEspeciales(String necesidadesEspeciales) { this.necesidadesEspeciales = necesidadesEspeciales; }

    public String getDetalleNecesidades() { return detalleNecesidades; }
    public void setDetalleNecesidades(String detalleNecesidades) { this.detalleNecesidades = detalleNecesidades; }

    public String getPuebloIndigena() { return puebloIndigena; }
    public void setPuebloIndigena(String puebloIndigena) { this.puebloIndigena = puebloIndigena; }

    public boolean isDeclaracionAutorizacion() { return declaracionAutorizacion; }
    public void setDeclaracionAutorizacion(boolean declaracionAutorizacion) { this.declaracionAutorizacion = declaracionAutorizacion; }

    public boolean isDeclaracionTrazabilidad() { return declaracionTrazabilidad; }
    public void setDeclaracionTrazabilidad(boolean declaracionTrazabilidad) { this.declaracionTrazabilidad = declaracionTrazabilidad; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
}
