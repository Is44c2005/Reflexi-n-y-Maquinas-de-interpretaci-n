package com.udla.figuras.service.excepcion;

/**
 * El código Groovy no compila, viola la política de seguridad o no define una figura
 * válida (se traduce a 422).
 */
public class CodigoGroovyInvalidoException extends RuntimeException {

    public CodigoGroovyInvalidoException(String message) {
        super(message);
    }

    public CodigoGroovyInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
