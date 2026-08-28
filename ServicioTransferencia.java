public class ServicioTransferencia {

    public static void transferir(CuentaBancaria origen, CuentaBancaria destino, double monto, RegistroAuditoriaBancaria auditoria) 
            throws SaldoInsuficienteException {
        if (origen.equals(destino)) {
            throw new IllegalArgumentException("La cuenta de origen y destino no pueden ser la misma.");
        }

        CuentaBancaria primera = origen.getNumeroCuenta().compareTo(destino.getNumeroCuenta()) < 0 ? origen : destino;
        CuentaBancaria segunda = primera == origen ? destino : origen;

        primera.getLock().lock();
        segunda.getLock().lock();
        try {
            CuentaBancaria.TransaccionRetiro tx = origen.retirar(monto, auditoria);
            destino.depositar(monto);

            auditoria.registrar(String.format(
                "TRANSFERENCIA EXITOSA | De: %s -> A: %s | Monto: $%.2f",
                origen.getNumeroCuenta(), destino.getNumeroCuenta(), monto
            ));
        } finally {
            segunda.getLock().unlock();
            primera.getLock().unlock();
        }
    }
}