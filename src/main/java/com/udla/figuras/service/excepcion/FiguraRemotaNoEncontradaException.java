package com.udla.figuras.service.excepcion;

/** El catálogo respondió 404: no tiene esa figura (se traduce a 404). */
public class FiguraRemotaNoEncontradaException extends RuntimeException {

    public FiguraRemotaNoEncontradaException(String nombre) {
        super("El catálogo remoto no tiene una figura llamada '" + nombre + "'");
    }
}
