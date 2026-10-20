package com.udla.figuras.model;

import com.udla.figuras.soporte.Cuadrado;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GestorFigurasTest {

    // ---- 8. Polimorfismo (la parte con figuras Groovy se prueba en la etapa 2) ----

    @Test
    void areaTotalSumaFigurasDeDistintoTipoSinConocerlas() {
        GestorFiguras gestor = new GestorFiguras();
        gestor.agregar(new Circulo(1));
        gestor.agregar(new Rectangulo(2, 3));
        gestor.agregar(new Triangulo(3, 4, 5));
        gestor.agregar(new Cuadrado(2));

        assertThat(gestor.areaTotal()).isCloseTo(Math.PI + 6 + 6 + 4, within(1e-9));
    }

    @Test
    void eliminarQuitaSoloLaFiguraIndicada() {
        GestorFiguras gestor = new GestorFiguras();
        Figura a = new Circulo(1);
        Figura b = new Circulo(1); // igual a 'a' en medidas, pero otra instancia
        gestor.agregar(a);
        gestor.agregar(b);

        assertThat(gestor.eliminar(a)).isTrue();
        assertThat(gestor.getFiguras()).containsExactly(b);
        assertThat(gestor.eliminar(a)).isFalse();
    }

    @Test
    void describirUsaDosDecimalesYPunto() {
        assertThat(new Circulo(5).describir())
                .isEqualTo("Circulo (color: Sin color) - área: 78.54, perímetro: 31.42");
    }
}
