package com.udla.figuras.service;

import com.udla.figuras.dto.InspeccionClase;
import com.udla.figuras.dto.InspeccionClase.CampoInfo;
import com.udla.figuras.dto.InspeccionClase.ConstructorInfo;
import com.udla.figuras.dto.InspeccionClase.MetodoInfo;
import com.udla.figuras.dto.InspeccionClase.ParametroInfo;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Describe una clase registrada usando la API de reflexión (Class, Constructor, Field, Method). */
@Service
public class InspeccionService {

    private final RegistroClases registro;

    public InspeccionService(RegistroClases registro) {
        this.registro = registro;
    }

    public InspeccionClase inspeccionar(String nombre) {
        Class<?> clase = registro.obtener(nombre);

        List<ConstructorInfo> constructores = Arrays.stream(clase.getConstructors())
                .sorted(Comparator.comparingInt(Constructor::getParameterCount))
                .map(c -> new ConstructorInfo(FiguraFactory.firma(c), parametros(c.getParameters())))
                .toList();

        return new InspeccionClase(
                clase.getSimpleName(),
                clase.getName(),
                registro.origenDe(nombre),
                clase.getSuperclass() == null ? null : clase.getSuperclass().getSimpleName(),
                Arrays.stream(clase.getInterfaces()).map(Class::getSimpleName).toList(),
                constructores,
                campos(clase),
                metodos(clase));
    }

    /** Campos de la clase y de sus superclases (nombre y color vienen de FiguraGeometrica). */
    private List<CampoInfo> campos(Class<?> clase) {
        List<CampoInfo> campos = new ArrayList<>();
        for (Class<?> actual = clase; actual != null && actual != Object.class; actual = actual.getSuperclass()) {
            for (Field f : actual.getDeclaredFields()) {
                if (f.isSynthetic() || f.getName().contains("$") || f.getName().equals("metaClass")) {
                    continue; // campos que añade el compilador de Groovy
                }
                campos.add(new CampoInfo(f.getName(), f.getType().getSimpleName(),
                        Modifier.toString(f.getModifiers()), actual.getSimpleName()));
            }
        }
        return campos;
    }

    /** Métodos públicos de la figura, sin los de Object ni los que inyecta Groovy. */
    private List<MetodoInfo> metodos(Class<?> clase) {
        return Arrays.stream(clase.getMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge() && !m.getName().contains("$"))
                .filter(m -> propioDeLaFigura(m.getDeclaringClass()))
                .sorted(Comparator.comparing(Method::getName).thenComparingInt(Method::getParameterCount))
                .map(m -> new MetodoInfo(m.getName(), m.getReturnType().getSimpleName(),
                        parametros(m.getParameters()), Modifier.toString(m.getModifiers()),
                        m.getDeclaringClass().getSimpleName()))
                .toList();
    }

    private boolean propioDeLaFigura(Class<?> declarante) {
        String paquete = declarante.getPackageName();
        return declarante != Object.class
                && !paquete.startsWith("groovy.")
                && !paquete.startsWith("org.codehaus.groovy")
                && !paquete.startsWith("org.apache.groovy");
    }

    private List<ParametroInfo> parametros(Parameter[] parametros) {
        return Arrays.stream(parametros)
                .map(p -> new ParametroInfo(p.getName(), p.getType().getSimpleName()))
                .toList();
    }
}
