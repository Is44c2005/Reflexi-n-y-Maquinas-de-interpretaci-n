package com.udla.figuras.dto;

import java.time.Instant;

/** Formato único de error de la API. */
public record ErrorResponse(Instant timestamp, int status, String error, String mensaje, String ruta) {
}
