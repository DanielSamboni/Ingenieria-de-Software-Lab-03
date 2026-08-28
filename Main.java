import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        CuentaAhorros cuentaAna = new CuentaAhorros("AH-101", "Ana Gómez", 500.0, 0.02);
        CuentaCorriente cuentaCarlos = new CuentaCorriente("CC-202", "Carlos Ruiz", 100.0, 200.0, 0.05);

        try (RegistroAuditoriaBancaria auditoria = new RegistroAuditoriaBancaria(Paths.get("auditoria.log"))) {
            
            System.out.println("--- Retiro Individual con WAL ---");
            CuentaBancaria.TransaccionRetiro tx = cuentaAna.retirar(100.0, auditoria);
            System.out.printf("Retiro completado. Saldo actual Ana: $%.2f%n", cuentaAna.getSaldo());

            System.out.println("\n--- Transferencia Segura ---");
            ServicioTransferencia.transferir(cuentaAna, cuentaCarlos, 150.0, auditoria);
            System.out.printf("Saldo Ana: $%.2f | Saldo Carlos: $%.2f%n", cuentaAna.getSaldo(), cuentaCarlos.getSaldo());

            System.out.println("\n--- Concurrencia Cruzada Multi-Hilo ---");
            ExecutorService executor = Executors.newFixedThreadPool(4);

            executor.submit(() -> {
                try {
                    ServicioTransferencia.transferir(cuentaAna, cuentaCarlos, 50.0, auditoria);
                } catch (Exception e) {
                    System.err.println("Error Hilo 1: " + e.getMessage());
                }
            });

            executor.submit(() -> {
                try {
                    ServicioTransferencia.transferir(cuentaCarlos, cuentaAna, 30.0, auditoria);
                } catch (Exception e) {
                    System.err.println("Error Hilo 2: " + e.getMessage());
                }
            });

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            System.out.printf("%nSaldos Finales -> Ana: $%.2f | Carlos: $%.2f%n", cuentaAna.getSaldo(), cuentaCarlos.getSaldo());

        } catch (Exception e) {
            System.err.println("ERROR DE INFRAESTRUCTURA O DOMINIO: " + e.getMessage());
            e.printStackTrace();
        }
    }
}