package com.udla.figuras.service.excepcion;

/**
 * El constructor de la figura rechazó los valores (medida negativa, triángulo inválido...).
 * Se traduce a 400. La causa original es la que lanzó el constructor, ya desempaquetada
 * de InvocationTargetException.
 */
public class ValidacionFiguraException extends RuntimeException {

    public ValidacionFiguraException(String message, Throwable cause) {
        super(message, cause);
    }
}
