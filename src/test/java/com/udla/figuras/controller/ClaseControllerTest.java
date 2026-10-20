package com.udla.figuras.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.udla.figuras.soporte.PruebaIntegracion;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClaseControllerTest extends PruebaIntegracion {

    @Autowired
    MockMvc mvc;

    @Test
    void listaLasClasesNativasConSuOrigen() throws Exception {
        mvc.perform(get("/api/clases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItems("Circulo", "Rectangulo", "Triangulo")))
                .andExpect(jsonPath("$[0].origen").value("NATIVA"));
    }

    @Test
    void inspeccionaConstructoresCamposYMetodos() throws Exception {
        mvc.perform(get("/api/clases/Triangulo/inspeccion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("com.udla.figuras.model.Triangulo"))
                .andExpect(jsonPath("$.superclase").value("FiguraGeometrica"))
                .andExpect(jsonPath("$.constructores[0].parametros[*].nombre", hasItems("ladoA", "ladoB", "ladoC")))
                .andExpect(jsonPath("$.constructores[0].parametros[0].tipo").value("double"))
                // campos propios y heredados de FiguraGeometrica
                .andExpect(jsonPath("$.campos[*].nombre", hasItems("ladoA", "nombre", "color")))
                .andExpect(jsonPath("$.metodos[*].nombre", hasItems("calcularArea", "esValido", "escalar", "getColor")))
                // sin métodos de Object
                .andExpect(jsonPath("$.metodos[?(@.nombre=='hashCode')]").isEmpty());
    }

    @Test
    void inspeccionDeClaseInexistenteDevuelve404ConJson() throws Exception {
        mvc.perform(get("/api/clases/Dodecaedro/inspeccion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("'Dodecaedro'")))
                .andExpect(jsonPath("$.ruta").value("/api/clases/Dodecaedro/inspeccion"));
    }
}
