package com.udla.figuras.dto;

import com.udla.figuras.service.OrigenClase;

import java.util.List;

/** Resultado de inspeccionar una clase por reflexión (constructores, campos y métodos). */
public record InspeccionClase(
        String nombre,
        String nombreCompleto,
        OrigenClase origen,
        String superclase,
        List<String> interfaces,
        List<ConstructorInfo> constructores,
        List<CampoInfo> campos,
        List<MetodoInfo> metodos) {

    public record ParametroInfo(String nombre, String tipo) {
    }

    public record ConstructorInfo(String firma, List<ParametroInfo> parametros) {
    }

    public record CampoInfo(String nombre, String tipo, String modificadores, String declaradoEn) {
    }

    public record MetodoInfo(String nombre, String retorno, List<ParametroInfo> parametros,
                             String modificadores, String declaradoEn) {
    }
}
