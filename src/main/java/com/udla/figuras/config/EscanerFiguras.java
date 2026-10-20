package com.udla.figuras.config;

import com.udla.figuras.model.Figura;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca en el classpath las clases concretas que implementan {@link Figura}.
 * El proveedor de Spring descarta por defecto interfaces y clases abstractas, así que
 * FiguraGeometrica no aparece.
 */
public final class EscanerFiguras {

    private EscanerFiguras() {
    }

    public static List<Class<?>> escanear(String paquete, ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AssignableTypeFilter(Figura.class));

        List<Class<?>> encontradas = new ArrayList<>();
        for (BeanDefinition definicion : escaner.findCandidateComponents(paquete)) {
            try {
                encontradas.add(ClassUtils.forName(definicion.getBeanClassName(), classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                throw new IllegalStateException("No se pudo cargar la clase " + definicion.getBeanClassName(), e);
            }
        }
        return encontradas;
    }
}
