import java.util.concurrent.locks.ReentrantLock;

public abstract class CuentaBancaria {
    private final String numeroCuenta;
    private final String titular;
    protected double saldo;
    private final ReentrantLock lock = new ReentrantLock();

    public static final class TransaccionRetiro {
        private final String numeroCuenta;
        private final double montoRetirado;
        private final double comisionAplicada;
        private boolean revertida = false;

        private TransaccionRetiro(String numeroCuenta, double montoRetirado, double comisionAplicada) {
            this.numeroCuenta = numeroCuenta;
            this.montoRetirado = montoRetirado;
            this.comisionAplicada = comisionAplicada;
        }

        public String getNumeroCuenta() { return numeroCuenta; }
        public double getMontoRetirado() { return montoRetirado; }
        public double getComisionAplicada() { return comisionAplicada; }
        public double getImpactoTotal() { return montoRetirado + comisionAplicada; }
        public boolean isRevertida() { return revertida; }

        private void marcarRevertida() {
            this.revertida = true;
        }
    }

    public CuentaBancaria(String numeroCuenta, String titular, double saldoInicial) {
        if (saldoInicial < 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser negativo.");
        }
        this.numeroCuenta = numeroCuenta;
        this.titular = titular;
        this.saldo = saldoInicial;
    }

    public String getNumeroCuenta() { return numeroCuenta; }
    public String getTitular() { return titular; }
    public double getSaldo() { return saldo; }
    public ReentrantLock getLock() { return lock; }

    public void depositar(double monto) {
        lock.lock();
        try {
            if (monto <= 0) {
                throw new IllegalArgumentException("El monto a depositar debe ser mayor a cero.");
            }
            saldo += monto;
        } finally {
            lock.unlock();
        }
    }

    public void revertir(TransaccionRetiro tx) {
        lock.lock();
        try {
            if (tx == null) {
                throw new IllegalArgumentException("La transacción no puede ser nula.");
            }
            if (!tx.getNumeroCuenta().equals(this.numeroCuenta)) {
                throw new IllegalArgumentException("La transacción no pertenece a esta cuenta bancaria.");
            }
            if (tx.isRevertida()) {
                throw new IllegalStateException("La transacción ya fue revertida.");
            }

            this.saldo += tx.getImpactoTotal();
            tx.marcarRevertida();
        } finally {
            lock.unlock();
        }
    }

    public final TransaccionRetiro retirar(double monto, RegistroAuditoriaBancaria auditoria) 
            throws SaldoInsuficienteException {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto a retirar debe ser mayor a cero.");
        }

        lock.lock();
        try {
            ImpactoRetiro impacto = calcularImpactoRetiro(monto);

            String log = String.format(
                "RETIRO | Cuenta: %s | Titular: %s | Monto: $%.2f | Comisión: $%.2f | Total: $%.2f",
                this.numeroCuenta, this.titular, impacto.getMonto(), impacto.getComision(), impacto.getImpactoTotal()
            );
            auditoria.registrar(log);

            this.saldo -= impacto.getImpactoTotal();

            return new TransaccionRetiro(this.numeroCuenta, impacto.getMonto(), impacto.getComision());
        } finally {
            lock.unlock();
        }
    }

    protected abstract ImpactoRetiro calcularImpactoRetiro(double monto) throws SaldoInsuficienteException;
    public abstract void aplicarComisionMensual();
}