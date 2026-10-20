package com.udla.figuras.service.excepcion;

/** La clase pedida no existe en el registro (se traduce a 404). */
public class ClaseNoRegistradaException extends RuntimeException {

    public ClaseNoRegistradaException(String message) {
        super(message);
    }
}
