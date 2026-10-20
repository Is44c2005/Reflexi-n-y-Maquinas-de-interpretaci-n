package com.udla.figuras.dto;

import java.util.Map;

/**
 * @param parametros los valores con los que se creó la figura (no cambian al escalar)
 * @param escala     producto de los factores aplicados con {@code escalar} (1.0 si nunca se escaló)
 */
public record FiguraResponse(
        long id,
        String clase,
        String nombre,
        Map<String, Object> parametros,
        double escala,
        double area,
        double perimetro) {
}
