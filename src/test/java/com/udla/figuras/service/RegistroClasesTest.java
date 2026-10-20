package com.udla.figuras.service;

import com.udla.figuras.model.Circulo;
import com.udla.figuras.model.FiguraGeometrica;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.udla.figuras.soporte.PruebaIntegracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistroClasesTest extends PruebaIntegracion {

    @Autowired
    RegistroClases registro;

    @Test
    void elEscaneoRegistraSoloLasFigurasConcretas() {
        assertThat(registro.clases().keySet()).containsExactly("Circulo", "Rectangulo", "Triangulo");
        assertThat(registro.origenDe("Circulo")).isEqualTo(OrigenClase.NATIVA);
        assertThat(registro.origenDe("Triangulo")).isEqualTo(OrigenClase.NATIVA);
    }

    @Test
    void noRegistraClasesAbstractasNiQueNoSeanFigura() {
        assertThatThrownBy(() -> new RegistroClases().registrar(FiguraGeometrica.class, OrigenClase.NATIVA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroClases().registrar(String.class, OrigenClase.GROOVY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaClaseDinamicaNoPuedeReemplazarUnaNativa() {
        assertThatThrownBy(() -> registro.registrar(Circulo.class, OrigenClase.GROOVY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nativa");
    }
}
