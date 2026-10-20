package com.udla.figuras.config;

import com.udla.figuras.service.CompiladorGroovy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, vuelve a registrar las clases Groovy guardadas en {@code figuras.groovy.dir}.
 *
 * <p>Se hace al crear el bean y no con un {@code ApplicationRunner}: los runners se ejecutan cuando el
 * servidor web ya acepta peticiones, y una petición en esa ventana encontraría las clases sin registrar.
 */
@Component
public class CargadorGroovyInicial implements InitializingBean {

    private final CompiladorGroovy compilador;

    public CargadorGroovyInicial(CompiladorGroovy compilador) {
        this.compilador = compilador;
    }

    @Override
    public void afterPropertiesSet() {
        compilador.recargarGuardadas();
    }
}
