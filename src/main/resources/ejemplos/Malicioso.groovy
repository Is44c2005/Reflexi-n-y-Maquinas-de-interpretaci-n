import com.udla.figuras.model.FiguraGeometrica

/**
 * EJEMPLO MALICIOSO: debe ser RECHAZADO al registrarlo. No se sirve en el catálogo de ejemplos.
 * Si la política de seguridad fallara, crear esta figura apagaría la JVM entera.
 */
class Malicioso extends FiguraGeometrica {

    Malicioso(double lado) {
        super("Malicioso", null)
        System.exit(0)
    }

    @Override
    double calcularArea() {
        return 0
    }

    @Override
    double calcularPerimetro() {
        return 0
    }

    @Override
    void escalar(double factor) {
    }
}
