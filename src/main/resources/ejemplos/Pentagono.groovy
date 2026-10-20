import com.udla.figuras.model.FiguraGeometrica

/**
 * Pentágono regular de lado `lado`.
 *   área      = 5 * lado^2 / (4 * tan(36°))
 *   perímetro = 5 * lado
 */
class Pentagono extends FiguraGeometrica {

    private double lado

    Pentagono(double lado) {
        super("Pentagono", null)
        this.lado = exigirPositivo("lado", lado)
    }

    double getLado() {
        return lado
    }

    @Override
    double calcularArea() {
        return 5 * lado * lado / (4 * Math.tan(Math.toRadians(36)))
    }

    @Override
    double calcularPerimetro() {
        return 5 * lado
    }

    @Override
    void escalar(double factor) {
        this.lado *= exigirFactor(factor)
    }
}
