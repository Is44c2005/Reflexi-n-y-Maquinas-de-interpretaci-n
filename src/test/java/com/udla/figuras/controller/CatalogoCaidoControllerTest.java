package com.udla.figuras.controller;

import com.udla.figuras.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con el catálogo caído la API responde 502 con un mensaje claro y sigue funcionando. */
@TestPropertySource(properties = {
        "figuras.catalogo.url=http://127.0.0.1:1/no-hay-nadie",
        "figuras.catalogo.timeout-ms=1000"})
class CatalogoCaidoControllerTest extends PruebaIntegracion {

    @Autowired
    MockMvc mvc;

    @Test
    void listarConElCatalogoCaidoDevuelve502() throws Exception {
        mvc.perform(get("/api/clases/remotas"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.mensaje").value(containsString("No se pudo conectar")))
                .andExpect(jsonPath("$.ruta").value("/api/clases/remotas"));
    }

    @Test
    void importarConElCatalogoCaidoDevuelve502YLaAplicacionSigueViva() throws Exception {
        mvc.perform(post("/api/clases/remotas/Pentagono"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje").value(containsString("127.0.0.1:1")));

        // el resto de la API no se ve afectado
        mvc.perform(get("/api/clases")).andExpect(status().isOk());
    }
}
