package com.udla.figuras.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udla.figuras.dto.ClaseInfo;
import com.udla.figuras.model.Figura;
import com.udla.figuras.service.excepcion.CatalogoRemotoException;
import com.udla.figuras.service.excepcion.CodigoGroovyInvalidoException;
import com.udla.figuras.service.excepcion.FiguraRemotaNoEncontradaException;
import com.udla.figuras.soporte.ServidorCatalogoFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static com.udla.figuras.service.CompiladorGroovyTest.ejemplo;
import static com.udla.figuras.soporte.ServidorCatalogoFalso.figuraJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * El código que llega del catálogo es tan poco confiable como el que llega por POST. Estos tests
 * usan un servidor falso para controlar exactamente lo que "devuelve el catálogo".
 */
class CatalogoRemotoServiceTest {

    @TempDir
    Path carpeta;

    ServidorCatalogoFalso servidor;
    RegistroClases registro;
    FiguraFactory factory;
    CatalogoRemotoService servicio;

    @BeforeEach
    void preparar() throws IOException {
        servidor = new ServidorCatalogoFalso();
        registro = CompiladorGroovyTest.registroConNativas();
        factory = new FiguraFactory(registro);
        CompiladorGroovy compilador = new CompiladorGroovy(registro, carpeta.toString());
        CatalogoRemotoClient cliente = new CatalogoRemotoClient(RestClient.builder(), new ObjectMapper(),
                servidor::url, Duration.ofMillis(2000));
        servicio = new CatalogoRemotoService(cliente, compilador);
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    // ---- 7. Descargar, compilar y usar una figura del catálogo ----

    @Test
    void descargaCompilaYUsaUnaFiguraRemota() throws IOException {
        servidor.json("/figuras", "[\"Pentagono\"]")
                .json("/figuras/Pentagono", figuraJson("Pentagono", ejemplo("Pentagono")));
        assertThat(servicio.listar()).containsExactly("Pentagono");
        assertThat(registro.contiene("Pentagono")).isFalse();

        ClaseInfo info = servicio.importar("Pentagono");

        assertThat(info.nombre()).isEqualTo("Pentagono");
        assertThat(info.origen()).isEqualTo(OrigenClase.REMOTA);
        assertThat(registro.origenDe("Pentagono")).isEqualTo(OrigenClase.REMOTA);

        // desde aquí es una figura como cualquier otra
        Figura pentagono = factory.crear("Pentagono", Map.of("lado", 2));
        assertThat(pentagono.calcularArea()).isCloseTo(6.8819, within(0.0001));
        assertThat(pentagono.calcularPerimetro()).isEqualTo(10.0);
        assertThat(factory.calcularArea(pentagono)).isCloseTo(6.8819, within(0.0001));
    }

    @Test
    void laFiguraRemotaSeGuardaConSuOrigenParaRecargarla() throws IOException {
        servidor.json("/figuras/Hexagono", figuraJson("Hexagono", ejemplo("Hexagono")));

        servicio.importar("Hexagono");

        assertThat(Files.readString(carpeta.resolve("Hexagono.groovy"))).startsWith("// origen: REMOTA");

        RegistroClases registroNuevo = CompiladorGroovyTest.registroConNativas();
        new CompiladorGroovy(registroNuevo, carpeta.toString()).recargarGuardadas();
        assertThat(registroNuevo.origenDe("Hexagono")).isEqualTo(OrigenClase.REMOTA);
    }

    // ---- código remoto que no debe aceptarse ----

    @Test
    void rechazaCodigoRemotoMalicioso() throws IOException {
        servidor.json("/figuras/Malicioso", figuraJson("Malicioso", ejemplo("Malicioso")));

        assertThatThrownBy(() -> servicio.importar("Malicioso"))
                .isInstanceOf(CodigoGroovyInvalidoException.class)
                .hasMessageContaining("java.lang.System");

        assertThat(registro.contiene("Malicioso")).isFalse();
        assertThat(carpeta.resolve("Malicioso.groovy")).doesNotExist();
    }

    @Test
    void rechazaCodigoRemotoQueNoCompila() {
        servidor.json("/figuras/Roto", figuraJson("Roto", "class Roto extends { ((( "));

        assertThatThrownBy(() -> servicio.importar("Roto"))
                .isInstanceOf(CodigoGroovyInvalidoException.class)
                .hasMessageContaining("No se pudo compilar");
        assertThat(registro.contiene("Roto")).isFalse();
    }

    @Test
    void rechazaCodigoRemotoQueNoEsUnaFigura() {
        servidor.json("/figuras/Otra", figuraJson("Otra", "class Otra { }"));

        assertThatThrownBy(() -> servicio.importar("Otra"))
                .isInstanceOf(CodigoGroovyInvalidoException.class)
                .hasMessageContaining("debe implementar Figura");
    }

    @Test
    void rechazaQueElCatalogoDevuelvaOtraClaseDeLaPedida() throws IOException {
        // Se pide Pentagono pero el servicio entrega el código de Hexagono
        servidor.json("/figuras/Pentagono", figuraJson("Pentagono", ejemplo("Hexagono")));

        assertThatThrownBy(() -> servicio.importar("Pentagono"))
                .isInstanceOf(CodigoGroovyInvalidoException.class)
                .hasMessageContaining("Se pidió la clase 'Pentagono'")
                .hasMessageContaining("'Hexagono'");

        assertThat(registro.contiene("Hexagono")).as("no se registra lo que no se pidió").isFalse();
        assertThat(registro.contiene("Pentagono")).isFalse();
        assertThat(carpeta.toFile().list()).as("no se guarda nada").isNullOrEmpty();
    }

    @Test
    void noPermiteQueUnaFiguraRemotaReemplaceUnaNativa() {
        String codigo = """
                import com.udla.figuras.model.FiguraGeometrica
                class Circulo extends FiguraGeometrica {
                    Circulo() { super("Circulo", null) }
                    double calcularArea() { 0 }
                    double calcularPerimetro() { 0 }
                    void escalar(double f) { }
                }
                """;
        servidor.json("/figuras/Circulo", figuraJson("Circulo", codigo));

        assertThatThrownBy(() -> servicio.importar("Circulo"))
                .isInstanceOf(CodigoGroovyInvalidoException.class)
                .hasMessageContaining("nativa");
        assertThat(registro.origenDe("Circulo")).isEqualTo(OrigenClase.NATIVA);
    }

    // ---- fallos del servicio ----

    @Test
    void figuraQueElCatalogoNoTiene() {
        assertThatThrownBy(() -> servicio.importar("Dodecaedro"))
                .isInstanceOf(FiguraRemotaNoEncontradaException.class);
    }

    @Test
    void catalogoConError500() {
        servidor.responder("/figuras/Pentagono", 500, "text/plain", "error interno");

        assertThatThrownBy(() -> servicio.importar("Pentagono"))
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("500");
    }

    @Test
    void unFalloNoDejaElServicioEnUnEstadoRaro() throws IOException {
        servidor.responder("/figuras/Pentagono", 500, "text/plain", "error interno");
        assertThatThrownBy(() -> servicio.importar("Pentagono")).isInstanceOf(CatalogoRemotoException.class);

        servidor.json("/figuras/Pentagono", figuraJson("Pentagono", ejemplo("Pentagono")));

        assertThat(servicio.importar("Pentagono").origen()).isEqualTo(OrigenClase.REMOTA);
    }
}
