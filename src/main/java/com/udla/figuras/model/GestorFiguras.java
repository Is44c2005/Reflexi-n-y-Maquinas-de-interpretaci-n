package com.udla.figuras.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mantiene las figuras creadas. Trabaja solo contra la interfaz {@link Figura}: no sabe si
 * una figura es nativa o cargada desde Groovy (polimorfismo).
 */
public class GestorFiguras {

    private final List<Figura> figuras = new CopyOnWriteArrayList<>();

    public void agregar(Figura f) {
        if (f == null) {
            throw new IllegalArgumentException("La figura no puede ser nula");
        }
        figuras.add(f);
    }

    /** Añadido al diseño original para poder borrar por id desde la API. */
    public boolean eliminar(Figura f) {
        return figuras.removeIf(existente -> existente == f);
    }

    public double areaTotal() {
        return figuras.stream().mapToDouble(Figura::calcularArea).sum();
    }

    public void mostrarTodas() {
        figuras.forEach(f -> System.out.println(f.describir()));
    }

    public List<Figura> getFiguras() {
        return List.copyOf(figuras);
    }
}
