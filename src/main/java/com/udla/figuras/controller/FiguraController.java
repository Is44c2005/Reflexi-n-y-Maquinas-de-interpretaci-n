package com.udla.figuras.controller;

import com.udla.figuras.dto.AreaTotalResponse;
import com.udla.figuras.dto.CrearFiguraRequest;
import com.udla.figuras.dto.EscalarRequest;
import com.udla.figuras.dto.FiguraResponse;
import com.udla.figuras.service.FiguraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/figuras")
public class FiguraController {

    private final FiguraService servicio;

    public FiguraController(FiguraService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<FiguraResponse> crear(@RequestBody CrearFiguraRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(peticion));
    }

    @GetMapping
    public List<FiguraResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/area-total")
    public AreaTotalResponse areaTotal() {
        return servicio.areaTotal();
    }

    @GetMapping("/{id}")
    public FiguraResponse obtener(@PathVariable long id) {
        return servicio.obtener(id);
    }

    @PostMapping("/{id}/escalar")
    public FiguraResponse escalar(@PathVariable long id, @RequestBody EscalarRequest peticion) {
        return servicio.escalar(id, peticion.factor());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
