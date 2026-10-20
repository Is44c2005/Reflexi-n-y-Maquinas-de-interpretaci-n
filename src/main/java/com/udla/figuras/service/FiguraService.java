package com.udla.figuras.service;

import com.udla.figuras.dto.AreaTotalResponse;
import com.udla.figuras.dto.CrearFiguraRequest;
import com.udla.figuras.dto.FiguraResponse;
import com.udla.figuras.model.Escalable;
import com.udla.figuras.model.Figura;
import com.udla.figuras.model.GestorFiguras;
import com.udla.figuras.service.excepcion.FiguraNoEncontradaException;
import com.udla.figuras.service.excepcion.ParametrosInvalidosException;
import com.udla.figuras.service.excepcion.ValidacionFiguraException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Casos de uso de la API de figuras. Crea con {@link FiguraFactory}, guarda en {@link GestorFiguras}
 * y mantiene aparte lo que el diseño original no tiene: el id y los parámetros de creación.
 */
@Service
public class FiguraService {

    private static class Entrada {
        final long id;
        final String clase;
        final Map<String, Object> parametros;
        final Figura figura;
        double escala = 1.0;

        Entrada(long id, String clase, Map<String, Object> parametros, Figura figura) {
            this.id = id;
            this.clase = clase;
            this.parametros = parametros;
            this.figura = figura;
        }
    }

    private final FiguraFactory factory;
    private final GestorFiguras gestor;
    private final Map<Long, Entrada> entradas = new LinkedHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    public FiguraService(FiguraFactory factory, GestorFiguras gestor) {
        this.factory = factory;
        this.gestor = gestor;
    }

    public synchronized FiguraResponse crear(CrearFiguraRequest peticion) {
        if (peticion == null || peticion.clase() == null || peticion.clase().isBlank()) {
            throw new ParametrosInvalidosException("Falta el campo 'clase' (por ejemplo \"Circulo\")");
        }
        Map<String, Object> parametros = peticion.parametros() == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(peticion.parametros()));

        Figura figura = factory.crear(peticion.clase(), parametros);
        comprobarValoresFinitos(figura, "los valores indicados son demasiado grandes");

        Entrada entrada = new Entrada(secuencia.incrementAndGet(), peticion.clase(), parametros, figura);
        gestor.agregar(figura);
        entradas.put(entrada.id, entrada);
        return aRespuesta(entrada);
    }

    public synchronized List<FiguraResponse> listar() {
        return entradas.values().stream().map(this::aRespuesta).toList();
    }

    public synchronized FiguraResponse obtener(long id) {
        return aRespuesta(buscar(id));
    }

    public synchronized FiguraResponse escalar(long id, Double factor) {
        Entrada entrada = buscar(id);
        if (factor == null) {
            throw new ParametrosInvalidosException("Falta el campo 'factor' (por ejemplo {\"factor\": 2})");
        }
        if (!(factor > 0) || Double.isInfinite(factor)) {
            throw new ValidacionFiguraException("El factor de escala debe ser un número mayor que 0 (recibido: "
                    + factor + ")", null);
        }
        // Escalable es una interfaz aparte de Figura: una figura Groovy puede no implementarla
        if (!(entrada.figura instanceof Escalable escalable)) {
            throw new ParametrosInvalidosException("La figura " + id + " (" + entrada.clase
                    + ") no implementa Escalable, así que no se puede escalar");
        }
        try {
            escalable.escalar(factor);
        } catch (RuntimeException e) {
            throw new ValidacionFiguraException("No se pudo escalar la figura " + id + ": " + e.getMessage(), e);
        }
        try {
            comprobarValoresFinitos(entrada.figura, "el factor es demasiado grande");
        } catch (ValidacionFiguraException e) {
            // Mejor esfuerzo para dejar la figura como estaba (si la medida misma se desbordó no hay vuelta atrás)
            try {
                escalable.escalar(1 / factor);
            } catch (RuntimeException ignorada) {
                // se informa del problema original
            }
            throw e;
        }
        entrada.escala *= factor;
        return aRespuesta(entrada);
    }

    public synchronized void eliminar(long id) {
        Entrada entrada = buscar(id);
        gestor.eliminar(entrada.figura);
        entradas.remove(id);
    }

    public synchronized AreaTotalResponse areaTotal() {
        return new AreaTotalResponse(gestor.areaTotal(), entradas.size());
    }

    private Entrada buscar(long id) {
        Entrada entrada = entradas.get(id);
        if (entrada == null) {
            throw new FiguraNoEncontradaException(id);
        }
        return entrada;
    }

    private void comprobarValoresFinitos(Figura figura, String motivo) {
        if (!Double.isFinite(factory.calcularArea(figura)) || !Double.isFinite(factory.calcularPerimetro(figura))) {
            throw new ValidacionFiguraException("El área o el perímetro no son un número finito: " + motivo, null);
        }
    }

    /** Área y perímetro se calculan por reflexión (Method.invoke), igual para figuras nativas y dinámicas. */
    private FiguraResponse aRespuesta(Entrada e) {
        return new FiguraResponse(e.id, e.clase, e.figura.getNombre(), e.parametros, e.escala,
                factory.calcularArea(e.figura), factory.calcularPerimetro(e.figura));
    }
}
