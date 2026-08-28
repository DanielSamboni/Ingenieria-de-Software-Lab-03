public class CuentaAhorros extends CuentaBancaria {
    private final double tasaInteres;

    public CuentaAhorros(String numeroCuenta, String titular, double saldoInicial, double tasaInteres) {
        super(numeroCuenta, titular, saldoInicial);
        if (tasaInteres < 0) {
            throw new IllegalArgumentException("La tasa de interés no puede ser negativa.");
        }
        this.tasaInteres = tasaInteres;
    }

    @Override
    protected ImpactoRetiro calcularImpactoRetiro(double monto) throws SaldoInsuficienteException {
        if (monto > saldo) {
            throw new SaldoInsuficienteException("Saldo insuficiente en cuenta de ahorros. Saldo actual: $" + saldo);
        }
        return new ImpactoRetiro(monto, 0.0);
    }

    @Override
    public void aplicarComisionMensual() {
        getLock().lock();
        try {
            saldo += (saldo * tasaInteres);
        } finally {
            getLock().unlock();
        }
    }
}