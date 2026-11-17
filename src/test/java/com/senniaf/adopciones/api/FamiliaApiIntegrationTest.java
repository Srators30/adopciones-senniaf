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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas automatizadas de la API de familias (tarea transversal del Sprint 7).
 *
 * A diferencia de las pruebas unitarias, estas levantan Spring completo:
 * seguridad con JWT, controladores, servicios y base de datos. Cada prueba
 * hace peticiones HTTP reales con MockMvc y revisa el codigo de respuesta.
 *
 * Usan una base H2 en memoria propia (ver properties abajo): no tocan la
 * carpeta data del proyecto y empiezan vacias en cada ejecucion. Los
 * usuarios admin, tecnico1 y juez1 los crea DataInitializer al arrancar.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pruebas_api_familias;DB_CLOSE_DELAY=-1",
        "app.documentos.ruta=./target/documentos-prueba"
})
@AutoConfigureMockMvc
class FamiliaApiIntegrationTest {

    /** Cada familia de prueba necesita un documento distinto (si no, 409). */
    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    private static final String JUSTIFICACION =
            "Se recibio el informe psicologico actualizado de la familia.";

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
        d.put("nombres", "Ana Maria");
        d.put("apellidos", "Prueba Automatica");
        d.put("tipo_documento", "cedula");
        d.put("numero_documento", "8-900-" + SECUENCIA.getAndIncrement());
        d.put("fecha_nacimiento", "1985-04-12");
        d.put("ocupacion", "Docente");
        d.put("telefono", "6000-0000");
        d.put("tiene_segundo", "no");
        d.put("estado_civil", "casada");
        d.put("provincia_residencia", "Panamá");
        d.put("distrito_residencia", "Panamá");
        d.put("direccion", "Calle de prueba, casa 1");
        d.put("tipo_vivienda", "propia");
        d.put("habitaciones", 3);
        d.put("personas_hogar", 2);
        d.put("tiene_hijos", "no");
        d.put("ingreso_rango", "1000_2000");
        d.put("fuente_ingreso", "empleo");
        d.put("modalidad_solicitada", "adopcion");
        d.put("edad_minima", 0);
        d.put("edad_maxima", 5);
        d.put("sexo_preferencia", "indistinto");
        d.put("acepta_hermanos", "si");
        d.put("acepta_necesidades", "no");
        d.put("motivacion", "Queremos dar un hogar estable a un menor.");
        d.put("declaracion_veracidad", true);
        d.put("declaracion_requisitos", true);
        d.put("declaracion_trazabilidad", true);
        return d;
    }

    private ResultActions enviar(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        var peticion = switch (metodo) {
            case "POST" -> post(ruta);
            case "PATCH" -> patch(ruta);
            default -> get(ruta);
        };
        if (token != null) peticion.header("Authorization", token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(peticion);
    }

    /** Registra una familia y devuelve su numero de solicitud. */
    private String registrarFamilia() throws Exception {
        String cuerpo = enviar("POST", "/api/tecnico/familias", tecnico(), datosValidos())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("numeroSolicitud").asText();
    }

    private void pasarAEvaluacion(String numero) throws Exception {
        enviar("PATCH", "/api/tecnico/familias/" + numero + "/estado", tecnico(), Map.of("estado", "EN_EVALUACION"))
                .andExpect(status().isOk());
    }

    private void emitir(String numero, String resultado) throws Exception {
        enviar("POST", "/api/tecnico/familias/" + numero + "/dictamen", tecnico(),
                Map.of("resultado", resultado,
                       "observacion", "La vivienda no cuenta con espacio y no hay red de apoyo."))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------ registro (HU-05)

    @Test
    @DisplayName("HU-05: el Tecnico registra una familia (201) y queda en estado Registrada")
    void registrarFamilia201() throws Exception {
        enviar("POST", "/api/tecnico/familias", tecnico(), datosValidos())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroSolicitud", startsWith("FAM-")))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"));
    }

    @Test
    @DisplayName("HU-05: registrar sin token responde 401 y como Juez responde 403")
    void registrarSinPermiso() throws Exception {
        enviar("POST", "/api/tecnico/familias", null, datosValidos()).andExpect(status().isUnauthorized());
        enviar("POST", "/api/tecnico/familias", juez(), datosValidos()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-05: datos incompletos responden 400 con la lista de campos")
    void registrarDatosInvalidos() throws Exception {
        Map<String, Object> datos = datosValidos();
        datos.remove("nombres");
        enviar("POST", "/api/tecnico/familias", tecnico(), datos)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItem("nombres")));
    }

    @Test
    @DisplayName("HU-05: el mismo documento dos veces responde 409")
    void documentoDuplicado() throws Exception {
        Map<String, Object> datos = datosValidos();
        enviar("POST", "/api/tecnico/familias", tecnico(), datos).andExpect(status().isCreated());
        enviar("POST", "/api/tecnico/familias", tecnico(), datos).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-05: los tres roles consultan la ficha; un numero inexistente responde 404")
    void consultarFicha() throws Exception {
        String numero = registrarFamilia();
        enviar("GET", "/api/familias/" + numero, juez(), null).andExpect(status().isOk());
        enviar("GET", "/api/familias/" + numero, admin(), null).andExpect(status().isOk());
        enviar("GET", "/api/familias/FAM-1999-9999", tecnico(), null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-05: cambiar a un estado inexistente responde 400")
    void estadoInvalido() throws Exception {
        String numero = registrarFamilia();
        enviar("PATCH", "/api/tecnico/familias/" + numero + "/estado", tecnico(), Map.of("estado", "APROBADA"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------- dictamen (HU-06.1)

    @Test
    @DisplayName("HU-06.1: sin En evaluacion responde 409; luego 201; un segundo dictamen 409; el Juez 403")
    void emitirDictamen() throws Exception {
        String numero = registrarFamilia();
        String ruta = "/api/tecnico/familias/" + numero + "/dictamen";
        Map<String, Object> cuerpo = Map.of("resultado", "IDONEA", "observacion", "Cumple los requisitos.");

        enviar("POST", ruta, tecnico(), cuerpo).andExpect(status().isConflict());
        pasarAEvaluacion(numero);
        enviar("POST", ruta, juez(), cuerpo).andExpect(status().isForbidden());
        enviar("POST", ruta, tecnico(), cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaIdoneidad").isNotEmpty());
        enviar("POST", ruta, tecnico(), cuerpo).andExpect(status().isConflict());

        // Sprint 7: el dictamen movio el estado de la solicitud.
        enviar("GET", "/api/familias/" + numero, juez(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("IDONEA"));
    }

    @Test
    @DisplayName("Sprint 7: con dictamen solo se puede cerrar; Idonea no se elige a mano; solo el Administrador reabre")
    void estadosConDictamenYReapertura() throws Exception {
        String numero = registrarFamilia();
        String estado = "/api/tecnico/familias/" + numero + "/estado";
        String reapertura = "/api/admin/familias/" + numero + "/reapertura";
        String motivo = "La familia presento una apelacion con documentos nuevos.";

        enviar("PATCH", estado, tecnico(), Map.of("estado", "IDONEA")).andExpect(status().isBadRequest());
        pasarAEvaluacion(numero);
        emitir(numero, "IDONEA");

        enviar("PATCH", estado, tecnico(), Map.of("estado", "EN_EVALUACION")).andExpect(status().isConflict());
        enviar("PATCH", estado, tecnico(), Map.of("estado", "CERRADA")).andExpect(status().isOk());
        enviar("PATCH", estado, tecnico(), Map.of("estado", "EN_EVALUACION")).andExpect(status().isConflict());

        enviar("POST", reapertura, tecnico(), Map.of("motivo", motivo)).andExpect(status().isForbidden());
        enviar("POST", reapertura, admin(), Map.of("motivo", "Corto")).andExpect(status().isBadRequest());
        enviar("POST", reapertura, admin(), Map.of("motivo", motivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("IDONEA"));
        enviar("POST", reapertura, admin(), Map.of("motivo", motivo)).andExpect(status().isConflict());
    }

    // ------------------------------------------ cambio de dictamen (HU-06.4)

    @Test
    @DisplayName("HU-06.4: solo el Administrador cambia el dictamen; Tecnico y Juez reciben 403")
    void cambioSoloAdministrador() throws Exception {
        String numero = registrarFamilia();
        pasarAEvaluacion(numero);
        emitir(numero, "NO_IDONEA");
        String ruta = "/api/admin/familias/" + numero + "/dictamen/cambio";
        Map<String, Object> cuerpo = Map.of("resultado", "IDONEA", "observacion", "Cumple tras la reevaluacion.",
                                            "justificacion", JUSTIFICACION);

        enviar("POST", ruta, tecnico(), cuerpo).andExpect(status().isForbidden());
        enviar("POST", ruta, juez(), cuerpo).andExpect(status().isForbidden());
        enviar("POST", ruta, admin(), cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("IDONEA"))
                .andExpect(jsonPath("$.justificacionCambio").value(JUSTIFICACION));
    }

    @Test
    @DisplayName("HU-06.4: un cambio sin justificacion responde 400")
    void cambioSinJustificacion() throws Exception {
        String numero = registrarFamilia();
        pasarAEvaluacion(numero);
        emitir(numero, "NO_IDONEA");
        enviar("POST", "/api/admin/familias/" + numero + "/dictamen/cambio", admin(),
                Map.of("resultado", "IDONEA", "observacion", "Cumple tras la reevaluacion."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItem("justificacion")));
    }

    @Test
    @DisplayName("HU-06.4: el historial guarda los dos dictamenes y el anterior conserva su periodo")
    void historialDeDictamenes() throws Exception {
        String numero = registrarFamilia();
        pasarAEvaluacion(numero);
        emitir(numero, "NO_IDONEA");
        enviar("POST", "/api/admin/familias/" + numero + "/dictamen/cambio", admin(),
                Map.of("resultado", "IDONEA", "observacion", "Cumple tras la reevaluacion.",
                       "justificacion", JUSTIFICACION))
                .andExpect(status().isCreated());

        String cuerpo = enviar("GET", "/api/familias/" + numero + "/dictamen", juez(), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(cuerpo);

        assertEquals("IDONEA", r.get("dictamen").get("resultado").asText());
        assertEquals(2, r.get("historial").size());
        JsonNode anterior = r.get("historial").get(1);
        assertEquals("NO_IDONEA", anterior.get("resultado").asText());
        assertEquals(false, anterior.get("vigente").asBoolean());
        assertTrue(!anterior.get("fechaSustitucion").asText().isEmpty());
        assertTrue(r.get("posicionLista").isInt());
    }

    // -------------------------------------------- lista de espera (HU-06.3)

    @Test
    @DisplayName("HU-06.3: sin token responde 401; el Juez la consulta y ve a la familia idonea")
    void listaDeEspera() throws Exception {
        String numero = registrarFamilia();
        pasarAEvaluacion(numero);
        emitir(numero, "IDONEA");

        enviar("GET", "/api/familias/lista-espera", null, null).andExpect(status().isUnauthorized());
        enviar("GET", "/api/familias/lista-espera", juez(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familias[*].numeroSolicitud", hasItem(numero)));
    }

    @Test
    @DisplayName("HU-06.4 c4: si la familia deja de ser idonea, sale de la lista en ese momento")
    void dejaDeSerIdoneaSaleDeLaLista() throws Exception {
        String numero = registrarFamilia();
        pasarAEvaluacion(numero);
        emitir(numero, "IDONEA");
        enviar("POST", "/api/admin/familias/" + numero + "/dictamen/cambio", admin(),
                Map.of("resultado", "NO_IDONEA",
                       "observacion", "Se detecto que la vivienda no cumple con los requisitos minimos.",
                       "justificacion", JUSTIFICACION))
                .andExpect(status().isCreated());

        String cuerpo = enviar("GET", "/api/familias/lista-espera", tecnico(), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode fila : json.readTree(cuerpo).get("familias")) {
            assertTrue(!numero.equals(fila.get("numeroSolicitud").asText()));
        }
    }
}
