package com.udla.figuras.service;

import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.AttributeExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.syntax.Types;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Política de seguridad para el código Groovy que llega por la API.
 *
 * <p><b>Riesgo:</b> compilar y cargar código recibido por red es ejecución remota de código.
 * Un script podría llamar a {@code System.exit(0)}, leer archivos, lanzar procesos o usar
 * reflexión para saltarse cualquier otro control. Esta clase reduce esa superficie con tres
 * capas, todas en tiempo de compilación: el código rechazado nunca llega a cargarse.
 * <ol>
 *   <li>{@link SecureASTCustomizer} con lista blanca de imports: solo {@code Math}, tipos básicos
 *       de {@code java.lang}, colecciones, {@code BigDecimal}, {@code groovy.transform.*} y el
 *       paquete del modelo ({@code Figura}, {@code FiguraGeometrica}).</li>
 *   <li>Un visitante propio, {@link VisitanteSeguro}, que aplica la misma lista blanca a <i>todo</i>
 *       tipo usado en el código (también los que Groovy importa por defecto, como System o File,
 *       y los nombres totalmente cualificados) y prohíbe llamadas de reflexión y de evaluación
 *       dinámica. Existe porque {@code SecureASTCustomizer} solo recorre los métodos: no mira
 *       constructores, inicializadores estáticos ni inicializadores de campos, y ahí es donde
 *       un {@code System.exit(0)} pasaba sin ser visto.</li>
 *   <li>Anotaciones: solo una lista corta de {@code groovy.transform} y sin alias en los
 *       imports. {@code @ASTTest} y {@code @Grab} ejecutan código o descargan JARs durante la
 *       propia compilación, antes de cualquier otra comprobación.</li>
 * </ol>
 *
 * <p><b>Límites (esto NO es un sandbox):</b> las llamadas sobre objetos sin tipo se vigilan por
 * nombre de método, y Groovy es dinámico, así que existen formas de ocultarlas que esta lista no
 * cubre. Tampoco se limita CPU ni memoria: un {@code while (true)} dentro de
 * {@code calcularArea} bloquea un hilo. Para código no confiable de verdad hay que ejecutarlo en
 * otro proceso o contenedor sin permisos. Aquí se mitiga lo evidente y se deja constancia.
 */
final class SeguridadGroovy {

    static final List<String> IMPORTS_PERMITIDOS = List.of(
            "java.lang.Object", "java.lang.String", "java.lang.Math", "java.lang.Number",
            "java.lang.Double", "java.lang.Float", "java.lang.Integer", "java.lang.Long",
            "java.lang.Short", "java.lang.Byte", "java.lang.Boolean", "java.lang.Character",
            "java.lang.StringBuilder", "java.lang.Comparable", "java.lang.Iterable",
            "java.lang.Exception", "java.lang.RuntimeException", "java.lang.IllegalArgumentException",
            "java.lang.IllegalStateException", "java.lang.ArithmeticException",
            "java.lang.NumberFormatException", "java.lang.UnsupportedOperationException",
            "java.lang.Override", "java.lang.Deprecated", "java.lang.SuppressWarnings",
            "java.math.BigDecimal", "java.math.BigInteger", "java.math.RoundingMode", "java.math.MathContext",
            "java.util.List", "java.util.ArrayList", "java.util.Map", "java.util.HashMap",
            "java.util.Set", "java.util.HashSet", "java.util.Objects");

    static final List<String> STAR_IMPORTS_PERMITIDOS = List.of(
            "groovy.transform.*", "com.udla.figuras.model.*");

    /** Anotaciones permitidas (nombre simple). Excluye @ASTTest, @Grab*, @AnnotationCollector... */
    static final Set<String> ANOTACIONES_PERMITIDAS = Set.of(
            "CompileStatic", "TypeChecked", "CompileDynamic", "EqualsAndHashCode", "ToString",
            "TupleConstructor", "Immutable", "Canonical", "Memoized", "Override", "Deprecated",
            "SuppressWarnings", "FunctionalInterface");

    static final Set<String> METODOS_PROHIBIDOS = Set.of(
            // reflexión
            "getClass", "forName", "newInstance", "getMetaClass", "setMetaClass", "getClassLoader",
            "getDeclaredMethod", "getDeclaredMethods", "getDeclaredField", "getDeclaredFields",
            "getDeclaredConstructor", "getDeclaredConstructors", "getMethod", "getMethods",
            "getField", "getFields", "getConstructor", "getConstructors", "setAccessible",
            "getProperty", "setProperty", "getProperties", "invokeMethod", "invoke", "metaClass",
            "getAt", "putAt", "with", "dump", "inspect",
            // evaluación de código y procesos
            "execute", "evaluate", "exec", "eval", "parse", "parseClass", "run", "getRuntime", "exit",
            "halt", "load", "loadLibrary", "start", "sleep", "wait", "notify", "notifyAll",
            // archivos y red a través de los métodos que Groovy añade a String, File y URL
            "toURL", "toURI", "toFile", "toPath", "getText", "getBytes", "readLines", "eachLine",
            "newReader", "newWriter", "newInputStream", "newOutputStream", "withReader", "withWriter",
            "withInputStream", "withOutputStream", "eachFile", "eachDir", "eachFileRecurse",
            "write", "append", "delete", "deleteDir", "mkdir", "mkdirs", "createTempFile");

