package com.udla.figuras.service;

import com.udla.figuras.model.Figura;
import com.udla.figuras.service.excepcion.ParametrosInvalidosException;
import com.udla.figuras.service.excepcion.ValidacionFiguraException;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Crea figuras por reflexión. No conoce ningún tipo concreto: busca la clase en el
 * {@link RegistroClases}, elige un constructor y empareja los parámetros por nombre.
 * Por eso funciona igual con una figura nativa que con una cargada desde Groovy.
 */
@Service
public class FiguraFactory {

    private static final ConversionService CONVERSION = DefaultConversionService.getSharedInstance();

    private final RegistroClases registro;

    public FiguraFactory(RegistroClases registro) {
        this.registro = registro;
    }

    public Figura crear(String nombreClase, Map<String, Object> parametros) {
        Class<?> clase = registro.obtener(nombreClase);
        Map<String, Object> recibidos = parametros == null ? Map.of() : parametros;

        Constructor<?> constructor = elegirConstructor(clase, recibidos.keySet());
        Parameter[] declarados = constructor.getParameters();
        Object[] argumentos = new Object[declarados.length];
        for (int i = 0; i < declarados.length; i++) {
            argumentos[i] = convertir(clase, declarados[i], recibidos.get(declarados[i].getName()));
        }

        try {
            return Figura.class.cast(constructor.newInstance(argumentos));
        } catch (InvocationTargetException e) {
            // El constructor lanzó una excepción: la causa real es la que interesa al usuario
            Throwable causa = e.getCause();
            if (causa instanceof Error error) {
                throw error;
            }
            throw new ValidacionFiguraException(
                    "No se pudo crear '" + clase.getSimpleName() + "': " + causa.getMessage(), causa);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("No se pudo invocar el constructor de " + clase.getName()
                    + " (¿la clase es pública y concreta?): " + e.getMessage(), e);
        }
    }

    /** Invoca calcularArea() por reflexión (Method.invoke), sin depender del tipo de la figura. */
    public double calcularArea(Object figura) {
        return invocarDouble(figura, "calcularArea");
    }

    /** Invoca calcularPerimetro() por reflexión (Method.invoke), sin depender del tipo de la figura. */
    public double calcularPerimetro(Object figura) {
        return invocarDouble(figura, "calcularPerimetro");
    }

    private double invocarDouble(Object instancia, String metodo) {
        Class<?> clase = instancia.getClass();
        try {
            Method m = clase.getMethod(metodo);
            return ((Number) m.invoke(instancia)).doubleValue();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("La clase " + clase.getName() + " no tiene el método público "
                    + metodo + "()", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Falló " + clase.getSimpleName() + "." + metodo + "(): "
                    + e.getCause().getMessage(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("No se pudo acceder a " + clase.getName() + "." + metodo + "()", e);
        }
    }

    /**
     * Entre los constructores públicos elige el que mejor encaja con los nombres recibidos:
     * menos parámetros que faltan o que sobran y, a igualdad, el de más parámetros.
     */
    private Constructor<?> elegirConstructor(Class<?> clase, Set<String> recibidos) {
        Constructor<?>[] constructores = clase.getConstructors();
        if (constructores.length == 0) {
            throw new ParametrosInvalidosException(
                    "La clase '" + clase.getSimpleName() + "' no tiene constructores públicos");
        }

        Constructor<?> mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Constructor<?> c : constructores) {
            Set<String> esperados = nombresDe(c);
            int distancia = diferencia(esperados, recibidos).size() + diferencia(recibidos, esperados).size();
            if (distancia < mejorDistancia
                    || (distancia == mejorDistancia && c.getParameterCount() > mejor.getParameterCount())) {
                mejor = c;
                mejorDistancia = distancia;
            }
        }

        Set<String> esperados = nombresDe(mejor);
        Set<String> faltan = diferencia(esperados, recibidos);
        Set<String> sobran = diferencia(recibidos, esperados);
        if (!faltan.isEmpty()) {
            throw new ParametrosInvalidosException("Faltan parámetros para crear '" + clase.getSimpleName()
                    + "': " + faltan + ". Constructor esperado: " + firma(mejor));
        }
        if (!sobran.isEmpty()) {
            throw new ParametrosInvalidosException("Parámetros no reconocidos para '" + clase.getSimpleName()
                    + "': " + sobran + ". Constructor esperado: " + firma(mejor));
        }
        return mejor;
    }

    private Object convertir(Class<?> clase, Parameter parametro, Object valor) {
        Class<?> destino = ClassUtils.resolvePrimitiveIfNecessary(parametro.getType());
        try {
            Object convertido;
            if (valor == null || destino.isInstance(valor)) {
                convertido = valor;
            } else {
                // Spring convierte una colección en su primer elemento (o en un texto con comas): aquí
                // una lista, un objeto JSON o un array nunca son un valor válido para un parámetro simple
                if (valor instanceof java.util.Collection<?> || valor instanceof Map<?, ?> || valor.getClass().isArray()) {
                    throw new IllegalArgumentException("se esperaba un valor simple");
                }
                if (valor instanceof Number n && esEntero(destino) && n.doubleValue() != Math.rint(n.doubleValue())) {
                    throw new IllegalArgumentException("el valor tiene decimales");
                }
                convertido = CONVERSION.convert(valor, destino);
            }
            if (convertido == null && parametro.getType().isPrimitive()) {
                throw new IllegalArgumentException("el valor está vacío");
            }
            return convertido;
        } catch (ConversionException | IllegalArgumentException e) {
            throw new ParametrosInvalidosException("Tipo incorrecto para el parámetro '" + parametro.getName()
                    + "' de '" + clase.getSimpleName() + "': se esperaba "
                    + parametro.getType().getSimpleName() + " pero se recibió "
                    + (valor == null ? "null" : valor.getClass().getSimpleName() + " (" + valor + ")"));
        }
    }

    private static boolean esEntero(Class<?> tipo) {
        return tipo == Integer.class || tipo == Long.class || tipo == Short.class || tipo == Byte.class;
    }

    private static Set<String> nombresDe(Constructor<?> constructor) {
        Set<String> nombres = new LinkedHashSet<>();
        for (Parameter p : constructor.getParameters()) {
            if (!p.isNamePresent()) {
                throw new IllegalStateException("No se conocen los nombres de los parámetros de "
                        + constructor.getDeclaringClass().getName() + ": compílala con la opción -parameters");
            }
            nombres.add(p.getName());
        }
        return nombres;
    }

    private static Set<String> diferencia(Set<String> a, Set<String> b) {
        Set<String> resultado = new LinkedHashSet<>(a);
        resultado.removeAll(b);
        return resultado;
    }

    static String firma(Constructor<?> constructor) {
        List<String> partes = new ArrayList<>();
        Arrays.stream(constructor.getParameters())
                .forEach(p -> partes.add(p.getType().getSimpleName() + " " + p.getName()));
        return constructor.getDeclaringClass().getSimpleName()
                + partes.stream().collect(Collectors.joining(", ", "(", ")"));
    }
}
