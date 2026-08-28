public final class ImpactoRetiro {
    private final double monto;
    private final double comision;

    public ImpactoRetiro(double monto, double comision) {
        this.monto = monto;
        this.comision = comision;
    }

    public double getMonto() { return monto; }
    public double getComision() { return comision; }
    public double getImpactoTotal() { return monto + comision; }
}