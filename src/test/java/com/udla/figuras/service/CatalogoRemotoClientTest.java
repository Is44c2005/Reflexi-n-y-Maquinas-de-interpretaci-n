package com.udla.figuras.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udla.figuras.dto.FiguraRemota;
import com.udla.figuras.service.excepcion.CatalogoRemotoException;
import com.udla.figuras.service.excepcion.FiguraRemotaNoEncontradaException;
import com.udla.figuras.service.excepcion.ParametrosInvalidosException;
import com.udla.figuras.soporte.ServidorCatalogoFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static com.udla.figuras.soporte.ServidorCatalogoFalso.figuraJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada fallo del servicio externo debe dar un mensaje claro, no una excepción de red sin traducir. */
class CatalogoRemotoClientTest {

    ServidorCatalogoFalso servidor;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = new ServidorCatalogoFalso();
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    CatalogoRemotoClient cliente(String url, long tiempoLimiteMs) {
        return new CatalogoRemotoClient(RestClient.builder(), new ObjectMapper(), () -> url, Duration.ofMillis(tiempoLimiteMs));
    }

    CatalogoRemotoClient cliente() {
        return cliente(servidor.url(), 2000);
    }

    // ---- funcionamiento normal ----

    @Test
    void listaLosNombres() {
        servidor.json("/figuras", "[\"Pentagono\",\"Hexagono\"]");

        assertThat(cliente().listar()).containsExactly("Pentagono", "Hexagono");
    }

    @Test
    void descargaElCodigoDeUnaFigura() {
        servidor.json("/figuras/Pentagono", figuraJson("Pentagono", "class Pentagono { }"));

        FiguraRemota figura = cliente().descargar("Pentagono");

        assertThat(figura.nombre()).isEqualTo("Pentagono");
        assertThat(figura.codigoGroovy()).isEqualTo("class Pentagono { }");
    }

    @Test
    void conservaLosAcentosYSaltosDeLineaDelCodigo() {
        String codigo = "// perímetro y raíz\nclass A { }\n";
        servidor.json("/figuras/A", figuraJson("A", codigo));

        assertThat(cliente().descargar("A").codigoGroovy()).isEqualTo(codigo);
    }

    @Test
    void toleraUnaUrlConBarraFinal() {
        servidor.json("/figuras", "[\"A\"]");

        assertThat(cliente(servidor.url() + "/", 2000).listar()).containsExactly("A");
    }

    @Test
    void funcionaConUnHostEstaticoQueSirveJsonComoTextHtmlYRedirige() {
        // Es lo que hace GitHub Pages: /figuras redirige a /figuras/ y todo sale como text/html
        servidor.redirigir("/figuras", "/figuras/")
                .responder("/figuras/", 200, "text/html", "[\"Pentagono\"]")
                .responder("/figuras/Pentagono", 200, "text/html", figuraJson("Pentagono", "class Pentagono { }"));

        assertThat(cliente().listar()).containsExactly("Pentagono");
        assertThat(cliente().descargar("Pentagono").codigoGroovy()).isEqualTo("class Pentagono { }");
    }

    @Test
    void toleraUnBomAlPrincipioDelJson() {
        // Notepad y Set-Content -Encoding utf8 (Windows PowerShell) escriben los archivos UTF-8 con BOM
        servidor.responder("/figuras", 200, "text/html", "﻿[\"Pentagono\"]")
                .responder("/figuras/Pentagono", 200, "application/octet-stream",
                        "﻿" + figuraJson("Pentagono", "class Pentagono { }"));

        assertThat(cliente().listar()).containsExactly("Pentagono");
        assertThat(cliente().descargar("Pentagono").codigoGroovy()).isEqualTo("class Pentagono { }");
    }

    // ---- fallos del servicio ----

    @Test
    void figuraInexistenteEs404Propio() {
        // el servidor falso responde 404 a cualquier ruta que no conozca
        assertThatThrownBy(() -> cliente().descargar("Dodecaedro"))
                .isInstanceOf(FiguraRemotaNoEncontradaException.class)
                .hasMessageContaining("'Dodecaedro'");
    }

