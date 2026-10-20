package com.udla.figuras.controller;

import com.udla.figuras.dto.ErrorResponse;
import com.udla.figuras.service.excepcion.CatalogoRemotoException;
import com.udla.figuras.service.excepcion.ClaseNoRegistradaException;
import com.udla.figuras.service.excepcion.CodigoGroovyInvalidoException;
import com.udla.figuras.service.excepcion.FiguraNoEncontradaException;
import com.udla.figuras.service.excepcion.FiguraRemotaNoEncontradaException;
import com.udla.figuras.service.excepcion.ParametrosInvalidosException;
import com.udla.figuras.service.excepcion.ValidacionFiguraException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

/** Respuestas de error JSON con el mismo formato para toda la API. */
@RestControllerAdvice
public class ManejadorExcepciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorExcepciones.class);

    @ExceptionHandler(ClaseNoRegistradaException.class)
    public ResponseEntity<ErrorResponse> claseNoRegistrada(ClaseNoRegistradaException e, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler({ParametrosInvalidosException.class, ValidacionFiguraException.class})
    public ResponseEntity<ErrorResponse> peticionInvalida(RuntimeException e, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage(), req);
    }

    @ExceptionHandler(FiguraNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> figuraNoEncontrada(FiguraNoEncontradaException e, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parametroDeRutaInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "El valor '" + e.getValue() + "' no es válido para '" + e.getName() + "' (se esperaba un número)", req);
    }

    @ExceptionHandler(FiguraRemotaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> figuraRemotaNoEncontrada(FiguraRemotaNoEncontradaException e, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(CatalogoRemotoException.class)
    public ResponseEntity<ErrorResponse> catalogoRemoto(CatalogoRemotoException e, HttpServletRequest req) {
        return responder(HttpStatus.BAD_GATEWAY, e.getMessage(), req);
    }

    @ExceptionHandler(CodigoGroovyInvalidoException.class)
    public ResponseEntity<ErrorResponse> groovyInvalido(CodigoGroovyInvalidoException e, HttpServletRequest req) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException e, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o le faltan campos", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception e, HttpServletRequest req) {
        // Errores propios de Spring MVC (ruta inexistente, método no permitido...) traen su estado
        if (e instanceof org.springframework.web.ErrorResponse errorSpring) {
            HttpStatus estado = HttpStatus.valueOf(errorSpring.getStatusCode().value());
            return responder(estado, e.getMessage(), req);
        }
        log.error("Error inesperado en {}", req.getRequestURI(), e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno: " + e.getMessage(), req);
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus estado, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(estado).body(new ErrorResponse(
                Instant.now(), estado.value(), estado.getReasonPhrase(), mensaje, req.getRequestURI()));
    }
}
