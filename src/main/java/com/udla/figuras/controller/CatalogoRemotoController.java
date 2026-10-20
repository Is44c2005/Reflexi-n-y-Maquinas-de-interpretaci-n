package com.udla.figuras.controller;

import com.udla.figuras.dto.ClaseInfo;
import com.udla.figuras.service.CatalogoRemotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clases/remotas")
public class CatalogoRemotoController {

    private final CatalogoRemotoService servicio;

    public CatalogoRemotoController(CatalogoRemotoService servicio) {
        this.servicio = servicio;
    }

    /** Nombres de las figuras que ofrece el catálogo configurado. */
    @GetMapping
    public List<String> listar() {
        return servicio.listar();
    }

    /** Descarga la figura, la compila y la registra con origen REMOTA. */
    @PostMapping("/{nombre}")
    public ResponseEntity<ClaseInfo> importar(@PathVariable String nombre) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.importar(nombre));
    }
}
