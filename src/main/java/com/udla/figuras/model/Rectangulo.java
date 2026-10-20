package com.udla.figuras.model;

public class Rectangulo extends FiguraGeometrica {

    private double base;
    private double altura;

    public Rectangulo(double base, double altura) {
        super("Rectangulo", COLOR_POR_DEFECTO);
        this.base = exigirPositivo("base", base);
        this.altura = exigirPositivo("altura", altura);
    }

    public double getBase() {
        return base;
    }

    public double getAltura() {
        return altura;
    }

    @Override
    public double calcularArea() {
        return base * altura;
    }

    @Override
    public double calcularPerimetro() {
        return 2 * (base + altura);
    }

    @Override
    public void escalar(double factor) {
        exigirFactor(factor);
        this.base *= factor;
        this.altura *= factor;
    }
}
