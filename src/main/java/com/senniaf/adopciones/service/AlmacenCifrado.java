package com.senniaf.adopciones.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Guarda y recupera archivos CIFRADOS en disco (HU-04: "los documentos
 * se almacenan de forma protegida/cifrada").
 *
 * Como funciona:
 *   - Algoritmo AES-256 en modo GCM. GCM ademas de cifrar autentica: si
 *     alguien edita un byte del archivo, el descifrado falla en vez de
 *     devolver datos corruptos en silencio.
 *   - Cada archivo lleva su propio IV (vector de inicializacion) de 12
 *     bytes, generado al azar. Reutilizar el IV con la misma clave rompe
 *     la seguridad de GCM, por eso nunca se repite.
 *   - El IV no es secreto: se guarda junto a los metadatos en la base.
 *     El secreto es la clave.
 *   - La clave sale de app.documentos.clave pasada por SHA-256, lo que
 *     da los 32 bytes que AES-256 necesita a partir de cualquier texto.
 *
 * LIMITACION CONOCIDA: la clave esta hoy en application.properties, al
 * igual que el secreto del JWT. En un despliegue real ambas tienen que
 * venir de variables de entorno, porque quien tenga el archivo de
 * configuracion y la carpeta data puede leer todos los documentos.
 */
@Component
public class AlmacenCifrado {

    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int TAM_IV = 12;          // 96 bits, lo recomendado para GCM
    private static final int TAM_TAG = 128;        // bits de la etiqueta de autenticacion

    private final SecureRandom azar = new SecureRandom();

    @Value("${app.documentos.ruta:./data/documentos}")
    private String rutaBase;

    @Value("${app.documentos.clave}")
    private String claveConfigurada;

    private SecretKeySpec clave;

    @PostConstruct
    void prepararClave() throws Exception {
        byte[] derivada = MessageDigest.getInstance("SHA-256")
                .digest(claveConfigurada.getBytes(StandardCharsets.UTF_8));
        this.clave = new SecretKeySpec(derivada, "AES");
        Files.createDirectories(Paths.get(rutaBase));
    }

    /** Resultado de guardar: lo que hay que anotar en la base. */
    public record ArchivoGuardado(String nombreEnDisco, String ivBase64, String hashSha256) {
    }

    public ArchivoGuardado guardar(Long expedienteId, byte[] contenido) throws Exception {
        byte[] iv = new byte[TAM_IV];
        azar.nextBytes(iv);

        Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
        cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAM_TAG, iv));
        byte[] cifrado = cifrador.doFinal(contenido);

        Path carpeta = Paths.get(rutaBase, String.valueOf(expedienteId));
        Files.createDirectories(carpeta);

        // El nombre en disco es un UUID: la carpeta no revela de que trata
        // el documento ni de que menor es.
        String nombreEnDisco = java.util.UUID.randomUUID() + ".enc";
        Files.write(carpeta.resolve(nombreEnDisco), cifrado);

        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(contenido));

        return new ArchivoGuardado(nombreEnDisco, Base64.getEncoder().encodeToString(iv), hash);
    }

    public byte[] leer(Long expedienteId, String nombreEnDisco, String ivBase64) throws Exception {
        Path archivo = Paths.get(rutaBase, String.valueOf(expedienteId), nombreEnDisco);
        if (!Files.exists(archivo)) {
            throw new IOException("El archivo cifrado no existe en el almacen");
        }
        byte[] cifrado = Files.readAllBytes(archivo);

        Cipher descifrador = Cipher.getInstance(TRANSFORMACION);
        descifrador.init(Cipher.DECRYPT_MODE, clave,
                new GCMParameterSpec(TAM_TAG, Base64.getDecoder().decode(ivBase64)));
        return descifrador.doFinal(cifrado);
    }

    public void borrar(Long expedienteId, String nombreEnDisco) throws IOException {
        Files.deleteIfExists(Paths.get(rutaBase, String.valueOf(expedienteId), nombreEnDisco));
    }
}
