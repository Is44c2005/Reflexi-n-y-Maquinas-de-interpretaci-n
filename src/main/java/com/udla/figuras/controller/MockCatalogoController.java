package com.udla.figuras.controller;

import com.udla.figuras.dto.FiguraRemota;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo simulado que cumple el contrato del servicio externo y sirve los ejemplos de
 * {@code resources/ejemplos}. Existe porque no hay un servicio público con este contrato.
 * Se desactiva con {@code figuras.catalogo.mock.enabled=false} al apuntar a un servicio real.
 *
 * <p>No sirve los ejemplos de {@code figuras.catalogo.mock.excluidos} (por defecto, Malicioso): es un
 * caso de prueba de seguridad, no una figura que se quiera "traer del catálogo".
 */
@RestController
@RequestMapping("/mock-catalogo")
@ConditionalOnProperty(name = "figuras.catalogo.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockCatalogoController {

    private final PathMatchingResourcePatternResolver resolvedor = new PathMatchingResourcePatternResolver();
    private final Set<String> excluidos;

    public MockCatalogoController(@Value("${figuras.catalogo.mock.excluidos:Malicioso}") Set<String> excluidos) {
        this.excluidos = excluidos;
    }

    @GetMapping("/figuras")
    public List<String> listar() throws IOException {
        return Arrays.stream(resolvedor.getResources("classpath:ejemplos/*.groovy"))
                .map(r -> r.getFilename().replaceFirst("\\.groovy$", ""))
                .filter(nombre -> !excluidos.contains(nombre))
                .sorted()
                .collect(Collectors.toList());
    }

    @GetMapping("/figuras/{nombre}")
    public ResponseEntity<FiguraRemota> obtener(@PathVariable String nombre) throws IOException {
        // Solo se sirve lo que aparece en la lista: el nombre nunca se usa para construir una ruta libre
        if (!listar().contains(nombre)) {
            return ResponseEntity.notFound().build();
        }
        Resource recurso = resolvedor.getResource("classpath:ejemplos/" + nombre + ".groovy");
        try (InputStream in = recurso.getInputStream()) {
            return ResponseEntity.ok(new FiguraRemota(nombre, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }
}
