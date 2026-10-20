package com.udla.figuras.service;

import com.udla.figuras.model.Circulo;
import com.udla.figuras.model.Figura;
import com.udla.figuras.model.Rectangulo;
import com.udla.figuras.model.Triangulo;
import com.udla.figuras.service.excepcion.ClaseNoRegistradaException;
import com.udla.figuras.service.excepcion.ParametrosInvalidosException;
import com.udla.figuras.service.excepcion.ValidacionFiguraException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.udla.figuras.soporte.PruebaIntegracion;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FiguraFactoryTest extends PruebaIntegracion {

    @Autowired
    FiguraFactory factory;

    // ---- 1. Instanciación por reflexión de figuras nativas ----

    @Test
    void creaCirculoConRadio5() {
        Figura figura = factory.crear("Circulo", Map.of("radio", 5));

        assertThat(figura).isInstanceOf(Circulo.class);
        assertThat(figura.calcularArea()).isCloseTo(78.54, within(0.01));
        assertThat(figura.calcularPerimetro()).isCloseTo(31.42, within(0.01));
    }

    @Test
    void areaYPerimetroSeCalculanPorReflexion() {
        Figura figura = factory.crear("Circulo", Map.of("radio", 5));

        assertThat(factory.calcularArea(figura)).isCloseTo(78.54, within(0.01));
        assertThat(factory.calcularPerimetro(figura)).isCloseTo(31.42, within(0.01));
    }

    @Test
    void convierteTiposCompatibles() {
        // String -> double y Integer -> double
        Figura figura = factory.crear("Rectangulo", Map.of("base", "4.5", "altura", 2));

        assertThat(figura.calcularArea()).isCloseTo(9.0, within(1e-9));
    }

    // ---- 2. Rectángulo y Triángulo ----

    @Test
    void creaRectangulo() {
        Figura figura = factory.crear("Rectangulo", Map.of("base", 4, "altura", 3));

        assertThat(figura).isInstanceOf(Rectangulo.class);
        assertThat(figura.calcularArea()).isCloseTo(12.0, within(1e-9));
        assertThat(figura.calcularPerimetro()).isCloseTo(14.0, within(1e-9));
    }

    @Test
    void creaTrianguloRectanguloDe345() {
        Figura figura = factory.crear("Triangulo", Map.of("ladoA", 3, "ladoB", 4, "ladoC", 5));

        assertThat(figura).isInstanceOf(Triangulo.class);
        assertThat(figura.calcularArea()).isCloseTo(6.0, within(1e-9));
        assertThat(figura.calcularPerimetro()).isCloseTo(12.0, within(1e-9));
    }

    // ---- 3. Errores de parámetros y validación ----

    @Test
    void faltaUnParametro() {
        assertThatThrownBy(() -> factory.crear("Rectangulo", Map.of("base", 4)))
                .isInstanceOf(ParametrosInvalidosException.class)
                .hasMessageContaining("Faltan parámetros")
                .hasMessageContaining("altura");
    }

    @Test
    void faltanTodosLosParametros() {
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of()))
                .isInstanceOf(ParametrosInvalidosException.class)
                .hasMessageContaining("radio");
        assertThatThrownBy(() -> factory.crear("Circulo", null))
                .isInstanceOf(ParametrosInvalidosException.class);
    }

    @Test
    void parametroDeTipoIncorrecto() {
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", "abc")))
                .isInstanceOf(ParametrosInvalidosException.class)
                .hasMessageContaining("Tipo incorrecto")
                .hasMessageContaining("radio")
                .hasMessageContaining("abc");
    }

    @Test
    void parametroNuloParaTipoPrimitivo() {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("radio", null);

        assertThatThrownBy(() -> factory.crear("Circulo", parametros))
                .isInstanceOf(ParametrosInvalidosException.class)
                .hasMessageContaining("radio");
    }

    @Test
    void unaListaOUnObjetoNoSonUnValorValidoParaUnParametroSimple() {
        // Spring convertiría [1, 2] en 1 sin avisar
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", java.util.List.of(1, 2))))
                .isInstanceOf(ParametrosInvalidosException.class)
                .hasMessageContaining("Tipo incorrecto")
                .hasMessageContaining("radio");
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", java.util.List.of(5))))
                .isInstanceOf(ParametrosInvalidosException.class);
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", Map.of("valor", 5))))
                .isInstanceOf(ParametrosInvalidosException.class);
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", new double[]{5})))
                .isInstanceOf(ParametrosInvalidosException.class);
    }

    @Test
    void parametroNoReconocido() {
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", 5, "diametro", 10)))
                .isInstanceOf(ParametrosInvalidosException.class)
                .hasMessageContaining("no reconocidos")
                .hasMessageContaining("diametro");
    }

    @Test
    void medidaNegativaMuestraLaCausaRealDelConstructor() {
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", -3)))
                .isInstanceOf(ValidacionFiguraException.class)
                .hasMessageContaining("radio")
                .hasMessageContaining("mayor que 0")
                // la causa es la IllegalArgumentException del constructor, no InvocationTargetException
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void medidaCeroYNanSonRechazadas() {
        assertThatThrownBy(() -> factory.crear("Rectangulo", Map.of("base", 0, "altura", 2)))
                .isInstanceOf(ValidacionFiguraException.class)
                .hasMessageContaining("base");
        assertThatThrownBy(() -> factory.crear("Circulo", Map.of("radio", "NaN")))
                .isInstanceOf(ValidacionFiguraException.class);
    }

    @Test
    void trianguloInvalido() {
        assertThatThrownBy(() -> factory.crear("Triangulo", Map.of("ladoA", 1, "ladoB", 2, "ladoC", 10)))
                .isInstanceOf(ValidacionFiguraException.class)
                .hasMessageContaining("triángulo válido");
        // degenerado: 1 + 2 == 3
        assertThatThrownBy(() -> factory.crear("Triangulo", Map.of("ladoA", 1, "ladoB", 2, "ladoC", 3)))
                .isInstanceOf(ValidacionFiguraException.class);
    }

    // ---- 4. Clase inexistente ----

    @Test
    void claseInexistente() {
        assertThatThrownBy(() -> factory.crear("Pentagono", Map.of("lado", 2)))
                .isInstanceOf(ClaseNoRegistradaException.class)
                .hasMessageContaining("'Pentagono' no está registrada")
                .hasMessageContaining("Circulo");
    }

    @Test
    void laClaseAbstractaNoEstaDisponible() {
        assertThatThrownBy(() -> factory.crear("FiguraGeometrica", Map.of()))
                .isInstanceOf(ClaseNoRegistradaException.class);
    }

    // ---- escalar ----

    @Test
    void escalarModificaLasMedidas() {
        Figura figura = factory.crear("Circulo", Map.of("radio", 5));
        ((Circulo) figura).escalar(2);

        assertThat(((Circulo) figura).getRadio()).isEqualTo(10.0);
        assertThatThrownBy(() -> ((Circulo) figura).escalar(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factor");
    }
}
