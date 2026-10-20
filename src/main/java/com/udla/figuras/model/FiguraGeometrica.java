package com.udla.figuras.model;

import java.util.Locale;

public abstract class FiguraGeometrica implements Figura, Escalable {

    public static final String COLOR_POR_DEFECTO = "Sin color";

    private final String nombre;
    private final String color;

    protected FiguraGeometrica(String nombre, String color) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la figura no puede estar vacío");
        }
        this.nombre = nombre;
        this.color = (color == null || color.isBlank()) ? COLOR_POR_DEFECTO : color;
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    public String getColor() {
        return color;
    }

    @Override
    public String describir() {
        // Locale.ROOT para que el separador decimal sea siempre punto, sin depender del equipo
        return String.format(Locale.ROOT, "%s (color: %s) - área: %.2f, perímetro: %.2f",
                nombre, color, calcularArea(), calcularPerimetro());
    }

    @Override
    public abstract double calcularArea();

    @Override
    public abstract double calcularPerimetro();

    @Override
    public abstract void escalar(double factor);

    /**
     * Exige que una medida sea un número finito mayor que 0. Se escribe como !(v > 0)
     * para que NaN también sea rechazado.
     */
    protected static double exigirPositivo(String medida, double valor) {
        if (!(valor > 0) || Double.isInfinite(valor)) {
            throw new IllegalArgumentException(
                    "La medida '" + medida + "' debe ser un número mayor que 0 (recibido: " + valor + ")");
        }
        return valor;
    }

    protected static double exigirFactor(double factor) {
        if (!(factor > 0) || Double.isInfinite(factor)) {
            throw new IllegalArgumentException(
                    "El factor de escala debe ser un número mayor que 0 (recibido: " + factor + ")");
        }
        return factor;
    }
}