    static final Set<String> PROPIEDADES_PROHIBIDAS = Set.of(
            "class", "metaClass", "classLoader", "declaredFields", "declaredMethods", "properties",
            "text", "bytes", "in", "out", "err");

    /**
     * Anotaciones que actúan durante la compilación. @Grab lo hace en una transformación global que se
     * ejecuta antes que cualquier comprobación del AST, así que se descarta mirando el texto fuente.
     * Es conservador a propósito: también salta si aparece dentro de un comentario o de un texto.
     */
    private static final Pattern ANOTACIONES_DE_COMPILACION =
            Pattern.compile("@\\s*(?:[A-Za-z_][\\w]*\\s*\\.\\s*)*(Grab\\w*|Grapes|ASTTest)\\b");

    /** Devuelve el nombre de la anotación prohibida si el texto la contiene. */
    static Optional<String> anotacionDeCompilacionProhibida(String codigo) {
        Matcher m = ANOTACIONES_DE_COMPILACION.matcher(codigo);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private SeguridadGroovy() {
    }

    static CompilerConfiguration configuracion() {
        CompilerConfiguration config = new CompilerConfiguration();
        // Sin -parameters, FiguraFactory no podría emparejar los parámetros por nombre
        config.setParameters(true);
        config.addCompilationCustomizers(anotacionesEImports(), listaBlancaDeImports(), visitanteDeCodigo());
        return config;
    }

    private static SecureASTCustomizer listaBlancaDeImports() {
        SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setAllowedImports(IMPORTS_PERMITIDOS);
        secure.setAllowedStarImports(STAR_IMPORTS_PERMITIDOS);
        secure.setAllowedStaticImports(List.of());
        secure.setAllowedStaticStarImports(List.of("java.lang.Math.*"));
        return secure;
    }

    /** Aplica la lista blanca de tipos y la lista negra de llamadas a todo el código de la clase. */
    private static CompilationCustomizer visitanteDeCodigo() {
        return new CompilationCustomizer(CompilePhase.CANONICALIZATION) {
            @Override
            public void call(SourceUnit fuente, GeneratorContext contexto, ClassNode clase) throws CompilationFailedException {
                Set<String> propias = new HashSet<>();
                fuente.getAST().getClasses().forEach(c -> propias.add(c.getName()));
                new VisitanteSeguro(fuente, propias).visitClass(clase);
            }
        };
    }

    /** Se ejecuta en CONVERSION, antes de que se apliquen las transformaciones AST (@ASTTest, @Grab). */
    private static CompilationCustomizer anotacionesEImports() {
        return new CompilationCustomizer(CompilePhase.CONVERSION) {
            @Override
            public void call(SourceUnit fuente, GeneratorContext contexto, ClassNode clase) throws CompilationFailedException {
                for (ImportNode importacion : fuente.getAST().getImports()) {
                    String simple = importacion.getType().getNameWithoutPackage();
                    if (importacion.getAlias() != null && !importacion.getAlias().equals(simple)) {
                        throw new SecurityException("No se permiten alias en los imports (" + importacion.getAlias() + ")");
                    }
                }
                new ClassCodeVisitorSupport() {
                    @Override
                    protected SourceUnit getSourceUnit() {
                        return fuente;
                    }

                    @Override
                    public void visitAnnotations(AnnotatedNode nodo) {
                        for (AnnotationNode anotacion : nodo.getAnnotations()) {
                            String nombre = anotacion.getClassNode().getNameWithoutPackage();
                            if (!ANOTACIONES_PERMITIDAS.contains(nombre)) {
                                throw new SecurityException("La anotación @" + nombre + " no está permitida");
                            }
                        }
                        super.visitAnnotations(nodo);
                    }
                }.visitClass(clase);
            }
        };
    }

    /** Recorre clase, campos, constructores, métodos, inicializadores y closures. */
    private static final class VisitanteSeguro extends ClassCodeVisitorSupport {

        private final SourceUnit fuente;
        private final Set<String> clasesPropias;

        VisitanteSeguro(SourceUnit fuente, Set<String> clasesPropias) {
            this.fuente = fuente;
            this.clasesPropias = clasesPropias;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return fuente;
        }

        // ---- tipos: lista blanca ----

        private void exigirTipo(ClassNode tipo) {
            if (tipo == null || tipo.isGenericsPlaceHolder()) {
                return;
            }
            if (tipo.isArray()) {
                exigirTipo(tipo.getComponentType());
                return;
            }
            GenericsType[] genericos = tipo.getGenericsTypes();
            if (genericos != null) {
                for (GenericsType g : genericos) {
                    if (!g.isPlaceholder() && !g.isWildcard()) {
                        exigirTipo(g.getType());
                    }
                }
            }
            if (ClassHelper.isPrimitiveType(tipo) || clasesPropias.contains(tipo.getName())
                    || IMPORTS_PERMITIDOS.contains(tipo.getName())
                    || STAR_IMPORTS_PERMITIDOS.contains(tipo.getPackageName() + ".*")) {
                return;
            }
            throw new SecurityException("El tipo '" + tipo.getName() + "' no está permitido en las figuras "
                    + "dinámicas (fuera de la lista blanca de imports)");
        }

        @Override
        public void visitClass(ClassNode nodo) {
            exigirTipo(nodo.getSuperClass());
            for (ClassNode interfaz : nodo.getInterfaces()) {
                exigirTipo(interfaz);
            }
            super.visitClass(nodo);
        }

        @Override
        public void visitField(FieldNode campo) {
            exigirTipo(campo.getType());
            super.visitField(campo);
        }

        @Override
        public void visitProperty(PropertyNode propiedad) {
            exigirTipo(propiedad.getType());
            super.visitProperty(propiedad);
        }

        @Override
        protected void visitConstructorOrMethod(MethodNode metodo, boolean esConstructor) {
            exigirTipo(metodo.getReturnType());
            for (Parameter p : metodo.getParameters()) {
                exigirTipo(p.getOriginType());
            }
            super.visitConstructorOrMethod(metodo, esConstructor);
        }

        @Override
        public void visitClosureExpression(ClosureExpression cierre) {
            if (cierre.getParameters() != null) {
                for (Parameter p : cierre.getParameters()) {
                    exigirTipo(p.getOriginType());
                }
            }
            super.visitClosureExpression(cierre);
        }

        @Override
        public void visitDeclarationExpression(DeclarationExpression declaracion) {
            Expression izquierda = declaracion.getLeftExpression();
            if (izquierda instanceof VariableExpression variable) {
                exigirTipo(variable.getOriginType());
            }
            super.visitDeclarationExpression(declaracion);
        }

        @Override
        public void visitCastExpression(CastExpression conversion) {
            exigirTipo(conversion.getType());
            super.visitCastExpression(conversion);
        }

        @Override
        public void visitClassExpression(ClassExpression expresion) {
            exigirTipo(expresion.getType());
            super.visitClassExpression(expresion);
        }

        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression llamada) {
            exigirTipo(llamada.getType());
            super.visitConstructorCallExpression(llamada);
        }

        // ---- llamadas y propiedades: lista negra de nombres ----

        @Override
        public void visitMethodCallExpression(MethodCallExpression llamada) {
            String receptor = llamada.isImplicitThis() ? "this" : llamada.getObjectExpression().getText();
            exigirNombre(llamada.getMethod(), METODOS_PROHIBIDOS, "llamar al método (sobre " + receptor + ")");
            super.visitMethodCallExpression(llamada);
        }

        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression llamada) {
            exigirNombre(llamada.getMethod(), METODOS_PROHIBIDOS, "llamar al método");
            super.visitStaticMethodCallExpression(llamada);
        }

