# informe.md

# Informe Técnico: Arquitectura, Concurrencia y Persistencia

## 1. Hallazgos en E/S y Manejo de Memoria

* **Vulnerabilidad en java.io.FileWriter:** Las llamadas a `Thread.interrupt()` no detienen E/S bloqueante tradicional. Al ocurrir una falla en disco, el consumidor retiene el monitor intrínseco `synchronized(lock)`, congelando al hilo principal en `cerrarRecursosFisicos()`.
* **Solución java.nio.channels.FileChannel:** Al implementar `InterruptibleChannel`, las interrupciones invocan un callback nativo que invalida el descriptor de archivo en la capa de kernel del sistema operativo. Esto expulsa los hilos bloqueados inmediatamente lanzando `ClosedByInterruptException` sin contención de cerrojos.

## 2. Garantía de Durabilidad (ACID) y Patrón WAL

* **Desacoplamiento previo (Inconsistencia):** Las colas asíncronas en RAM (`ArrayBlockingQueue`) provocaban pérdida de trazas cuando la JVM o el hilo colapsaban, violando la propiedad de Durabilidad del modelo ACID.
* **Patrón Write-Ahead Logging (WAL):** Se estructuró un flujo en 3 fases:
  1. Validación
  2. Persistencia Física Síncrona vía `FileChannel.force(true)`
  3. Mutación de Saldo
  
  La transacción en RAM no se concreta a menos que los bytes impacten el almacenamiento secundario.

## 3. Concurrencia y Prevención de Interbloqueos (Deadlocks)

* **Aislamiento por Registro:** El uso de `ReentrantLock` por cada objeto `CuentaBancaria` garantiza la atomicidad completa del ciclo de 3 fases.
* **Ordenamiento Determinista de Cerrojos:** Para evitar condiciones de interbloqueo (*deadlock*) cuando dos hilos transfieren fondos cruzados de forma simultánea (Cuenta A → Cuenta B y Cuenta B → Cuenta A), la adquisición de cerrojos se ordena globalmente según el identificador lexicográfico de las cuentas (`origen.getId().compareTo(destino.getId())`). Con este orden estricto, la condición de espera circular queda matemáticamente eliminada.
