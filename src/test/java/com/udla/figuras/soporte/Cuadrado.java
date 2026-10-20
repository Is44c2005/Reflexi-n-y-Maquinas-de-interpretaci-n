package com.udla.figuras.soporte;

import com.udla.figuras.model.Figura;

/**
 * Figura de prueba que no es ninguna de las nativas. Vive fuera del paquete escaneado
 * (com.udla.figuras.model) para no registrarse como figura nativa.
 */
public class Cuadrado implements Figura {

    private final double lado;

    public Cuadrado(double lado) {
        this.lado = lado;
    }

    @Override
    public double calcularArea() {
        return lado * lado;
    }

    @Override
    public double calcularPerimetro() {
        return 4 * lado;
    }

    @Override
    public String getNombre() {
        return "Cuadrado";
    }

    @Override
    public String describir() {
        return "Cuadrado de lado " + lado;
    }
}
