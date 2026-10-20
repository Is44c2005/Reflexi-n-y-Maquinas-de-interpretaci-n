package com.udla.figuras.service;

import com.udla.figuras.model.Figura;
import com.udla.figuras.service.excepcion.ClaseNoRegistradaException;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de las clases de figura disponibles, indexadas por nombre simple ("Circulo").
 * Es lo que permite a {@link FiguraFactory} resolver una clase sin un switch por tipo:
 * las nativas se añaden al arrancar y las dinámicas (Groovy) en tiempo de ejecución.
 */
public class RegistroClases {

    private final Map<String, Class<?>> clases = new ConcurrentHashMap<>();
    private final Map<String, OrigenClase> origenes = new ConcurrentHashMap<>();

    public synchronized void registrar(Class<?> clase, OrigenClase origen) {
        if (!Figura.class.isAssignableFrom(clase)
                || clase.isInterface() || Modifier.isAbstract(clase.getModifiers())) {
            throw new IllegalArgumentException("La clase " + clase.getName()
                    + " debe ser una clase concreta que implemente Figura");
        }
        String nombre = clase.getSimpleName();
        if (origenes.get(nombre) == OrigenClase.NATIVA && origen != OrigenClase.NATIVA) {
            throw new IllegalArgumentException("Ya existe una clase nativa llamada '" + nombre
                    + "'; una clase dinámica no puede reemplazarla");
        }
        // Una clase dinámica con el mismo nombre que otra dinámica sí la reemplaza (redefinición)
        clases.put(nombre, clase);
        origenes.put(nombre, origen);
    }

    public Class<?> obtener(String nombre) {
        Class<?> clase = nombre == null ? null : clases.get(nombre);
        if (clase == null) {
            throw new ClaseNoRegistradaException("La clase '" + nombre + "' no está registrada. "
                    + "Clases disponibles: " + new TreeMap<>(clases).keySet());
        }
        return clase;
    }

    public boolean contiene(String nombre) {
        return nombre != null && clases.containsKey(nombre);
    }

    public OrigenClase origenDe(String nombre) {
        obtener(nombre);
        return origenes.get(nombre);
    }

    /** Copia ordenada por nombre, segura para recorrer. */
    public Map<String, Class<?>> clases() {
        return new TreeMap<>(clases);
    }
}
