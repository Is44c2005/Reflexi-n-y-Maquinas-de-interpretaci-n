package com.udla.figuras.service;

import com.udla.figuras.dto.ClaseInfo;
import com.udla.figuras.model.Figura;
import com.udla.figuras.service.excepcion.CodigoGroovyInvalidoException;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Compila clases Groovy en tiempo de ejecución, comprueba que sean figuras y las registra en
 * {@link RegistroClases}; desde ahí {@link FiguraFactory} las trata igual que a una nativa.
 */
@Service
public class CompiladorGroovy {

    private static final Logger log = LoggerFactory.getLogger(CompiladorGroovy.class);

    static final int MAX_CARACTERES = 50_000;
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final String MARCA_ORIGEN = "// origen: ";

    private final RegistroClases registro;
    private final Path directorio;

    public CompiladorGroovy(RegistroClases registro,
                            @Value("${figuras.groovy.dir:./figuras-groovy}") String directorio) {
        this.registro = registro;
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    /** Compila, valida, registra y guarda en disco el código de una clase Groovy. */
    public ClaseInfo registrar(String codigo, OrigenClase origen) {
        return registrar(codigo, origen, null);
    }

    /**
     * Igual que {@link #registrar(String, OrigenClase)}, pero exige que la clase del código se llame
     * {@code nombreEsperado} (comprobado antes de registrar ni guardar nada). Lo usa el catálogo remoto:
     * si se pidió "Pentagono" y el servicio devuelve otra clase, se rechaza.
     */
    public ClaseInfo registrar(String codigo, OrigenClase origen, String nombreEsperado) {
        Class<?> clase = compilarYRegistrar(codigo, origen, nombreEsperado);
        guardar(clase.getSimpleName(), codigo, origen);
        return new ClaseInfo(clase.getSimpleName(), clase.getName(), origen);
    }

    /** Vuelve a cargar los .groovy guardados. Un archivo que falle se omite sin detener el arranque. */
    public int recargarGuardadas() {
        if (!Files.isDirectory(directorio)) {
            return 0;
        }
        int cargadas = 0;
        try (Stream<Path> archivos = Files.list(directorio)) {
            List<Path> groovy = archivos.filter(p -> p.getFileName().toString().endsWith(".groovy")).sorted().toList();
            for (Path archivo : groovy) {
                try {
                    String codigo = Files.readString(archivo, StandardCharsets.UTF_8);
                    compilarYRegistrar(codigo, origenGuardado(codigo), null);
                    cargadas++;
                } catch (IOException | RuntimeException e) {
                    log.warn("No se pudo recargar {}: {}", archivo.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo leer la carpeta {}: {}", directorio, e.getMessage());
        }
        log.info("Clases Groovy recargadas desde {}: {}", directorio, cargadas);
        return cargadas;
    }

    private Class<?> compilarYRegistrar(String codigo, OrigenClase origen, String nombreEsperado) {
        if (codigo == null || codigo.isBlank()) {
            throw new CodigoGroovyInvalidoException("El código Groovy está vacío");
        }
        if (codigo.length() > MAX_CARACTERES) {
            throw new CodigoGroovyInvalidoException("El código Groovy supera el máximo de " + MAX_CARACTERES + " caracteres");
        }

        SeguridadGroovy.anotacionDeCompilacionProhibida(codigo).ifPresent(anotacion -> {
            throw new CodigoGroovyInvalidoException("La anotación @" + anotacion
                    + " no está permitida: se ejecuta o descarga código durante la compilación");
        });

        Class<?> clase = compilar(codigo);
        verificar(clase);
        if (nombreEsperado != null && !nombreEsperado.equals(clase.getSimpleName())) {
            throw new CodigoGroovyInvalidoException("Se pidió la clase '" + nombreEsperado
                    + "' pero el código define '" + clase.getSimpleName() + "'");
        }
        try {
            registro.registrar(clase, origen);
        } catch (IllegalArgumentException e) {
            throw new CodigoGroovyInvalidoException(e.getMessage(), e);
        }
        log.info("Clase Groovy registrada: {} ({})", clase.getSimpleName(), origen);
        return clase;
    }

    private Class<?> compilar(String codigo) {
        // El padre es el classloader de la aplicación, el que ve Figura y FiguraGeometrica. Se usa el de
        // la propia clase Figura y no el del sistema, porque en un fat jar de Spring Boot son distintos.
        ClassLoader padre = Figura.class.getClassLoader();
        try {
            // Un cargador por compilación: así se puede redefinir una clase sin conflictos de nombre
            GroovyClassLoader cargador = new GroovyClassLoader(padre, SeguridadGroovy.configuracion());
            return cargador.parseClass(codigo, "figura.groovy");
        } catch (Exception | LinkageError e) {
            throw new CodigoGroovyInvalidoException("No se pudo compilar el código Groovy: " + resumir(e), e);
        }
    }

    private void verificar(Class<?> clase) {
        String nombre = clase.getSimpleName();
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            throw new CodigoGroovyInvalidoException("El nombre de clase '" + nombre
                    + "' no es válido: use letras, números y guion bajo, empezando por letra");
        }
        if (!Figura.class.isAssignableFrom(clase)) {
            throw new CodigoGroovyInvalidoException("La clase '" + nombre
                    + "' debe implementar Figura o extender FiguraGeometrica");
        }
        if (clase.isInterface() || Modifier.isAbstract(clase.getModifiers()) || !Modifier.isPublic(clase.getModifiers())) {
            throw new CodigoGroovyInvalidoException("La clase '" + nombre + "' debe ser pública y concreta");
        }
        for (Constructor<?> constructor : clase.getConstructors()) {
            if (constructor.getParameters().length > 0 && !constructor.getParameters()[0].isNamePresent()) {
                throw new CodigoGroovyInvalidoException("No se pudieron leer los nombres de los parámetros de " + nombre);
            }
        }
    }

    private void guardar(String nombre, String codigo, OrigenClase origen) {
        try {
            Files.createDirectories(directorio);
            // Se anota el origen en la primera línea para conservarlo al recargar
            String contenido = origen == OrigenClase.GROOVY ? codigo : MARCA_ORIGEN + origen + System.lineSeparator() + codigo;
            Files.writeString(directorio.resolve(nombre + ".groovy"), contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // La clase ya está registrada y funciona en esta sesión; solo se perderá al reiniciar
            log.warn("No se pudo guardar {}.groovy en {}: {}", nombre, directorio, e.getMessage());
        }
    }

    private OrigenClase origenGuardado(String codigo) {
        String primera = codigo.lines().findFirst().orElse("");
        if (primera.startsWith(MARCA_ORIGEN)) {
            try {
                return OrigenClase.valueOf(primera.substring(MARCA_ORIGEN.length()).trim());
            } catch (IllegalArgumentException e) {
                // origen desconocido: se trata como Groovy local
            }
        }
        return OrigenClase.GROOVY;
    }

    /** Solo el mensaje de cada error de compilación, sin la traza interna de Groovy. */
    private static String resumir(Throwable e) {
        String mensaje;
        if (e instanceof MultipleCompilationErrorsException errores) {
            List<String> partes = new ArrayList<>();
            for (Object error : errores.getErrorCollector().getErrors()) {
                if (error instanceof ExceptionMessage m) {
                    partes.add(m.getCause().getMessage());
                } else if (error instanceof SyntaxErrorMessage m) {
                    partes.add(m.getCause().getMessage());
                } else {
                    partes.add(String.valueOf(error));
                }
            }
            mensaje = String.join("; ", partes);
        } else {
            mensaje = String.valueOf(e.getMessage());
        }
        mensaje = mensaje.strip();
        return mensaje.length() > 600 ? mensaje.substring(0, 600) + "..." : mensaje;
    }
}
