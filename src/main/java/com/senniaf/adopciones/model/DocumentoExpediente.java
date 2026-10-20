package com.senniaf.adopciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Metadatos de un documento adjunto al expediente (HU-04).
 *
 * En la base NO se guarda el contenido del archivo: solo la ficha que
 * lo describe y la ruta donde quedo el archivo cifrado. Separarlos
 * mantiene la base liviana y permite que el archivo este protegido en
 * disco con una clave que la base no conoce.
 */
@Entity
@Table(name = "documentos_expediente",
       indexes = @Index(name = "idx_doc_expediente", columnList = "expediente_id"))
public class DocumentoExpediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    /** Nombre con el que lo subio la persona, para mostrarlo y descargarlo. */
    @Column(name = "nombre_original", nullable = false, length = 180)
    private String nombreOriginal;

    /** Para que sirve este documento dentro del expediente. */
    @Column(nullable = false, length = 40)
    private String categoria;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    /**
     * Nombre del archivo cifrado en disco. Es un UUID, nunca el nombre
     * original: asi el sistema de archivos no revela de que trata el
     * documento ni de que menor es.
     */
    @Column(name = "archivo_en_disco", nullable = false, length = 80)
    private String archivoEnDisco;

    /** Vector de inicializacion del cifrado AES-GCM, en Base64. */
    @Column(name = "iv_base64", nullable = false, length = 32)
    private String ivBase64;

    /** SHA-256 del contenido original: permite detectar alteraciones. */
    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "subido_por", nullable = false, length = 60)
    private String subidoPor;

    @Column(name = "fecha_subida", nullable = false)
    private LocalDateTime fechaSubida = LocalDateTime.now();

    protected DocumentoExpediente() {
        // Requerido por JPA
    }

    public DocumentoExpediente(Expediente expediente, String nombreOriginal, String categoria,
                               String tipoMime, long tamanoBytes, String archivoEnDisco,
                               String ivBase64, String hashSha256, String subidoPor) {
        this.expediente = expediente;
        this.nombreOriginal = nombreOriginal;
        this.categoria = categoria;
        this.tipoMime = tipoMime;
        this.tamanoBytes = tamanoBytes;
        this.archivoEnDisco = archivoEnDisco;
        this.ivBase64 = ivBase64;
        this.hashSha256 = hashSha256;
        this.subidoPor = subidoPor;
        this.fechaSubida = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Expediente getExpediente() { return expediente; }
    public String getNombreOriginal() { return nombreOriginal; }
    public String getCategoria() { return categoria; }
    public String getTipoMime() { return tipoMime; }
    public long getTamanoBytes() { return tamanoBytes; }
    public String getArchivoEnDisco() { return archivoEnDisco; }
    public String getIvBase64() { return ivBase64; }
    public String getHashSha256() { return hashSha256; }
    public String getSubidoPor() { return subidoPor; }
    public LocalDateTime getFechaSubida() { return fechaSubida; }
}
