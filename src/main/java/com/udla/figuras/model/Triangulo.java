package com.udla.figuras.model;

public class Triangulo extends FiguraGeometrica {

    private double ladoA;
    private double ladoB;
    private double ladoC;

    public Triangulo(double ladoA, double ladoB, double ladoC) {
        super("Triangulo", COLOR_POR_DEFECTO);
        this.ladoA = exigirPositivo("ladoA", ladoA);
        this.ladoB = exigirPositivo("ladoB", ladoB);
        this.ladoC = exigirPositivo("ladoC", ladoC);
        if (!esValido()) {
            throw new IllegalArgumentException("Los lados " + ladoA + ", " + ladoB + " y " + ladoC
                    + " no forman un triángulo válido: la suma de dos lados debe ser mayor que el tercero");
        }
    }

    /** Desigualdad triangular estricta (un triángulo degenerado, como 1-2-3, no es válido). */
    public boolean esValido() {
        return ladoA + ladoB > ladoC && ladoA + ladoC > ladoB && ladoB + ladoC > ladoA;
    }

    @Override
    public double calcularArea() {
        // Fórmula de Herón
        double s = calcularPerimetro() / 2;
        double producto = s * (s - ladoA) * (s - ladoB) * (s - ladoC);
        return Math.sqrt(Math.max(0, producto));
    }

    @Override
    public double calcularPerimetro() {
        return ladoA + ladoB + ladoC;
    }

    @Override
    public void escalar(double factor) {
        exigirFactor(factor);
        this.ladoA *= factor;
        this.ladoB *= factor;
        this.ladoC *= factor;
    }
}
