package com.udla.figuras.controller;

import com.udla.figuras.model.Figura;
import com.udla.figuras.service.FiguraFactory;
import com.udla.figuras.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registra clases en el contexto compartido, así que se descarta al terminar. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClaseGroovyControllerTest extends PruebaIntegracion {

    @Autowired
    MockMvc mvc;

    @Autowired
    FiguraFactory factory;

    @Value("${figuras.groovy.dir}")
    String carpeta;

    static String ejemplo(String nombre) throws IOException {
        try (InputStream in = ClaseGroovyControllerTest.class.getResourceAsStream("/ejemplos/" + nombre + ".groovy")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void registraPentagonoPorHttpYApareceEnElListadoConSuOrigen() throws Exception {
        mvc.perform(get("/api/clases"))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Pentagono"))));

        mvc.perform(post("/api/clases/groovy").contentType(MediaType.TEXT_PLAIN).content(ejemplo("Pentagono")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pentagono"))
                .andExpect(jsonPath("$.origen").value("GROOVY"));

        mvc.perform(get("/api/clases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='Pentagono')].origen").value("GROOVY"))
                .andExpect(jsonPath("$[?(@.nombre=='Circulo')].origen").value("NATIVA"));

        // La inspección funciona igual que con una nativa
        mvc.perform(get("/api/clases/Pentagono/inspeccion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superclase").value("FiguraGeometrica"))
                .andExpect(jsonPath("$.constructores[0].parametros[0].nombre").value("lado"))
                .andExpect(jsonPath("$.constructores[0].parametros[0].tipo").value("double"))
                .andExpect(jsonPath("$.origen").value("GROOVY"));

        // Y la fábrica ya puede crearla
        Figura pentagono = factory.crear("Pentagono", Map.of("lado", 2));
        assertThat(pentagono.calcularPerimetro()).isEqualTo(10.0);

        // Se guardó para recargarla al arrancar
        assertThat(Path.of(carpeta, "Pentagono.groovy")).exists();
    }

    @Test
    void rechazaElScriptMaliciosoConUn422YNoLoGuarda() throws Exception {
        mvc.perform(post("/api/clases/groovy").contentType(MediaType.TEXT_PLAIN).content(ejemplo("Malicioso")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.mensaje").value(containsString("java.lang.System")))
                .andExpect(jsonPath("$.ruta").value("/api/clases/groovy"));

        mvc.perform(get("/api/clases")).andExpect(jsonPath("$[*].nombre", not(hasItem("Malicioso"))));
        assertThat(Files.exists(Path.of(carpeta, "Malicioso.groovy"))).isFalse();
    }

    @Test
    void codigoQueNoCompilaDevuelve422ConElMotivoSinTrazasInternas() throws Exception {
        mvc.perform(post("/api/clases/groovy").contentType(MediaType.TEXT_PLAIN).content("class Roto extends { ((( "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("No se pudo compilar")))
                .andExpect(jsonPath("$.mensaje").value(not(containsString("org.codehaus"))));
    }

    @Test
    void unaClaseQueNoEsFiguraDevuelve422() throws Exception {
        mvc.perform(post("/api/clases/groovy").contentType(MediaType.TEXT_PLAIN).content("class Otra { }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("debe implementar Figura")));
    }

    @Test
    void elCuerpoDebeSerTextoPlano() throws Exception {
        mvc.perform(post("/api/clases/groovy").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void conservaLosAcentosDelCodigoFuente() throws Exception {
        String codigo = ejemplo("Hexagono"); // contiene "raíz" y "perímetro" en los comentarios
        mvc.perform(post("/api/clases/groovy")
                        .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                        .content(codigo.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated());

        assertThat(Files.readString(Path.of(carpeta, "Hexagono.groovy"), StandardCharsets.UTF_8))
                .contains("perímetro").contains("raíz");
    }
}
