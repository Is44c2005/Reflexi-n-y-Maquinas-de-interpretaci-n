package com.udla.figuras.controller;

import com.udla.figuras.dto.ClaseInfo;
import com.udla.figuras.dto.InspeccionClase;
import com.udla.figuras.service.CompiladorGroovy;
import com.udla.figuras.service.InspeccionService;
import com.udla.figuras.service.OrigenClase;
import com.udla.figuras.service.RegistroClases;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clases")
public class ClaseController {

    private final RegistroClases registro;
    private final InspeccionService inspeccion;
    private final CompiladorGroovy compilador;

    public ClaseController(RegistroClases registro, InspeccionService inspeccion, CompiladorGroovy compilador) {
        this.registro = registro;
        this.inspeccion = inspeccion;
        this.compilador = compilador;
    }

    @GetMapping
    public List<ClaseInfo> listar() {
        return registro.clases().entrySet().stream()
                .map(e -> new ClaseInfo(e.getKey(), e.getValue().getName(), registro.origenDe(e.getKey())))
                .toList();
    }

    @GetMapping("/{nombre}/inspeccion")
    public InspeccionClase inspeccionar(@PathVariable String nombre) {
        return inspeccion.inspeccionar(nombre);
    }

    /** El cuerpo es el código fuente de una clase Groovy, en texto plano. */
    @PostMapping(path = "/groovy", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<ClaseInfo> registrarGroovy(@RequestBody String codigo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compilador.registrar(codigo, OrigenClase.GROOVY));
    }
}
