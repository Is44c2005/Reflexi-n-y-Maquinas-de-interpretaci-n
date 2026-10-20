package com.udla.figuras.service;

import com.udla.figuras.dto.ClaseInfo;
import com.udla.figuras.dto.FiguraRemota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** Descarga figuras del catálogo remoto y las registra como cualquier otra clase Groovy. */
@Service
public class CatalogoRemotoService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoRemotoService.class);

    private final CatalogoRemotoClient cliente;
    private final CompiladorGroovy compilador;

    public CatalogoRemotoService(CatalogoRemotoClient cliente, CompiladorGroovy compilador) {
        this.cliente = cliente;
        this.compilador = compilador;
    }

    public List<String> listar() {
        return cliente.listar();
    }

    /**
     * El código descargado es tan poco confiable como el que llega por POST: pasa por el mismo
     * {@link CompiladorGroovy}, con la misma política de seguridad.
     */
    public ClaseInfo importar(String nombre) {
        FiguraRemota figura = cliente.descargar(nombre);
        log.info("Descargada del catálogo la figura '{}' ({} caracteres)", nombre, figura.codigoGroovy().length());
        return compilador.registrar(figura.codigoGroovy(), OrigenClase.REMOTA, nombre);
    }
}