        @Override
        public void visitMethodPointerExpression(MethodPointerExpression puntero) {
            exigirNombre(puntero.getMethodName(), METODOS_PROHIBIDOS, "referenciar el método");
            super.visitMethodPointerExpression(puntero);
        }

        @Override
        public void visitPropertyExpression(PropertyExpression propiedad) {
            exigirNombre(propiedad.getProperty(), PROPIEDADES_PROHIBIDAS, "acceder a la propiedad");
            super.visitPropertyExpression(propiedad);
        }

        @Override
        public void visitAttributeExpression(AttributeExpression atributo) {
            throw new SecurityException("El acceso directo a campos con '.@' no está permitido");
        }

        @Override
        public void visitBinaryExpression(BinaryExpression binaria) {
            // obj["class"] equivale a obj.class: se rechazan índices de texto y el uso sobre this/super
            if (binaria.getOperation().getType() == Types.LEFT_SQUARE_BRACKET) {
                Expression indice = binaria.getRightExpression();
                boolean indiceDeTexto = indice instanceof ConstantExpression c && c.getValue() instanceof String;
                boolean sobreThis = binaria.getLeftExpression() instanceof VariableExpression v && v.isThisExpression();
                if (indiceDeTexto || sobreThis || indice.getClass().getSimpleName().equals("GStringExpression")) {
                    throw new SecurityException("No se permite indexar con texto ni sobre 'this' (equivale a acceder a propiedades por nombre)");
                }
            }
            super.visitBinaryExpression(binaria);
        }

        /** Rechaza nombres prohibidos y nombres calculados en ejecución (this."$x"()). */
        private void exigirNombre(Expression nombre, Set<String> prohibidos, String accion) {
            if (!(nombre instanceof ConstantExpression constante) || !(constante.getValue() instanceof String valor)) {
                throw new SecurityException("No se permite " + accion + " con un nombre calculado en tiempo de ejecución");
            }
            exigirNombre(valor, prohibidos, accion);
        }

        private void exigirNombre(String nombre, Set<String> prohibidos, String accion) {
            if (prohibidos.contains(nombre)) {
                throw new SecurityException("No se permite " + accion + ": '" + nombre
                        + "' (reflexión, ejecución de código, procesos, archivos o red)");
            }
        }
    }
}
