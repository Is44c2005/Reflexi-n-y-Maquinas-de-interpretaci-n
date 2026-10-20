package com.udla.figuras.dto;

/** Cuerpo de {@code POST /api/figuras/{id}/escalar}: {@code {"factor": 2}}. */
public record EscalarRequest(Double factor) {
}
