package com.udla.figuras.dto;

/** Contrato del catálogo: GET {url}/figuras/{nombre} devuelve este JSON. */
public record FiguraRemota(String nombre, String codigoGroovy) {
}
