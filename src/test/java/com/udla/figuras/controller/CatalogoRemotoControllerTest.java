package com.udla.figuras.controller;

import com.udla.figuras.model.Figura;
import com.udla.figuras.service.FiguraFactory;
import com.udla.figuras.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de extremo a extremo con el catálogo simulado de la propia aplicación: el cliente hace una
 * petición HTTP real al puerto en el que arrancó el servidor. La URL se resuelve en cada petición,
 * por eso puede usar el puerto aleatorio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "figuras.catalogo.url=http://localhost:${local.server.port}/mock-catalogo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogoRemotoControllerTest extends PruebaIntegracion {

    @Autowired
    MockMvc mvc;

    @Autowired
    FiguraFactory factory;

    @Value("${figuras.groovy.dir}")
    String carpeta;

    @Test
    void elCatalogoSimuladoOfreceLosEjemplosSalvoElMalicioso() throws Exception {
        mvc.perform(get("/mock-catalogo/figuras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasItems("Hexagono", "Pentagono")))
                .andExpect(jsonPath("$").value(not(hasItems("Malicioso"))));

        mvc.perform(get("/mock-catalogo/figuras/Pentagono"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pentagono"))
                .andExpect(jsonPath("$.codigoGroovy").value(containsString("class Pentagono")));

        mvc.perform(get("/mock-catalogo/figuras/Malicioso")).andExpect(status().isNotFound());
        mvc.perform(get("/mock-catalogo/figuras/..%2Fapplication")).andExpect(status().isNotFound());
    }

    @Test
    void descargaCompilaYUsaUnaFiguraDelCatalogoPorHttp() throws Exception {
        mvc.perform(get("/api/clases/remotas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasItems("Hexagono", "Pentagono")));

        mvc.perform(post("/api/clases/remotas/Hexagono"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Hexagono"))
                .andExpect(jsonPath("$.origen").value("REMOTA"));

        mvc.perform(get("/api/clases"))
                .andExpect(jsonPath("$[?(@.nombre=='Hexagono')].origen").value("REMOTA"));
        mvc.perform(get("/api/clases/Hexagono/inspeccion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constructores[0].parametros[0].nombre").value("lado"));

        Figura hexagono = factory.crear("Hexagono", Map.of("lado", 2));
        assertThat(hexagono.calcularPerimetro()).isEqualTo(12.0);
        assertThat(hexagono.calcularArea()).isCloseTo(3 * Math.sqrt(3) / 2 * 4, org.assertj.core.api.Assertions.within(1e-9));

        assertThat(Files.readString(Path.of(carpeta, "Hexagono.groovy"))).startsWith("// origen: REMOTA");
    }

    @Test
    void figuraQueNoEstaEnElCatalogoDevuelve404() throws Exception {
        mvc.perform(post("/api/clases/remotas/Dodecaedro"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value(containsString("'Dodecaedro'")));
    }

    @Test
    void elNombreMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/api/clases/remotas/9abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("no es válido")));
    }
}
