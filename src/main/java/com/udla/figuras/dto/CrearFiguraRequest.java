package com.udla.figuras.dto;

import java.util.Map;

/** Cuerpo de {@code POST /api/figuras}: {@code {"clase": "Circulo", "parametros": {"radio": 5}}}. */
public record CrearFiguraRequest(String clase, Map<String, Object> parametros) {
}
