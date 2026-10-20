package com.udla.figuras.service.excepcion;

/** Faltan parámetros, sobran parámetros o alguno tiene un tipo incorrecto (se traduce a 400). */
public class ParametrosInvalidosException extends RuntimeException {

    public ParametrosInvalidosException(String message) {
        super(message);
    }
}