    @Test
    void errorDelServidorSeTraduceAMensajeConElEstado() {
        servidor.responder("/figuras", 500, "application/json", "{\"error\":\"boom\"}");

        assertThatThrownBy(() -> cliente().listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("error HTTP 500")
                .satisfies(e -> assertThat(((CatalogoRemotoException) e).getEstadoHttp()).isEqualTo(500));
    }

    @Test
    void ruta404EnLaListaEsUnFalloDelCatalogoNoUnaFiguraInexistente() {
        // /figuras no existe en este servidor: el catálogo está mal configurado, no falta una figura
        assertThatThrownBy(() -> cliente().listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("404");
    }

    @Test
    void tiempoDeEsperaAgotado() {
        servidor.lento("/figuras", 2000, "[]");

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> cliente(servidor.url(), 300).listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("tiempo de espera agotado")
                .hasMessageContaining("300 ms");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).as("no espera a que responda el servidor")
                .isLessThan(Duration.ofMillis(1500));
    }

    @Test
    void servicioCaido() throws IOException {
        String urlMuerta;
        try (ServerSocket libre = new ServerSocket(0)) {
            urlMuerta = "http://127.0.0.1:" + libre.getLocalPort();
        } // se cierra: nadie escucha en ese puerto

        assertThatThrownBy(() -> cliente(urlMuerta, 1000).listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("No se pudo conectar");
    }

    @Test
    void hostQueNoExiste() {
        assertThatThrownBy(() -> cliente("http://no-existe.invalid", 1000).listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("no-existe.invalid");
    }

    @Test
    void respuestaQueNoEsJson() {
        servidor.responder("/figuras", 200, "text/html", "<html>Bienvenido</html>");

        assertThatThrownBy(() -> cliente().listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("no es JSON válido");
    }

    @Test
    void respuestaVacia() {
        servidor.responder("/figuras", 200, "application/json", "");

        assertThatThrownBy(() -> cliente().listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("sin contenido");
    }

    @Test
    void listaConFormatoIncorrecto() {
        servidor.json("/figuras", "{\"figuras\":[\"A\"]}");
        assertThatThrownBy(() -> cliente().listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("lista JSON");

        servidor.json("/figuras", "[1,2]");
        assertThatThrownBy(() -> cliente().listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("solo nombres");
    }

    @Test
    void figuraSinCodigo() {
        servidor.json("/figuras/A", "{\"nombre\":\"A\"}");
        assertThatThrownBy(() -> cliente().descargar("A"))
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("codigoGroovy");

        servidor.json("/figuras/B", "{\"nombre\":\"B\",\"codigoGroovy\":\"   \"}");
        assertThatThrownBy(() -> cliente().descargar("B"))
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("codigoGroovy");
    }

    @Test
    void respuestaDemasiadoGrande() {
        servidor.json("/figuras", "[\"" + "x".repeat(1_100_000) + "\"]");

        assertThatThrownBy(() -> cliente().listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("máximo");
    }

    @Test
    void sinUrlConfigurada() {
        assertThatThrownBy(() -> cliente("  ", 1000).listar())
                .isInstanceOf(CatalogoRemotoException.class)
                .hasMessageContaining("figuras.catalogo.url");
    }

    @Test
    void elNombreSeValidaAntesDeLlamarALaRed() {
        for (String malo : new String[]{"../etc/passwd", "a/b", "", "9abc", "a b", "Pent%2Fagono", "x?y=1"}) {
            assertThatThrownBy(() -> cliente().descargar(malo))
                    .as("nombre " + malo)
                    .isInstanceOf(ParametrosInvalidosException.class);
        }
        assertThatThrownBy(() -> cliente().descargar(null)).isInstanceOf(ParametrosInvalidosException.class);
    }

    @Test
    void despuesDeUnFalloElClienteSigueFuncionando() {
        servidor.responder("/figuras", 503, "text/plain", "mantenimiento");
        CatalogoRemotoClient cliente = cliente();
        assertThatThrownBy(cliente::listar).isInstanceOf(CatalogoRemotoException.class);

        servidor.json("/figuras", "[\"A\"]");

        assertThat(cliente.listar()).containsExactly("A");
    }
}
