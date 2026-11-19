package com.senniaf.adopciones.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senniaf.adopciones.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas automatizadas de la API de expedientes y documentos (tarea
 * transversal del Sprint 8), mas la etapa juridica (HU-07.1 y HU-07.2), la
 * reapertura y los cierres automaticos.
 *
 * Igual que FamiliaApiIntegrationTest: Spring completo, peticiones HTTP con
 * MockMvc y una base H2 en memoria propia, con las mismas propiedades para
 * que las clases compartan un solo arranque de Spring.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pruebas_api_familias;DB_CLOSE_DELAY=-1",
        "app.documentos.ruta=./target/documentos-prueba"
})
@AutoConfigureMockMvc
class ExpedienteApiIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);
    private static final String OBSERVACION = "Resolución del Juzgado de Niñez y Adolescencia de Panamá.";
    private static final String MOTIVO = "Falta inscribir la sentencia en el Registro Civil.";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    // ---------------------------------------------------------- utilidades

    private String token(String usuario, String clave) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(usuario, clave))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(cuerpo).get("token").asText();
    }

    private String admin() throws Exception { return token("admin", "Admin123!"); }
    private String tecnico() throws Exception { return token("tecnico1", "Tecnico123!"); }
    private String juez() throws Exception { return token("juez1", "Juez123!"); }

    private Map<String, Object> datosValidos() {
        Map<String, Object> d = new HashMap<>();
        d.put("nombres", "Ana Lucía");
        d.put("apellidos", "Prueba Automática");
        d.put("sexo", "F");
        d.put("fecha_nacimiento", "2019-05-10");
        d.put("tipo_documento", "certificado_nacimiento");
        d.put("numero_documento", "8-1000-" + SECUENCIA.getAndIncrement());
        d.put("provincia_nacimiento", "Panamá");
        d.put("distrito_nacimiento", "Panamá");
        d.put("fecha_ingreso", "2025-01-15");
        d.put("modalidad_acogida", "centro_acogimiento");
        d.put("autoridad_remitente", "juzgado_ninez");
        d.put("motivo_ingreso", "negligencia");
        d.put("hermanos", "no");
        d.put("necesidades_especiales", "no");
        d.put("declaracion_autorizacion", true);
        d.put("declaracion_trazabilidad", true);
        return d;
    }

    private ResultActions enviar(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        var peticion = switch (metodo) {
            case "POST" -> post(ruta);
            case "PATCH" -> patch(ruta);
            case "PUT" -> put(ruta);
            default -> get(ruta);
        };
        if (token != null) peticion.header("Authorization", token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(peticion);
    }

    private String crearExpediente() throws Exception {
        String cuerpo = enviar("POST", "/api/tecnico/expedientes", tecnico(), datosValidos())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("numeroExpediente").asText();
    }

    private ResultActions etapa(String numero, String token, String etapa, String observacion) throws Exception {
        return enviar("PATCH", "/api/juez/expedientes/" + numero + "/estado-juridico", token,
                Map.of("etapa", etapa, "observacion", observacion));
    }

    /** Lleva un expediente hasta Adoptado. */
    private void llevarAAdoptado(String numero) throws Exception {
        String j = juez();
        etapa(numero, j, "EN_PROCESO_JUDICIAL", OBSERVACION).andExpect(status().isOk());
        etapa(numero, j, "DECLARADO_ADOPTABLE", OBSERVACION).andExpect(status().isOk());
        etapa(numero, j, "ADOPTADO", OBSERVACION).andExpect(status().isOk());
    }

    // ------------------------------------------------ expedientes (HU-02, HU-03)

    @Test
    @DisplayName("HU-02 y HU-07.1: el Tecnico crea un expediente (201) y arranca En proteccion")
    void crearExpediente201() throws Exception {
        enviar("POST", "/api/tecnico/expedientes", tecnico(), datosValidos())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroExpediente", startsWith("EXP-")))
                .andExpect(jsonPath("$.estado").value("REGISTRADO"))
                .andExpect(jsonPath("$.estadoJuridico").value("EN_PROTECCION"));
    }

    @Test
    @DisplayName("HU-02: crear sin token responde 401, como Juez 403 y con datos incompletos 400")
    void crearSinPermisoODatos() throws Exception {
        enviar("POST", "/api/tecnico/expedientes", null, datosValidos()).andExpect(status().isUnauthorized());
        enviar("POST", "/api/tecnico/expedientes", juez(), datosValidos()).andExpect(status().isForbidden());
        Map<String, Object> datos = datosValidos();
        datos.remove("nombres");
        enviar("POST", "/api/tecnico/expedientes", tecnico(), datos)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItem("nombres")));
    }

    @Test
    @DisplayName("HU-03: los tres roles consultan la ficha, queda la consulta en el historial; inexistente 404")
    void consultarFicha() throws Exception {
        String numero = crearExpediente();
        enviar("GET", "/api/expedientes/" + numero, juez(), null).andExpect(status().isOk());
        enviar("GET", "/api/expedientes/" + numero, admin(), null).andExpect(status().isOk());
        enviar("GET", "/api/expedientes/EXP-1999-9999", tecnico(), null).andExpect(status().isNotFound());
        enviar("GET", "/api/expedientes/" + numero + "/historial", tecnico(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipo", hasItem("CONSULTA")));
    }

    @Test
    @DisplayName("HU-03: un expediente cerrado no admite modificaciones (409)")
    void cerradoNoSeModifica() throws Exception {
        String numero = crearExpediente();
        enviar("PATCH", "/api/tecnico/expedientes/" + numero + "/estado", tecnico(), Map.of("estado", "CERRADO"))
                .andExpect(status().isOk());
        enviar("PUT", "/api/tecnico/expedientes/" + numero, tecnico(), datosValidos())
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------- documentos (HU-04)

    @Test
    @DisplayName("HU-04: se adjunta un PDF (201), los tres roles lo listan y la descarga devuelve el mismo archivo")
    void adjuntarYDescargar() throws Exception {
        String numero = crearExpediente();
        byte[] pdf = "%PDF-1.4\nDocumento de prueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "resolucion.pdf", "application/pdf", pdf);

        String cuerpo = mvc.perform(multipart("/api/tecnico/expedientes/" + numero + "/documentos")
                        .file(archivo).param("categoria", "resolucion")
                        .header("Authorization", tecnico()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(cuerpo).get("id").asLong();

        enviar("GET", "/api/expedientes/" + numero + "/documentos", juez(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("resolucion.pdf"));

        byte[] descargado = mvc.perform(get("/api/expedientes/" + numero + "/documentos/" + id)
                        .header("Authorization", juez()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(pdf, descargado);
    }

    @Test
    @DisplayName("HU-04: un archivo que no es PDF aunque diga .pdf responde 400; el Juez no adjunta (403)")
    void documentoInvalidoOSinPermiso() throws Exception {
        String numero = crearExpediente();
        MockMultipartFile falso = new MockMultipartFile("archivo", "falso.pdf", "application/pdf",
                "esto no es un pdf".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/tecnico/expedientes/" + numero + "/documentos")
                        .file(falso).param("categoria", "otro").header("Authorization", tecnico()))
                .andExpect(status().isBadRequest());
        MockMultipartFile pdf = new MockMultipartFile("archivo", "a.pdf", "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/api/tecnico/expedientes/" + numero + "/documentos")
                        .file(pdf).param("categoria", "otro").header("Authorization", juez()))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------- etapa juridica (HU-07)

    @Test
    @DisplayName("HU-07.2: solo el Juez cambia la etapa; Tecnico y Administrador 403, sin token 401")
    void soloElJuezCambiaLaEtapa() throws Exception {
        String numero = crearExpediente();
        etapa(numero, null, "EN_PROCESO_JUDICIAL", OBSERVACION).andExpect(status().isUnauthorized());
        etapa(numero, tecnico(), "EN_PROCESO_JUDICIAL", OBSERVACION).andExpect(status().isForbidden());
        etapa(numero, admin(), "EN_PROCESO_JUDICIAL", OBSERVACION).andExpect(status().isForbidden());
        etapa(numero, juez(), "EN_PROCESO_JUDICIAL", OBSERVACION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoJuridico").value("EN_PROCESO_JUDICIAL"));

        enviar("GET", "/api/expedientes/" + numero + "/historial", tecnico(), null)
                .andExpect(jsonPath("$[*].tipo", hasItem("ESTADO_JURIDICO")));
    }

    @Test
    @DisplayName("HU-07.2: saltarse etapas 409, etapa inexistente u observacion corta 400")
    void reglasDeLaEtapa() throws Exception {
        String numero = crearExpediente();
        String j = juez();
        etapa(numero, j, "ADOPTADO", OBSERVACION).andExpect(status().isConflict());
        etapa(numero, j, "LIBERADO", OBSERVACION)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItem("etapa")));
        etapa(numero, j, "EN_PROCESO_JUDICIAL", "Corta.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItem("observacion")));
    }

    @Test
    @DisplayName("HU-07.2 c3: Adoptado es final; HU-07.1: el listado filtra por etapa")
    void etapaFinalYFiltro() throws Exception {
        String numero = crearExpediente();
        llevarAAdoptado(numero);
        etapa(numero, juez(), "REINTEGRADO", OBSERVACION).andExpect(status().isConflict());

        enviar("GET", "/api/expedientes?etapa=ADOPTADO&tamano=50", tecnico(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].numeroExpediente", hasItem(numero)));
        String cuerpo = enviar("GET", "/api/expedientes?etapa=EN_PROTECCION&tamano=50", tecnico(), null)
                .andReturn().getResponse().getContentAsString();
        for (JsonNode fila : json.readTree(cuerpo).get("contenido")) {
            assertTrue(!numero.equals(fila.get("numeroExpediente").asText()));
        }
    }

    // ------------------------------------------- reapertura y cierres (PO)

    @Test
    @DisplayName("Decisiones del PO: con el expediente cerrado el Juez no cambia la etapa; solo el Administrador reabre")
    void cerradoYReapertura() throws Exception {
        String numero = crearExpediente();
        String reapertura = "/api/admin/expedientes/" + numero + "/reapertura";
        enviar("PATCH", "/api/tecnico/expedientes/" + numero + "/estado", tecnico(), Map.of("estado", "CERRADO"))
                .andExpect(status().isOk());

        etapa(numero, juez(), "EN_PROCESO_JUDICIAL", OBSERVACION).andExpect(status().isConflict());
        enviar("PATCH", "/api/tecnico/expedientes/" + numero + "/estado", tecnico(), Map.of("estado", "EN_REVISION"))
                .andExpect(status().isConflict());
        enviar("POST", reapertura, tecnico(), Map.of("motivo", MOTIVO)).andExpect(status().isForbidden());
        enviar("POST", reapertura, admin(), Map.of("motivo", "Corto")).andExpect(status().isBadRequest());
        enviar("POST", reapertura, admin(), Map.of("motivo", MOTIVO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
        enviar("POST", reapertura, admin(), Map.of("motivo", MOTIVO)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Cierres automaticos: un Adoptado aparece por cerrarse con 7 dias; el Juez no ve la seccion")
    void seccionCierresAutomaticos() throws Exception {
        String numero = crearExpediente();
        llevarAAdoptado(numero);

        String cuerpo = enviar("GET", "/api/tecnico/expedientes/cierres-automaticos", tecnico(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plazoDias").value(7))
                .andReturn().getResponse().getContentAsString();
        boolean aparece = false;
        for (JsonNode fila : json.readTree(cuerpo).get("porCerrarse")) {
            if (numero.equals(fila.get("numeroExpediente").asText())) {
                aparece = true;
                assertTrue(fila.get("diasRestantes").asInt() == 7);
            }
        }
        assertTrue(aparece);

        enviar("GET", "/api/expedientes/" + numero, juez(), null)
                .andExpect(jsonPath("$.cierreAutomatico.diasRestantes").value(7));
        enviar("GET", "/api/tecnico/expedientes/cierres-automaticos", juez(), null)
                .andExpect(status().isForbidden());
    }
}
