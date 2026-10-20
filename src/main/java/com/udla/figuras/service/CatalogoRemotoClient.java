package com.udla.figuras.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udla.figuras.dto.FiguraRemota;
import com.udla.figuras.service.excepcion.CatalogoRemotoException;
import com.udla.figuras.service.excepcion.FiguraRemotaNoEncontradaException;
import com.udla.figuras.service.excepcion.ParametrosInvalidosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Cliente del catálogo externo de figuras. Contrato:
 * <ul>
 *   <li>{@code GET {url}/figuras} → lista JSON con los nombres disponibles.</li>
 *   <li>{@code GET {url}/figuras/{nombre}} → {@code {"nombre": "...", "codigoGroovy": "..."}}.</li>
 * </ul>
 * Cualquier fallo del servicio se traduce a una excepción con un mensaje claro: nunca se propaga una
 * excepción de red sin traducir ni se tumba la aplicación.
 *
 * <p>La URL se lee de {@code figuras.catalogo.url} en cada petición, no al crear el bean.
 */
@Component
public class CatalogoRemotoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoRemotoClient.class);

    private static final int MAX_BYTES = 1_000_000;
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private final RestClient.Builder builder;
    private final ObjectMapper mapper;
    private final Supplier<String> url;
    private final Duration tiempoLimite;

    @Autowired
    public CatalogoRemotoClient(RestClient.Builder builder, ObjectMapper mapper, Environment entorno) {
        this(builder, mapper,
                () -> entorno.getProperty("figuras.catalogo.url", ""),
                Duration.ofMillis(entorno.getProperty("figuras.catalogo.timeout-ms", Long.class, 3000L)));
    }

    public CatalogoRemotoClient(RestClient.Builder builder, ObjectMapper mapper,
                                Supplier<String> url, Duration tiempoLimite) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(tiempoLimite);
        fabrica.setReadTimeout(tiempoLimite);
        this.builder = builder.clone().requestFactory(fabrica);
        this.mapper = mapper;
        this.url = url;
        this.tiempoLimite = tiempoLimite;
    }

    /** Nombres de las figuras que ofrece el catálogo. */
    public List<String> listar() {
        JsonNode raiz = leerJson(get("/figuras", null));
        if (!raiz.isArray()) {
            throw new CatalogoRemotoException("El catálogo respondió algo distinto de una lista JSON de nombres en /figuras");
        }
        List<String> nombres = new ArrayList<>();
        for (JsonNode elemento : raiz) {
            if (!elemento.isTextual()) {
                throw new CatalogoRemotoException("La lista de /figuras debe contener solo nombres (texto)");
            }
            nombres.add(elemento.asText());
        }
        return nombres;
    }

    /** Descarga el código Groovy de una figura. */
    public FiguraRemota descargar(String nombre) {
        // El nombre va dentro de la URL: solo se admiten identificadores, sin barras ni puntos
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            throw new ParametrosInvalidosException("El nombre de figura '" + nombre
                    + "' no es válido: use letras, números y guion bajo, empezando por letra");
        }
        byte[] cuerpo;
        try {
            cuerpo = get("/figuras/{nombre}", nombre);
        } catch (CatalogoRemotoException e) {
            if (Integer.valueOf(404).equals(e.getEstadoHttp())) {
                throw new FiguraRemotaNoEncontradaException(nombre);
            }
            throw e;
        }
        JsonNode raiz = leerJson(cuerpo);
        JsonNode codigo = raiz.path("codigoGroovy");
        if (!codigo.isTextual() || codigo.asText().isBlank()) {
            throw new CatalogoRemotoException("La respuesta del catálogo para '" + nombre
                    + "' no incluye el campo 'codigoGroovy' con el código fuente");
        }
        return new FiguraRemota(raiz.path("nombre").asText(nombre), codigo.asText());
    }

    private byte[] get(String ruta, String nombre) {
        String base = url.get() == null ? "" : url.get().strip().replaceAll("/+$", "");
        if (base.isEmpty()) {
            throw new CatalogoRemotoException("No hay catálogo remoto configurado: defina figuras.catalogo.url");
        }
        try {
            RestClient cliente = builder.clone().baseUrl(base).build();
            RestClient.RequestHeadersSpec<?> peticion = nombre == null
                    ? cliente.get().uri(ruta)
                    : cliente.get().uri(ruta, nombre);
            byte[] cuerpo = peticion.retrieve().body(byte[].class);
            if (cuerpo == null || cuerpo.length == 0) {
                throw new CatalogoRemotoException("El catálogo en " + base + " respondió sin contenido");
            }
            if (cuerpo.length > MAX_BYTES) {
                throw new CatalogoRemotoException("La respuesta del catálogo supera el máximo de " + MAX_BYTES + " bytes");
            }
            return cuerpo;
        } catch (RestClientResponseException e) {
            int estado = e.getStatusCode().value();
            log.warn("Catálogo remoto {}{} respondió {}", base, ruta, estado);
            throw new CatalogoRemotoException(estado == 404
                    ? "El catálogo respondió 404 (no encontrado) para " + ruta
                    : "El catálogo en " + base + " respondió con error HTTP " + estado, estado, e);
        } catch (ResourceAccessException e) {
            log.warn("No se pudo contactar el catálogo {}: {}", base, e.getMessage());
            throw new CatalogoRemotoException(describirFalloDeRed(base, e), null, e);
        } catch (RestClientException e) {
            log.warn("Respuesta inesperada del catálogo {}: {}", base, e.getMessage());
            throw new CatalogoRemotoException("No se pudo leer la respuesta del catálogo en " + base, null, e);
        }
    }

    private JsonNode leerJson(byte[] cuerpo) {
        try {
            // Se interpreta como JSON sea cual sea el Content-Type: hay hosts estáticos (p. ej. GitHub Pages)
            // que sirven JSON como text/html. Se decodifica en UTF-8 porque RFC 8259 lo exige. Se ignora un BOM
            // inicial: los editores y scripts de Windows lo añaden a los archivos UTF-8 y Jackson no lo admite.
            String texto = new String(cuerpo, StandardCharsets.UTF_8);
            if (texto.startsWith("﻿")) {
                texto = texto.substring(1);
            }
            return mapper.readTree(texto);
        } catch (JsonProcessingException e) {
            throw new CatalogoRemotoException("El catálogo devolvió una respuesta que no es JSON válido", null, e);
        }
    }

    private String describirFalloDeRed(String base, ResourceAccessException e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException) {
                return "El catálogo en " + base + " no respondió en " + tiempoLimite.toMillis() + " ms (tiempo de espera agotado)";
            }
            if (causa instanceof ConnectException) {
                return "No se pudo conectar con el catálogo en " + base + " (¿el servicio está en marcha?)";
            }
            if (causa instanceof UnknownHostException) {
                return "No se pudo resolver el servidor del catálogo en " + base;
            }
        }
        return "Error de red al contactar el catálogo en " + base;
    }
}
