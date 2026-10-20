package com.udla.figuras.service;

import com.udla.figuras.config.EscanerFiguras;
import com.udla.figuras.dto.ClaseInfo;
import com.udla.figuras.model.Circulo;
import com.udla.figuras.model.Figura;
import com.udla.figuras.model.GestorFiguras;
import com.udla.figuras.model.Rectangulo;
import com.udla.figuras.model.Triangulo;
import com.udla.figuras.service.excepcion.CodigoGroovyInvalidoException;
import com.udla.figuras.service.excepcion.ParametrosInvalidosException;
import com.udla.figuras.service.excepcion.ValidacionFiguraException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Sin contexto de Spring: cada test arma su propio registro y su propia carpeta temporal. */
class CompiladorGroovyTest {

    @TempDir
    Path carpeta;

    RegistroClases registro;
    FiguraFactory factory;
    CompiladorGroovy compilador;

    @BeforeEach
    void preparar() {
        registro = registroConNativas();
        factory = new FiguraFactory(registro);
        compilador = new CompiladorGroovy(registro, carpeta.toString());
    }

    static RegistroClases registroConNativas() {
        RegistroClases registro = new RegistroClases();
        EscanerFiguras.escanear("com.udla.figuras.model", CompiladorGroovyTest.class.getClassLoader())
                .forEach(c -> registro.registrar(c, OrigenClase.NATIVA));
        return registro;
    }

