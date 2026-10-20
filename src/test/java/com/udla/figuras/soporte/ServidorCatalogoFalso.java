package com.udla.figuras.soporte;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP mínimo (el del JDK) para simular un catálogo externo con respuestas a medida:
 * errores, lentitud, JSON roto, redirecciones y tipos de contenido raros.
 */
public class ServidorCatalogoFalso implements AutoCloseable {

    private record Respuesta(int estado, String tipo, String cuerpo, long demoraMs, String ubicacion) {
    }

    private final HttpServer servidor;
    private final Map<String, Respuesta> respuestas = new ConcurrentHashMap<>();

    public ServidorCatalogoFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/", intercambio -> {
            Respuesta r = respuestas.getOrDefault(intercambio.getRequestURI().getPath(),
                    new Respuesta(404, "application/json", "{\"error\":\"no existe\"}", 0, null));
            try {
                if (r.demoraMs() > 0) {
                    Thread.sleep(r.demoraMs());
                }
                byte[] bytes = r.cuerpo().getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().set("Content-Type", r.tipo());
                if (r.ubicacion() != null) {
                    intercambio.getResponseHeaders().set("Location", r.ubicacion());
                }
                intercambio.sendResponseHeaders(r.estado(), bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    try (OutputStream salida = intercambio.getResponseBody()) {
                        salida.write(bytes);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // el cliente se rindió antes (timeout): es lo que se quiere simular
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public ServidorCatalogoFalso json(String ruta, String cuerpo) {
        return responder(ruta, 200, "application/json", cuerpo);
    }

    public ServidorCatalogoFalso responder(String ruta, int estado, String tipo, String cuerpo) {
        respuestas.put(ruta, new Respuesta(estado, tipo, cuerpo, 0, null));
        return this;
    }

    public ServidorCatalogoFalso lento(String ruta, long demoraMs, String cuerpo) {
        respuestas.put(ruta, new Respuesta(200, "application/json", cuerpo, demoraMs, null));
        return this;
    }

    public ServidorCatalogoFalso redirigir(String ruta, String destino) {
        respuestas.put(ruta, new Respuesta(301, "text/html", "", 0, destino));
        return this;
    }

    /** JSON de una figura tal como lo devolvería el servicio real. */
    public static String figuraJson(String nombre, String codigo) {
        return "{\"nombre\":" + comillas(nombre) + ",\"codigoGroovy\":" + comillas(codigo) + "}";
    }

    private static String comillas(String texto) {
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
