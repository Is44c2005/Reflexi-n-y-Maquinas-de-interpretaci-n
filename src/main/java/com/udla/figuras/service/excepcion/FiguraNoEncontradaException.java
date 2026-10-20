package com.udla.figuras.service.excepcion;

/** No hay ninguna figura creada con ese id (se traduce a 404). */
public class FiguraNoEncontradaException extends RuntimeException {

    public FiguraNoEncontradaException(long id) {
        super("No existe ninguna figura con id " + id);
    }
}