    static String ejemplo(String nombre) throws IOException {
        try (InputStream in = CompiladorGroovyTest.class.getResourceAsStream("/ejemplos/" + nombre + ".groovy")) {
            assertThat(in).as("ejemplo " + nombre).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- 5. Registrar Pentagono en tiempo de ejecución y crearlo como si fuera nativo ----

    @Test
    void registraPentagonoYLoCreaConLaFabricaComoSiFueraNativo() throws IOException {
        assertThat(registro.contiene("Pentagono")).isFalse();
        assertThatThrownBy(() -> factory.crear("Pentagono", Map.of("lado", 2)))
                .hasMessageContaining("no está registrada");

        ClaseInfo info = compilador.registrar(ejemplo("Pentagono"), OrigenClase.GROOVY);

        assertThat(info.nombre()).isEqualTo("Pentagono");
        assertThat(info.origen()).isEqualTo(OrigenClase.GROOVY);
        assertThat(registro.origenDe("Pentagono")).isEqualTo(OrigenClase.GROOVY);

        // Mismo mecanismo que una nativa: parámetros por nombre y conversión de tipos
        Figura pentagono = factory.crear("Pentagono", Map.of("lado", 2));
        assertThat(pentagono.getNombre()).isEqualTo("Pentagono");
        assertThat(pentagono.calcularArea()).isCloseTo(6.8819, within(0.0001));   // 5·4 / (4·tan 36°)
        assertThat(pentagono.calcularPerimetro()).isCloseTo(10.0, within(1e-9));
        assertThat(factory.calcularArea(pentagono)).isCloseTo(6.8819, within(0.0001)); // por Method.invoke
        assertThat(factory.calcularPerimetro(pentagono)).isCloseTo(10.0, within(1e-9));
        assertThat(pentagono.describir()).contains("Pentagono").contains("6.88").contains("10.00");
    }

    @Test
    void laFiguraGroovyValidaSusMedidasConElMismoMensajeDeSiempre() throws IOException {
        compilador.registrar(ejemplo("Pentagono"), OrigenClase.GROOVY);

        assertThatThrownBy(() -> factory.crear("Pentagono", Map.of("lado", -1)))
                .isInstanceOf(ValidacionFiguraException.class)
                .hasMessageContaining("lado")
                .hasMessageContaining("mayor que 0");
        assertThatThrownBy(() -> factory.crear("Pentagono", Map.of()))
                .isInstanceOf(ParametrosInvalidosException.class)
                .hasMessageContaining("lado");
    }

    @Test
    void laFiguraGroovyEsEscalable() throws IOException {
        compilador.registrar(ejemplo("Hexagono"), OrigenClase.GROOVY);

        Figura hexagono = factory.crear("Hexagono", Map.of("lado", 1));
        double areaInicial = hexagono.calcularArea();
        ((com.udla.figuras.model.Escalable) hexagono).escalar(2);

        assertThat(hexagono.calcularArea()).isCloseTo(areaInicial * 4, within(1e-9));
        assertThat(hexagono.calcularPerimetro()).isCloseTo(12.0, within(1e-9));
    }

    @Test
    void volverARegistrarUnaClaseDinamicaLaRedefine() throws IOException {
        String plantilla = """
                import com.udla.figuras.model.FiguraGeometrica
                class Variante extends FiguraGeometrica {
                    private double lado
                    Variante(double lado) { super("Variante", null); this.lado = lado }
                    double calcularArea() { lado }
                    double calcularPerimetro() { %d * lado }
                    void escalar(double f) { lado *= f }
                }
                """;
        compilador.registrar(plantilla.formatted(5), OrigenClase.GROOVY);
        assertThat(factory.crear("Variante", Map.of("lado", 1)).calcularPerimetro()).isEqualTo(5.0);

        compilador.registrar(plantilla.formatted(99), OrigenClase.GROOVY);
        assertThat(factory.crear("Variante", Map.of("lado", 1)).calcularPerimetro()).isEqualTo(99.0);
    }

    // ---- 6. Rechazo de código malicioso ----

    @Test
    void rechazaElScriptMaliciosoDeEjemplo() throws IOException {
        assertThatThrownBy(() -> compilador.registrar(ejemplo("Malicioso"), OrigenClase.GROOVY))
                .isInstanceOf(CodigoGroovyInvalidoException.class)
                .hasMessageContaining("System");

        assertThat(registro.contiene("Malicioso")).isFalse();
        assertThat(carpeta.resolve("Malicioso.groovy")).doesNotExist();
    }

    static String clase(String antes, String cuerpoConstructor) {
        return antes + """
                import com.udla.figuras.model.FiguraGeometrica
                class Malo extends FiguraGeometrica {
                    Malo(double lado) { super("Malo", null); %s }
                    double calcularArea() { 0 }
                    double calcularPerimetro() { 0 }
                    void escalar(double f) { }
                }
                """.formatted(cuerpoConstructor);
    }

    /** Clase inofensiva en sí misma, con la anotación puesta sobre la clase. */
    static String conAnotacion(String imports, String anotacion) {
        return imports + """
                import com.udla.figuras.model.FiguraGeometrica
                %s
                class Malo extends FiguraGeometrica {
                    Malo(double lado) { super("Malo", null) }
                    double calcularArea() { 0 }
                    double calcularPerimetro() { 0 }
                    void escalar(double f) { }
                }
                """.formatted(anotacion);
    }

    static Stream<Arguments> scriptsPeligrosos() {
        return Stream.of(
                Arguments.of("System.exit", clase("", "System.exit(0)")),
                Arguments.of("System cualificado", clase("", "java.lang.System.exit(0)")),
                Arguments.of("Runtime.exec", clase("", "Runtime.getRuntime().exec('calc')")),
                Arguments.of("ProcessBuilder", clase("", "new ProcessBuilder('calc').start()")),
                Arguments.of("File por defecto", clase("", "new File('x.txt').text = 'hola'")),
                Arguments.of("File cualificado", clase("", "new java.io.File('x').delete()")),
                Arguments.of("import File", clase("import java.io.File\n", "new File('x').delete()")),
                Arguments.of("import estático de System", clase("import static java.lang.System.*\n", "exit(0)")),
                Arguments.of("import estático de Runtime", clase("import static java.lang.Runtime.getRuntime\n", "getRuntime()")),
                Arguments.of("Files y Paths", clase("", "java.nio.file.Files.readAllLines(java.nio.file.Paths.get('x'))")),
                Arguments.of("red", clase("", "new URL('http://localhost').text")),
                Arguments.of("Class.forName", clase("", "Class.forName('java.lang.System')")),
                Arguments.of("getClass", clase("", "lado.getClass().getClassLoader()")),
                Arguments.of("propiedad class", clase("", "def c = this.class")),
                Arguments.of("propiedad metaClass", clase("", "def m = this.metaClass")),
                Arguments.of("campo con .@", clase("", "def v = this.@lado")),
                Arguments.of("String.execute", clase("", "'calc'.execute()")),
                Arguments.of("evaluate", clase("", "evaluate('1+1')")),
                Arguments.of("GroovyShell", clase("", "new GroovyShell().evaluate('1')")),
                Arguments.of("Eval", clase("", "Eval.me('1')")),
                Arguments.of("método calculado", clase("", "this.\"${'ex' + 'it'}\"(0)")),
                Arguments.of("puntero a método", clase("", "def f = System.&exit; f(0)")),
                Arguments.of("closure con getClass", clase("", "[1].each { it.getClass() }")),
                Arguments.of("Thread.sleep", clase("", "Thread.sleep(100000)")),
                Arguments.of("inicializador estático", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Malo extends FiguraGeometrica {
                            static { System.exit(0) }
                            Malo(double lado) { super("Malo", null) }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """),
                Arguments.of("@ASTTest", conAnotacion("import groovy.transform.ASTTest\n",
                        "@ASTTest(value = { System.exit(0) })")),
                Arguments.of("@ASTTest cualificado", conAnotacion("",
                        "@groovy.transform.ASTTest(value = { System.exit(0) })")),
                Arguments.of("@Grab", conAnotacion("", "@Grab('org.apache.commons:commons-lang3:3.14.0')")),
                Arguments.of("alias de import", conAnotacion("import groovy.transform.ASTTest as CompileStatic\n",
                        "@CompileStatic(value = { System.exit(0) })")),
                Arguments.of("paquete interno de la app", clase("", "com.udla.figuras.service.RegistroClases.class")),
                Arguments.of("no es una figura", "class Otra { }"),
                Arguments.of("figura abstracta", """
                        import com.udla.figuras.model.FiguraGeometrica
                        abstract class Abstracta extends FiguraGeometrica {
                            Abstracta() { super("A", null) }
                        }
                        """),
                Arguments.of("sintaxis inválida", "class Roto extends { ((( "),
                Arguments.of("reemplazar una nativa", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Circulo extends FiguraGeometrica {
                            Circulo() { super("Circulo", null) }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """),
                Arguments.of("vacío", "   "),
                // constructores e inicializadores: SecureASTCustomizer por sí solo no los recorre
                Arguments.of("new File asignado", clase("", "def f = new File('x')")),
                Arguments.of("tipo File declarado", clase("", "File f = null")),
                Arguments.of("conversión a File", clase("", "def f = 'x' as File")),
                Arguments.of("System.out", clase("", "System.out.println('hola')")),
                Arguments.of("índice de texto", clase("", "def v = this['class']")),
                Arguments.of("índice sobre this", clase("", "def k = 'class'; def v = this[k]")),
                Arguments.of("campo con inicializador peligroso", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Malo extends FiguraGeometrica {
                            def x = System.getenv()
                            Malo(double lado) { super("Malo", null) }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """),
                Arguments.of("interfaz fuera de la lista", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Malo extends FiguraGeometrica implements Runnable {
                            Malo(double lado) { super("Malo", null) }
                            void run() { }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """),
                Arguments.of("parámetro de tipo prohibido", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Malo extends FiguraGeometrica {
                            Malo(double lado) { super("Malo", null) }
                            void usar(Process p) { }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """));
    }

    /** Evita que un rechazo pase "por casualidad" (plantilla mal armada, error de otro tipo...). */
    @ParameterizedTest(name = "motivo de {0}")
    @MethodSource("motivosEsperados")
    void rechazaPorElMotivoCorrecto(String descripcion, String codigo, String fragmento) {
        assertThatThrownBy(() -> compilador.registrar(codigo, OrigenClase.GROOVY))
                .isInstanceOf(CodigoGroovyInvalidoException.class)
                .hasMessageContaining(fragmento);
    }

    static Stream<Arguments> motivosEsperados() {
        return Stream.of(
                Arguments.of("System.exit", clase("", "System.exit(0)"), "java.lang.System"),
                Arguments.of("Runtime.exec", clase("", "Runtime.getRuntime().exec('calc')"), "java.lang.Runtime"),
                Arguments.of("new File", clase("", "def f = new File('x')"), "java.io.File"),
                Arguments.of("tipo File declarado", clase("", "File f = null"), "java.io.File"),
                Arguments.of("conversión a File", clase("", "def f = 'x' as File"), "java.io.File"),
                Arguments.of("campo con inicializador", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Malo extends FiguraGeometrica {
                            def x = System.getenv()
                            Malo(double lado) { super("Malo", null) }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """, "java.lang.System"),
                Arguments.of("interfaz Runnable", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Malo extends FiguraGeometrica implements Runnable {
                            Malo(double lado) { super("Malo", null) }
                            void run() { }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """, "java.lang.Runnable"),
                Arguments.of("parámetro Process", """
                        import com.udla.figuras.model.FiguraGeometrica
                        class Malo extends FiguraGeometrica {
                            Malo(double lado) { super("Malo", null) }
                            void usar(Process p) { }
                            double calcularArea() { 0 }
                            double calcularPerimetro() { 0 }
                            void escalar(double f) { }
                        }
                        """, "java.lang.Process"),
                Arguments.of("import de File", clase("import java.io.File\n", ""), "java.io.File"),
                Arguments.of("@Grab", conAnotacion("", "@Grab('org.apache.commons:commons-lang3:3.14.0')"), "@Grab"),
                Arguments.of("@Grapes", conAnotacion("", "@Grapes([@Grab('a:b:1')])"), "@Grapes"),
                Arguments.of("@GrabResolver", conAnotacion("", "@GrabResolver('http://example.com')"), "@GrabResolver"),
                Arguments.of("@ASTTest", conAnotacion("import groovy.transform.ASTTest\n",
                        "@ASTTest(value = { System.exit(0) })"), "@ASTTest"),
                Arguments.of("alias de import", conAnotacion("import groovy.transform.ASTTest as CompileStatic\n",
                        "@CompileStatic(value = { System.exit(0) })"), "alias"),
                Arguments.of("this con índice", clase("", "def v = this['class']"), "indexar"),
                Arguments.of("método calculado", clase("", "this.\"${'ex' + 'it'}\"(0)"), "nombre calculado"));
    }

    @ParameterizedTest(name = "rechaza: {0}")
    @MethodSource("scriptsPeligrosos")
    void rechazaScriptsPeligrososOInvalidos(String descripcion, String codigo) {
        assertThatThrownBy(() -> compilador.registrar(codigo, OrigenClase.GROOVY))
                .as(descripcion)
                .isInstanceOf(CodigoGroovyInvalidoException.class);

        assertThat(registro.clases().keySet()).containsExactly("Circulo", "Rectangulo", "Triangulo");
        assertThat(carpeta.toFile().list()).as("no se guarda nada").isNullOrEmpty();
    }

    @Test
    void elMensajeDeRechazoExplicaElMotivo() {
        assertThatThrownBy(() -> compilador.registrar(clase("", "'calc'.execute()"), OrigenClase.GROOVY))
                .hasMessageContaining("execute");
        assertThatThrownBy(() -> compilador.registrar("class Otra { }", OrigenClase.GROOVY))
                .hasMessageContaining("debe implementar Figura");
        assertThatThrownBy(() -> compilador.registrar("class Roto extends { ((( ", OrigenClase.GROOVY))
                .hasMessageContaining("No se pudo compilar");
    }

    // ---- lo que SÍ debe permitirse ----

    @Test
    void permiteUnaFiguraLegitimaConClosuresColeccionesYBigDecimal() {
        String codigo = """
                import com.udla.figuras.model.*
                import groovy.transform.CompileStatic
                import java.math.BigDecimal

                @CompileStatic
                class Estrella extends FiguraGeometrica {
                    private double lado
                    Estrella(double lado) { super("Estrella", "Amarillo"); this.lado = exigirPositivo("lado", lado) }
                    double calcularArea() {
                        List<Double> partes = [lado, lado * 2].collect { Double d -> d * d }
                        return partes.sum() as double
                    }
                    double calcularPerimetro() { new BigDecimal(10).multiply(new BigDecimal(lado)).doubleValue() }
                    void escalar(double f) { lado *= exigirFactor(f) }
                }
                """;

        compilador.registrar(codigo, OrigenClase.GROOVY);

        Figura estrella = factory.crear("Estrella", Map.of("lado", 2));
        assertThat(estrella.calcularArea()).isEqualTo(20.0);
        assertThat(estrella.calcularPerimetro()).isEqualTo(20.0);
    }

    @Test
    void permiteImportsEstaticosDeMathExcepcionesYCamposTipados() {
        String codigo = """
                import com.udla.figuras.model.FiguraGeometrica
                import static java.lang.Math.*

                class Disco extends FiguraGeometrica {
                    private final double radio
                    private final List<Double> historial = new ArrayList<>()

                    Disco(double radio) {
                        super("Disco", "Azul")
                        try {
                            this.radio = exigirPositivo("radio", radio)
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Disco inválido: " + e.getMessage())
                        }
                        historial.add(radio)
                    }
                    double calcularArea() { PI * pow(radio, 2) }
                    double calcularPerimetro() { 2 * PI * radio }
                    void escalar(double f) { exigirFactor(f) }
                }
                """;

        compilador.registrar(codigo, OrigenClase.GROOVY);

        assertThat(factory.crear("Disco", Map.of("radio", 5)).calcularArea()).isCloseTo(78.54, within(0.01));
        assertThatThrownBy(() -> factory.crear("Disco", Map.of("radio", -1)))
                .isInstanceOf(ValidacionFiguraException.class)
                .hasMessageContaining("Disco inválido");
    }

    @Test
    void elControlDeLaPlantillaDePruebaAceptaUnCuerpoInofensivo() {
        // Garantiza que los rechazos de arriba se deben al contenido y no a la plantilla
        compilador.registrar(clase("", "def x = lado * 2"), OrigenClase.GROOVY);
        assertThat(registro.contiene("Malo")).isTrue();
    }

    @Test
    void permiteImportsDelModeloExplicitos() throws IOException {
        // Pentagono usa 'import com.udla.figuras.model.FiguraGeometrica' (import explícito)
        assertThat(compilador.registrar(ejemplo("Pentagono"), OrigenClase.GROOVY).nombre()).isEqualTo("Pentagono");
    }

    // ---- persistencia ----

    @Test
    void guardaLosAceptadosYLosRecargaAlArrancar() throws IOException {
        compilador.registrar(ejemplo("Pentagono"), OrigenClase.GROOVY);
        compilador.registrar(ejemplo("Hexagono"), OrigenClase.REMOTA);
        assertThat(carpeta.resolve("Pentagono.groovy")).exists();
        assertThat(carpeta.resolve("Hexagono.groovy")).exists();

        // "Reinicio": registro nuevo con solo las nativas y mismo directorio
        RegistroClases registroNuevo = registroConNativas();
        CompiladorGroovy compiladorNuevo = new CompiladorGroovy(registroNuevo, carpeta.toString());
        assertThat(registroNuevo.contiene("Pentagono")).isFalse();

        assertThat(compiladorNuevo.recargarGuardadas()).isEqualTo(2);

        assertThat(registroNuevo.origenDe("Pentagono")).isEqualTo(OrigenClase.GROOVY);
        assertThat(registroNuevo.origenDe("Hexagono")).isEqualTo(OrigenClase.REMOTA);
        assertThat(new FiguraFactory(registroNuevo).crear("Pentagono", Map.of("lado", 2)).calcularPerimetro())
                .isEqualTo(10.0);
    }

    @Test
    void unArchivoRotoOPeligrosoEnLaCarpetaNoDetieneLaRecarga() throws IOException {
        Files.writeString(carpeta.resolve("Roto.groovy"), "class Roto extends { (((");
        Files.writeString(carpeta.resolve("Malicioso.groovy"), ejemplo("Malicioso"));
        Files.writeString(carpeta.resolve("Pentagono.groovy"), ejemplo("Pentagono"));
        Files.writeString(carpeta.resolve("notas.txt"), "no es groovy");

        assertThat(compilador.recargarGuardadas()).isEqualTo(1);

        assertThat(registro.contiene("Pentagono")).isTrue();
        assertThat(registro.contiene("Malicioso")).isFalse();
    }

    @Test
    void recargarSinCarpetaNoFalla() {
        CompiladorGroovy sinCarpeta = new CompiladorGroovy(registro, carpeta.resolve("no-existe").toString());
        assertThat(sinCarpeta.recargarGuardadas()).isZero();
    }

    // ---- 8. Polimorfismo con figuras nativas y dinámicas mezcladas ----

    @Test
    void areaTotalMezclaFigurasNativasYDinamicasSinConocerlas() throws IOException {
        compilador.registrar(ejemplo("Pentagono"), OrigenClase.GROOVY);
        compilador.registrar(ejemplo("Hexagono"), OrigenClase.GROOVY);

        GestorFiguras gestor = new GestorFiguras();
        gestor.agregar(factory.crear("Circulo", Map.of("radio", 1)));
        gestor.agregar(factory.crear("Rectangulo", Map.of("base", 2, "altura", 3)));
        gestor.agregar(factory.crear("Triangulo", Map.of("ladoA", 3, "ladoB", 4, "ladoC", 5)));
        gestor.agregar(factory.crear("Pentagono", Map.of("lado", 2)));
        gestor.agregar(factory.crear("Hexagono", Map.of("lado", 2)));

        double esperado = new Circulo(1).calcularArea() + new Rectangulo(2, 3).calcularArea()
                + new Triangulo(3, 4, 5).calcularArea()
                + 5 * 4 / (4 * Math.tan(Math.toRadians(36)))
                + 3 * Math.sqrt(3) / 2 * 4;
        assertThat(gestor.areaTotal()).isCloseTo(esperado, within(1e-9));
        assertThat(gestor.getFiguras()).extracting(Figura::getNombre)
                .containsExactly("Circulo", "Rectangulo", "Triangulo", "Pentagono", "Hexagono");
    }
}
