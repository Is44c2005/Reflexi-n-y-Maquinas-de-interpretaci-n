package com.udla.figuras.soporte;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Base de las pruebas con contexto de Spring. Cada contexto nuevo recibe su propia carpeta
 * temporal para {@code figuras.groovy.dir}: así las clases Groovy que un test guarda no se
 * recargan en el contexto de otro ni ensucian el directorio del proyecto.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PruebaIntegracion {

    /**
     * Este método se ejecuta una vez por contexto; el proveedor de la propiedad, en cambio, se evalúa
     * cada vez que alguien la lee. Por eso la carpeta se crea aquí y el proveedor devuelve siempre la misma.
     */
    @DynamicPropertySource
    static void carpetaGroovyTemporal(DynamicPropertyRegistry registro) {
        String carpeta;
        try {
            carpeta = Files.createTempDirectory("figuras-groovy-test").toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        registro.add("figuras.groovy.dir", () -> carpeta);
    }
}
