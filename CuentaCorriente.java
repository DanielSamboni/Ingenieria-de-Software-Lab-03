public class CuentaCorriente extends CuentaBancaria {
    private final double cupoSobregiro;
    private final double porcentajeComisionSobregiro;

    public CuentaCorriente(String numeroCuenta, String titular, double saldoInicial, double cupoSobregiro, double porcentajeComision) {
        super(numeroCuenta, titular, saldoInicial);
        if (cupoSobregiro < 0 || porcentajeComision < 0) {
            throw new IllegalArgumentException("El cupo y porcentaje deben ser mayores o iguales a cero.");
        }
        this.cupoSobregiro = cupoSobregiro;
        this.porcentajeComisionSobregiro = porcentajeComision;
    }

    @Override
    protected ImpactoRetiro calcularImpactoRetiro(double monto) throws SaldoInsuficienteException {
        double tramoSobregiro;
        
        if (this.saldo > 0) {
            tramoSobregiro = Math.max(0.0, monto - this.saldo);
        } else {
            tramoSobregiro = monto;
        }

        double comisionDinamica = tramoSobregiro * porcentajeComisionSobregiro;

        if ((monto + comisionDinamica) > (this.saldo + cupoSobregiro)) {
            throw new SaldoInsuficienteException(
                "La operación excede el límite de sobregiro. Máximo retirable estimado: $" 
                + (this.saldo + cupoSobregiro - comisionDinamica)
            );
        }

        return new ImpactoRetiro(monto, comisionDinamica);
    }

    @Override
    public void aplicarComisionMensual() {
        getLock().lock();
        try {
            saldo -= 10.0;
        } finally {
            getLock().unlock();
        }
    }
}