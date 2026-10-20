package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.DocumentoExpediente;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.DocumentoExpedienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas de la gestion documental (HU-04).
 *
 * Tres controles antes de aceptar un archivo:
 *   1. Extension dentro de la lista permitida.
 *   2. Tamano por debajo del maximo configurado.
 *   3. Los primeros bytes del archivo corresponden al tipo declarado.
 *
 * El tercero existe porque la extension y el Content-Type los elige
 * quien sube el archivo: renombrar "virus.exe" a "informe.pdf" engana a
 * los dos primeros controles, pero no a los bytes iniciales.
 */
@Service
public class DocumentoService {

    /** Extension -> tipo MIME que se guardara. */
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "pdf",  "application/pdf",
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "png",  "image/png",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final Set<String> CATEGORIAS = Set.of(
            "resolucion", "acta", "informe_social", "informe_medico",
            "documento_identidad", "otro");

    private final DocumentoExpedienteRepository documentos;
    private final AlmacenCifrado almacen;
    private final ExpedienteService expedientes;

    @Value("${app.documentos.max-mb:10}")
    private int maxMb;

    public DocumentoService(DocumentoExpedienteRepository documentos,
                            AlmacenCifrado almacen,
                            ExpedienteService expedientes) {
        this.documentos = documentos;
        this.almacen = almacen;
        this.expedientes = expedientes;
    }

    /** Error de validacion con mensaje listo para mostrar al usuario. */
    public static class DocumentoInvalido extends RuntimeException {
        public DocumentoInvalido(String mensaje) {
            super(mensaje);
        }
    }

    // =====================================================================
    //  Adjuntar
    // =====================================================================

    @Transactional
    public DocumentoExpediente adjuntar(Expediente expediente, MultipartFile archivo,
                                         String categoria, Usuario usuario) {

        if (archivo == null || archivo.isEmpty()) {
            throw new DocumentoInvalido("No se recibió ningún archivo.");
        }

        String nombre = limpiarNombre(archivo.getOriginalFilename());
        String extension = extensionDe(nombre);

        if (!TIPOS_PERMITIDOS.containsKey(extension)) {
            throw new DocumentoInvalido(
                    "Formato no permitido. Se aceptan PDF, JPG, PNG y DOCX.");
        }

        long maximo = (long) maxMb * 1024 * 1024;
        if (archivo.getSize() > maximo) {
            throw new DocumentoInvalido(
                    "El archivo pesa " + mb(archivo.getSize()) + " y el máximo es " + maxMb + " MB.");
        }

        if (!CATEGORIAS.contains(categoria)) {
            throw new DocumentoInvalido("Categoría de documento no válida.");
        }

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (Exception ex) {
            throw new DocumentoInvalido("No se pudo leer el archivo enviado.");
        }

        if (!contenidoCoincide(extension, contenido)) {
            throw new DocumentoInvalido(
                    "El contenido del archivo no corresponde con su extensión ." + extension + ".");
        }

        AlmacenCifrado.ArchivoGuardado guardado;
        try {
            guardado = almacen.guardar(expediente.getId(), contenido);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cifrar y guardar el documento", ex);
        }

        DocumentoExpediente doc = new DocumentoExpediente(
                expediente, nombre, categoria, TIPOS_PERMITIDOS.get(extension),
                archivo.getSize(), guardado.nombreEnDisco(), guardado.ivBase64(),
                guardado.hashSha256(), usuario.getUsername());

        documentos.save(doc);

        // Queda en el historial del expediente (HU-03 + HU-04)
        expedientes.registrar(expediente, TipoEvento.DOCUMENTO,
                "Adjuntó el documento \"" + nombre + "\" (" + etiquetaCategoria(categoria) + ").",
                usuario);

        return doc;
    }

    // =====================================================================
    //  Consultar y descargar
    // =====================================================================

    @Transactional(readOnly = true)
    public List<DocumentoExpediente> listar(Expediente expediente) {
        return documentos.findByExpedienteIdOrderByFechaSubidaDesc(expediente.getId());
    }

    @Transactional(readOnly = true)
    public Optional<DocumentoExpediente> buscar(Expediente expediente, Long idDocumento) {
        return documentos.findByIdAndExpedienteId(idDocumento, expediente.getId());
    }

    /**
     * Devuelve el contenido descifrado y deja constancia de la descarga:
     * en un expediente de un menor, saber quien se llevo una copia es
     * tan importante como saber quien lo modifico.
     */
    @Transactional
    public byte[] descargar(DocumentoExpediente doc, Usuario usuario) {
        byte[] contenido;
        try {
            contenido = almacen.leer(doc.getExpediente().getId(),
                    doc.getArchivoEnDisco(), doc.getIvBase64());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo descifrar el documento", ex);
        }

        expedientes.registrar(doc.getExpediente(), TipoEvento.DOCUMENTO,
                "Descargó el documento \"" + doc.getNombreOriginal() + "\".", usuario);

        return contenido;
    }

    // =====================================================================
    //  Utilidades
    // =====================================================================

    public Map<String, Object> aMapa(DocumentoExpediente d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("nombre", d.getNombreOriginal());
        m.put("categoria", d.getCategoria());
        m.put("categoriaEtiqueta", etiquetaCategoria(d.getCategoria()));
        m.put("tipoMime", d.getTipoMime());
        m.put("tamano", mb(d.getTamanoBytes()));
        m.put("hashSha256", d.getHashSha256());
        m.put("subidoPor", d.getSubidoPor());
        m.put("fechaSubida", d.getFechaSubida().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return m;
    }

    /**
     * Se queda solo con el nombre del archivo: quita rutas y caracteres
     * raros. Sin esto, un nombre como "../../config.txt" podria usarse
     * para escribir fuera de la carpeta prevista.
     */
    private static String limpiarNombre(String original) {
        if (original == null || original.isBlank()) {
            return "documento";
        }
        String base = original.replace("\\", "/");
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ \\-áéíóúÁÉÍÓÚñÑ]", "_").trim();
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }

    private static String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    /** Comprueba los bytes magicos del inicio del archivo. */
    private static boolean contenidoCoincide(String extension, byte[] b) {
        if (b.length < 4) return false;
        return switch (extension) {
            case "pdf"  -> b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46;      // %PDF
            case "png"  -> (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
            case "jpg", "jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
            case "docx" -> b[0] == 0x50 && b[1] == 0x4B;                                      // ZIP (PK)
            default -> false;
        };
    }

    private static String mb(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static String etiquetaCategoria(String c) {
        return switch (c) {
            case "resolucion" -> "Resolución judicial";
            case "acta" -> "Acta";
            case "informe_social" -> "Informe social";
            case "informe_medico" -> "Informe médico";
            case "documento_identidad" -> "Documento de identidad";
            default -> "Otro";
        };
    }
}
