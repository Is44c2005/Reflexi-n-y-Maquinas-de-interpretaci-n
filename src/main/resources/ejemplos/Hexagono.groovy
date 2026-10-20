import com.udla.figuras.model.FiguraGeometrica

/**
 * Hexágono regular de lado `lado`.
 *   área      = 3 * raíz(3) / 2 * lado^2
 *   perímetro = 6 * lado
 */
class Hexagono extends FiguraGeometrica {

    private double lado

    Hexagono(double lado) {
        super("Hexagono", null)
        this.lado = exigirPositivo("lado", lado)
    }

    double getLado() {
        return lado
    }

    @Override
    double calcularArea() {
        return 3 * Math.sqrt(3) / 2 * lado * lado
    }

    @Override
    double calcularPerimetro() {
        return 6 * lado
    }

    @Override
    void escalar(double factor) {
        this.lado *= exigirFactor(factor)
    }
}
