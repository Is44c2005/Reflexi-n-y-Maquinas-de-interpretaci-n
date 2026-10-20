package com.udla.figuras.config;

import com.udla.figuras.model.GestorFiguras;
import com.udla.figuras.service.OrigenClase;
import com.udla.figuras.service.RegistroClases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FigurasConfig {

    private static final Logger log = LoggerFactory.getLogger(FigurasConfig.class);

    /** Las clases nativas se registran escaneando el paquete, no con una lista fija. */
    @Bean
    public RegistroClases registroClases(@Value("${figuras.paquete-nativas}") String paquete) {
        RegistroClases registro = new RegistroClases();
        for (Class<?> clase : EscanerFiguras.escanear(paquete, getClass().getClassLoader())) {
            registro.registrar(clase, OrigenClase.NATIVA);
        }
        log.info("Clases nativas registradas: {}", registro.clases().keySet());
        return registro;
    }

    @Bean
    public GestorFiguras gestorFiguras() {
        return new GestorFiguras();
    }
}
