package com.udla.figuras.service.excepcion;

/**
 * Falló el servicio externo: está caído, tardó demasiado, respondió con error o con un formato
 * inesperado (se traduce a 502). {@code estadoHttp} es null si no llegó a haber respuesta HTTP.
 */
public class CatalogoRemotoException extends RuntimeException {

    private final Integer estadoHttp;

    public CatalogoRemotoException(String message) {
        this(message, null, null);
    }

    public CatalogoRemotoException(String message, Integer estadoHttp, Throwable cause) {
        super(message, cause);
        this.estadoHttp = estadoHttp;
    }

    public Integer getEstadoHttp() {
        return estadoHttp;
    }
}
