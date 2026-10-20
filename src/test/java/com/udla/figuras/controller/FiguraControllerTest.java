package com.udla.figuras.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udla.figuras.model.Circulo;
import com.udla.figuras.model.Rectangulo;
import com.udla.figuras.model.Triangulo;
import com.udla.figuras.soporte.PruebaIntegracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El gestor de figuras y las clases Groovy son estado compartido del contexto: se descarta al terminar. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FiguraControllerTest extends PruebaIntegracion {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void vaciarFiguras() throws Exception {
        String respuesta = mvc.perform(get("/api/figuras")).andReturn().getResponse().getContentAsString();
        for (JsonNode figura : json.readTree(respuesta)) {
            mvc.perform(delete("/api/figuras/" + figura.get("id").asLong())).andExpect(status().isNoContent());
        }
    }

    // ---- utilidades ----

    ResultActions crear(String cuerpo) throws Exception {
        return mvc.perform(post("/api/figuras").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    long crearYObtenerId(String cuerpo) throws Exception {
        String respuesta = crear(cuerpo).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    ResultActions escalar(long id, String cuerpo) throws Exception {
        return mvc.perform(post("/api/figuras/" + id + "/escalar").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    void registrarGroovy(String codigo) throws Exception {
        mvc.perform(post("/api/clases/groovy").contentType(MediaType.TEXT_PLAIN).content(codigo))
                .andExpect(status().isCreated());
    }

    static String ejemplo(String nombre) throws IOException {
        try (InputStream in = FiguraControllerTest.class.getResourceAsStream("/ejemplos/" + nombre + ".groovy")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- crear ----

    @Test
    void creaUnCirculoYResponde201ConTodosLosDatos() throws Exception {
        crear("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":5}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.clase").value("Circulo"))
                .andExpect(jsonPath("$.nombre").value("Circulo"))
                .andExpect(jsonPath("$.parametros.radio").value(5))
                .andExpect(jsonPath("$.escala").value(1.0))
                .andExpect(jsonPath("$.area").value(closeTo(78.54, 0.01)))
                .andExpect(jsonPath("$.perimetro").value(closeTo(31.42, 0.01)));
    }

    @Test
    void creaRectanguloYTriangulo() throws Exception {
        crear("{\"clase\":\"Rectangulo\",\"parametros\":{\"base\":4,\"altura\":3}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.area").value(12.0))
                .andExpect(jsonPath("$.perimetro").value(14.0));
        crear("{\"clase\":\"Triangulo\",\"parametros\":{\"ladoA\":3,\"ladoB\":4,\"ladoC\":5}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.area").value(6.0))
                .andExpect(jsonPath("$.perimetro").value(12.0));
    }

    @Test
    void losIdsSonUnicosCrecientesYNoSeReutilizan() throws Exception {
        long a = crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":1}}");
        long b = crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":1}}");
        mvc.perform(delete("/api/figuras/" + b)).andExpect(status().isNoContent());
        long c = crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":1}}");

        assertThat(b).isGreaterThan(a);
        assertThat(c).isGreaterThan(b);
    }

    // ---- errores al crear ----

    @Test
    void claseInexistenteDevuelve404() throws Exception {
        // (no se usa Pentagono: otros tests de esta clase lo registran en el contexto compartido)
        crear("{\"clase\":\"Dodecaedro\",\"parametros\":{\"lado\":2}}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value(containsString("'Dodecaedro' no está registrada")))
                .andExpect(jsonPath("$.ruta").value("/api/figuras"));
    }

    @Test
    void parametroFaltanteDevuelve400() throws Exception {
        crear("{\"clase\":\"Rectangulo\",\"parametros\":{\"base\":4}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("altura")));
        crear("{\"clase\":\"Circulo\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("radio")));
    }

    @Test
    void tipoIncorrectoDevuelve400() throws Exception {
        crear("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":\"mucho\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("Tipo incorrecto")));
        crear("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":[1,2]}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void medidaNegativaOTrianguloInvalidoDevuelve400() throws Exception {
        crear("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":-3}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("mayor que 0")));
        crear("{\"clase\":\"Triangulo\",\"parametros\":{\"ladoA\":1,\"ladoB\":2,\"ladoC\":10}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("triángulo válido")));
    }

    @Test
    void valoresTanGrandesQueElAreaNoEsFinitaSeRechazan() throws Exception {
        crear("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":1e200}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("demasiado grandes")));
        mvc.perform(get("/api/figuras")).andExpect(jsonPath("$", empty()));
    }

    @Test
    void cuerpoInvalidoOSinClaseDevuelve400() throws Exception {
        crear("esto no es json").andExpect(status().isBadRequest());
        crear("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("'clase'")));
        crear("{\"clase\":\"  \"}").andExpect(status().isBadRequest());
    }

    @Test
    void unaCreacionFallidaNoDejaRastro() throws Exception {
        crear("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":-1}}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/figuras")).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/figuras/area-total")).andExpect(jsonPath("$.cantidad").value(0));
    }

    // ---- consultar ----

    @Test
    void listaYConsultaPorId() throws Exception {
        long circulo = crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":1}}");
        long rect = crearYObtenerId("{\"clase\":\"Rectangulo\",\"parametros\":{\"base\":2,\"altura\":3}}");

        mvc.perform(get("/api/figuras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(circulo))
                .andExpect(jsonPath("$[1].id").value(rect));
        mvc.perform(get("/api/figuras/" + rect))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clase").value("Rectangulo"))
                .andExpect(jsonPath("$.area").value(6.0));
    }

    @Test
    void idInexistenteDevuelve404YIdNoNumericoDevuelve400() throws Exception {
        mvc.perform(get("/api/figuras/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe ninguna figura con id 999"));
        mvc.perform(get("/api/figuras/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("'abc'")));
    }

    // ---- escalar ----

    @Test
    void escalarMultiplicaElAreaYAcumulaLaEscala() throws Exception {
        long id = crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":5}}");

        escalar(id, "{\"factor\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escala").value(2.0))
                .andExpect(jsonPath("$.parametros.radio").value(5)) // los de creación no cambian
                .andExpect(jsonPath("$.area").value(closeTo(78.54 * 4, 0.05)))
                .andExpect(jsonPath("$.perimetro").value(closeTo(31.42 * 2, 0.05)));
        escalar(id, "{\"factor\":0.5}")
                .andExpect(jsonPath("$.escala").value(1.0))
                .andExpect(jsonPath("$.area").value(closeTo(78.54, 0.01)));

        mvc.perform(get("/api/figuras/" + id)).andExpect(jsonPath("$.escala").value(1.0));
    }

    @Test
    void escalarConFactorInvalidoDevuelve400YNoCambiaLaFigura() throws Exception {
        long id = crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":5}}");

        escalar(id, "{\"factor\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("mayor que 0")));
        escalar(id, "{\"factor\":-2}").andExpect(status().isBadRequest());
        escalar(id, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("'factor'")));
        escalar(id, "{\"factor\":\"mucho\"}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/figuras/" + id + "/escalar")).andExpect(status().isBadRequest());

        mvc.perform(get("/api/figuras/" + id))
                .andExpect(jsonPath("$.escala").value(1.0))
                .andExpect(jsonPath("$.area").value(closeTo(78.54, 0.01)));
    }

    @Test
    void escalarUnaFiguraInexistenteDevuelve404() throws Exception {
        escalar(12345, "{\"factor\":2}").andExpect(status().isNotFound());
    }

    @Test
    void escalarConUnFactorQueDesbordaSeRechazaYSeRestauraLaFigura() throws Exception {
        long id = crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":5}}");

        escalar(id, "{\"factor\":1e200}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("demasiado grande")));

        mvc.perform(get("/api/figuras/" + id)).andExpect(jsonPath("$.area").value(closeTo(78.54, 0.01)))
                .andExpect(jsonPath("$.escala").value(1.0));
    }

    // ---- eliminar ----

    @Test
    void eliminarQuitaLaFiguraDeLaListaYDelTotal() throws Exception {
        long a = crearYObtenerId("{\"clase\":\"Rectangulo\",\"parametros\":{\"base\":2,\"altura\":3}}");
        long b = crearYObtenerId("{\"clase\":\"Rectangulo\",\"parametros\":{\"base\":1,\"altura\":1}}");

        mvc.perform(delete("/api/figuras/" + a)).andExpect(status().isNoContent());

        mvc.perform(get("/api/figuras/" + a)).andExpect(status().isNotFound());
        mvc.perform(get("/api/figuras")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(b));
        mvc.perform(get("/api/figuras/area-total"))
                .andExpect(jsonPath("$.areaTotal").value(1.0))
                .andExpect(jsonPath("$.cantidad").value(1));
        mvc.perform(delete("/api/figuras/" + a)).andExpect(status().isNotFound());
    }

    // ---- área total y polimorfismo ----

    @Test
    void areaTotalSumaFigurasNativasYGroovyMezcladas() throws Exception {
        registrarGroovy(ejemplo("Pentagono"));
        crearYObtenerId("{\"clase\":\"Circulo\",\"parametros\":{\"radio\":1}}");
        crearYObtenerId("{\"clase\":\"Rectangulo\",\"parametros\":{\"base\":2,\"altura\":3}}");
        crearYObtenerId("{\"clase\":\"Triangulo\",\"parametros\":{\"ladoA\":3,\"ladoB\":4,\"ladoC\":5}}");
        crear("{\"clase\":\"Pentagono\",\"parametros\":{\"lado\":2}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pentagono"))
                .andExpect(jsonPath("$.area").value(closeTo(6.8819, 0.0001)))
                .andExpect(jsonPath("$.perimetro").value(10.0));

        double esperado = new Circulo(1).calcularArea() + new Rectangulo(2, 3).calcularArea()
                + new Triangulo(3, 4, 5).calcularArea() + 5 * 4 / (4 * Math.tan(Math.toRadians(36)));
        String cuerpo = mvc.perform(get("/api/figuras/area-total")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(4))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(cuerpo).get("areaTotal").asDouble()).isCloseTo(esperado, within(1e-9));
    }

    @Test
    void areaTotalSinFigurasEsCero() throws Exception {
        mvc.perform(get("/api/figuras/area-total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaTotal").value(0.0))
                .andExpect(jsonPath("$.cantidad").value(0));
    }

    @Test
    void unaFiguraGroovyEscalableSeEscalaComoUnaNativa() throws Exception {
        registrarGroovy(ejemplo("Hexagono"));
        long id = crearYObtenerId("{\"clase\":\"Hexagono\",\"parametros\":{\"lado\":1}}");

        escalar(id, "{\"factor\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perimetro").value(18.0))
                .andExpect(jsonPath("$.escala").value(3.0));
    }

    @Test
    void unaFiguraGroovyQueSoloImplementaFiguraNoSePuedeEscalar() throws Exception {
        registrarGroovy("""
                import com.udla.figuras.model.Figura
                class Fija implements Figura {
                    Fija() { }
                    double calcularArea() { 10 }
                    double calcularPerimetro() { 14 }
                    String getNombre() { 'Fija' }
                    String describir() { 'Figura fija' }
                }
                """);
        long id = crearYObtenerId("{\"clase\":\"Fija\"}");

        escalar(id, "{\"factor\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("no implementa Escalable")));
        mvc.perform(get("/api/figuras/" + id)).andExpect(jsonPath("$.area").value(10.0));
    }
}
