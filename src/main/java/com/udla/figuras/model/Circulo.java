package com.udla.figuras.model;

public class Circulo extends FiguraGeometrica {

    private double radio;

    public Circulo(double radio) {
        super("Circulo", COLOR_POR_DEFECTO);
        this.radio = exigirPositivo("radio", radio);
    }

    public double getRadio() {
        return radio;
    }

    @Override
    public double calcularArea() {
        return Math.PI * radio * radio;
    }

    @Override
    public double calcularPerimetro() {
        return 2 * Math.PI * radio;
    }

    @Override
    public void escalar(double factor) {
        this.radio *= exigirFactor(factor);
    }
}
