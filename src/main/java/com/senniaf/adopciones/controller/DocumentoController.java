package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.DocumentoExpediente;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.service.DocumentoService;
import com.senniaf.adopciones.service.ExpedienteService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Documentos del expediente (HU-04).
 *
 * El reparto de rutas repite el criterio del Sprint 3:
 *   - Adjuntar (escribe)  -> /api/tecnico/**   : ADMINISTRADOR y TECNICO.
 *   - Listar y descargar  -> /api/expedientes/**: los tres roles, pero
 *     nadie sin token, porque cae en .anyRequest().authenticated().
 *
 * Asi se cumple "solo usuarios autorizados pueden consultar o descargar
 * documentos" sin tocar SecurityConfig.
 */
@RestController
public class DocumentoController {

    private final ExpedienteService expedientes;
    private final DocumentoService documentos;

    public DocumentoController(ExpedienteService expedientes, DocumentoService documentos) {
        this.expedientes = expedientes;
        this.documentos = documentos;
    }

    // =====================================================================
    //  Adjuntar  (ADMINISTRADOR, TECNICO)
    // =====================================================================

    @PostMapping(path = "/api/tecnico/expedientes/{numero}/documentos",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> adjuntar(@PathVariable String numero,
                                                         @RequestParam("archivo") MultipartFile archivo,
                                                         @RequestParam("categoria") String categoria,
                                                         @AuthenticationPrincipal Usuario usuario) {

        Optional<Expediente> encontrado = expedientes.porNumero(numero);
        if (encontrado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            DocumentoExpediente doc = documentos.adjuntar(encontrado.get(), archivo, categoria, usuario);
            return ResponseEntity.status(HttpStatus.CREATED).body(documentos.aMapa(doc));

        } catch (DocumentoService.DocumentoInvalido ex) {
            // 400: el archivo no cumple las reglas (tipo, tamano o contenido)
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // =====================================================================
    //  Listar y descargar  (los tres roles, con token)
    // =====================================================================

    @GetMapping("/api/expedientes/{numero}/documentos")
    public ResponseEntity<List<Map<String, Object>>> listar(@PathVariable String numero) {
        Optional<Expediente> encontrado = expedientes.porNumero(numero);
        if (encontrado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(documentos.listar(encontrado.get()).stream()
                .map(documentos::aMapa)
                .toList());
    }

    /**
     * Devuelve el archivo descifrado. El navegador no puede mandar el
     * encabezado Authorization en un enlace normal, asi que el frontend
     * lo pide con fetch y arma la descarga desde el blob recibido.
     */
    @GetMapping("/api/expedientes/{numero}/documentos/{id}")
    public ResponseEntity<Resource> descargar(@PathVariable String numero,
                                               @PathVariable Long id,
                                               @AuthenticationPrincipal Usuario usuario) {

        Optional<Expediente> expediente = expedientes.porNumero(numero);
        if (expediente.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Se comprueba que el documento pertenezca a ESTE expediente: de
        // lo contrario, cambiar el id en la URL dejaria ver el documento
        // de otro menor.
        Optional<DocumentoExpediente> doc = documentos.buscar(expediente.get(), id);
        if (doc.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] contenido = documentos.descargar(doc.get(), usuario);

        String nombre = URLEncoder.encode(doc.get().getNombreOriginal(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + nombre)
                .header(HttpHeaders.CONTENT_TYPE, doc.get().getTipoMime())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(contenido.length)
                .body(new ByteArrayResource(contenido));
    }
}
