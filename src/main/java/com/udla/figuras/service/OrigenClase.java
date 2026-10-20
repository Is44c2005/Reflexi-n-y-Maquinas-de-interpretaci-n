package com.udla.figuras.service;

public enum OrigenClase {
    /** Clase compilada con la aplicación, descubierta al escanear el paquete de figuras. */
    NATIVA,
    /** Clase Groovy enviada o recargada desde la carpeta de persistencia. */
    GROOVY,
    /** Clase Groovy descargada del catálogo remoto. */
    REMOTA
}
