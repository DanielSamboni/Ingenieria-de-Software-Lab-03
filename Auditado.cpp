#include <iostream>
#include <fstream>
#include <string>
#include <stdexcept>
#include <mutex>
#include <thread>
#include <sstream>
#include <iomanip>
#include <algorithm>

using namespace std;

class SaldoInsuficienteException : public runtime_error {
public:
    explicit SaldoInsuficienteException(const string& mensaje)
        : runtime_error(mensaje) {}
};

class AuditoriaException : public runtime_error {
public:
    explicit AuditoriaException(const string& mensaje)
        : runtime_error(mensaje) {}
};

class ImpactoRetiro {
private:
    double monto;
    double comision;

public:
    ImpactoRetiro(double monto, double comision)
        : monto(monto), comision(comision) {}

    double getMonto() const { return monto; }
    double getComision() const { return comision; }
    double getImpactoTotal() const { return monto + comision; }
};

class RegistroAuditoriaBancaria {
private:
    ofstream archivo;
    mutex mtx;

public:
    explicit RegistroAuditoriaBancaria(const string& rutaArchivo) {
        archivo.open(rutaArchivo, ios::out | ios::app);
        if (!archivo.is_open()) {
            throw AuditoriaException("Fallo al abrir el archivo de auditoría.");
        }
        registrar("--- Inicio de Sesión de Auditoría ---");
    }

    ~RegistroAuditoriaBancaria() {
        try {
            if (archivo.is_open()) {
                registrar("--- Cierre Seguro de Auditoría ---");
                archivo.close();
            }
        } catch (...) {}
    }

    void registrar(const string& mensaje) {
        lock_guard<mutex> lock(mtx);
        if (!archivo.is_open()) {
            throw AuditoriaException("El canal de auditoría está cerrado.");
        }
        archivo << mensaje << "\n";
        archivo.flush();
        if (archivo.fail()) {
            throw AuditoriaException("CRÍTICO I/O: Fallo al persistir en la bitácora WAL.");
        }
    }
};

class CuentaBancaria {
public:
    class TransaccionRetiro {
    private:
        string numeroCuenta;
        double montoRetirado;
        double comisionAplicada;
        bool revertida;

        TransaccionRetiro(string numeroCuenta, double montoRetirado, double comisionAplicada)
            : numeroCuenta(numeroCuenta), montoRetirado(montoRetirado), comisionAplicada(comisionAplicada), revertida(false) {}

        friend class CuentaBancaria;

    public:
        string getNumeroCuenta() const { return numeroCuenta; }
        double getMontoRetirado() const { return montoRetirado; }
        double getComisionAplicada() const { return comisionAplicada; }
        double getImpactoTotal() const { return montoRetirado + comisionAplicada; }
        bool isRevertida() const { return revertida; }
    };

private:
    string numeroCuenta;
    string titular;
    mutable recursive_mutex mtx;

protected:
    double saldo;

    virtual ImpactoRetiro calcularImpactoRetiro(double monto) = 0;

public:
    CuentaBancaria(string numeroCuenta, string titular, double saldoInicial)
        : numeroCuenta(numeroCuenta), titular(titular), saldo(saldoInicial) {
        if (saldoInicial < 0) {
            throw invalid_argument("El saldo inicial no puede ser negativo.");
        }
    }

    virtual ~CuentaBancaria() = default;

    string getNumeroCuenta() const { return numeroCuenta; }
    string getTitular() const { return titular; }

    double getSaldo() const {
        lock_guard<recursive_mutex> lock(mtx);
        return saldo;
    }

    recursive_mutex& getLock() const { return mtx; }

    void depositar(double monto) {
        lock_guard<recursive_mutex> lock(mtx);
        if (monto <= 0) {
            throw invalid_argument("El monto a depositar debe ser mayor a cero.");
        }
        saldo += monto;
    }

    void revertir(TransaccionRetiro& tx) {
        lock_guard<recursive_mutex> lock(mtx);
        if (tx.getNumeroCuenta() != this->numeroCuenta) {
            throw invalid_argument("La transacción no pertenece a esta cuenta bancaria.");
        }
        if (tx.isRevertida()) {
            throw logic_error("La transacción ya fue revertida.");
        }
        saldo += tx.getImpactoTotal();
        tx.revertida = true;
    }

    TransaccionRetiro retirar(double monto, RegistroAuditoriaBancaria& auditoria) {
        if (monto <= 0) {
            throw invalid_argument("El monto a retirar debe ser mayor a cero.");
        }

        lock_guard<recursive_mutex> lock(mtx);
        ImpactoRetiro impacto = calcularImpactoRetiro(monto);

        ostringstream ss;
        ss << fixed << setprecision(2);
        ss << "RETIRO | Cuenta: " << numeroCuenta << " | Titular: " << titular 
           << " | Monto: $" << impacto.getMonto() 
           << " | Comisión: $" << impacto.getComision() 
           << " | Total: $" << impacto.getImpactoTotal();

        auditoria.registrar(ss.str());

        saldo -= impacto.getImpactoTotal();

        return TransaccionRetiro(numeroCuenta, impacto.getMonto(), impacto.getComision());
    }

    virtual void aplicarComisionMensual() = 0;
};

class CuentaAhorros : public CuentaBancaria {
private:
    double tasaInteres;

protected:
    ImpactoRetiro calcularImpactoRetiro(double monto) override {
        if (monto > saldo) {
            ostringstream ss;
            ss << fixed << setprecision(2);
            ss << "Saldo insuficiente en cuenta de ahorros. Saldo actual: $" << saldo;
            throw SaldoInsuficienteException(ss.str());
        }
        return ImpactoRetiro(monto, 0.0);
    }

public:
    CuentaAhorros(string numeroCuenta, string titular, double saldoInicial, double tasaInteres)
        : CuentaBancaria(numeroCuenta, titular, saldoInicial), tasaInteres(tasaInteres) {
        if (tasaInteres < 0) {
            throw invalid_argument("La tasa de interés no puede ser negativa.");
        }
    }

    void aplicarComisionMensual() override {
        lock_guard<recursive_mutex> lock(getLock());
        saldo += (saldo * tasaInteres);
    }
};

class CuentaCorriente : public CuentaBancaria {
private:
    double cupoSobregiro;
    double porcentajeComisionSobregiro;

protected:
    ImpactoRetiro calcularImpactoRetiro(double monto) override {
        double tramoSobregiro = 0.0;
        if (saldo > 0) {
            tramoSobregiro = max(0.0, monto - saldo);
        } else {
            tramoSobregiro = monto;
        }

        double comisionDinamica = tramoSobregiro * porcentajeComisionSobregiro;

        if ((monto + comisionDinamica) > (saldo + cupoSobregiro)) {
            ostringstream ss;
            ss << fixed << setprecision(2);
            ss << "La operación excede el límite de sobregiro. Máximo retirable estimado: $" 
               << (saldo + cupoSobregiro - comisionDinamica);
            throw SaldoInsuficienteException(ss.str());
        }

        return ImpactoRetiro(monto, comisionDinamica);
    }

public:
    CuentaCorriente(string numeroCuenta, string titular, double saldoInicial, double cupoSobregiro, double porcentajeComision)
        : CuentaBancaria(numeroCuenta, titular, saldoInicial), cupoSobregiro(cupoSobregiro), porcentajeComisionSobregiro(porcentajeComision) {
        if (cupoSobregiro < 0 || porcentajeComision < 0) {
            throw invalid_argument("El cupo y porcentaje deben ser mayores o iguales a cero.");
        }
    }

    void aplicarComisionMensual() override {
        lock_guard<recursive_mutex> lock(getLock());
        saldo -= 10.0;
    }
};

class ServicioTransferencia {
public:
    static void transferir(CuentaBancaria& origen, CuentaBancaria& destino, double monto, RegistroAuditoriaBancaria& auditoria) {
        if (origen.getNumeroCuenta() == destino.getNumeroCuenta()) {
            throw invalid_argument("La cuenta de origen y destino no pueden ser la misma.");
        }

        bool origenPrimero = origen.getNumeroCuenta() < destino.getNumeroCuenta();
        CuentaBancaria& primera = origenPrimero ? origen : destino;
        CuentaBancaria& segunda = origenPrimero ? destino : origen;

        unique_lock<recursive_mutex> lock1(primera.getLock(), defer_lock);
        unique_lock<recursive_mutex> lock2(segunda.getLock(), defer_lock);

        lock(lock1, lock2);

        auto tx = origen.retirar(monto, auditoria);
        destino.depositar(monto);

        ostringstream ss;
        ss << fixed << setprecision(2);
        ss << "TRANSFERENCIA EXITOSA | De: " << origen.getNumeroCuenta() 
           << " -> A: " << destino.getNumeroCuenta() 
           << " | Monto: $" << monto;

        auditoria.registrar(ss.str());
    }
};

int main() {
    try {
        CuentaAhorros cuentaAna("AH-101", "Ana Gómez", 500.0, 0.02);
        CuentaCorriente cuentaCarlos("CC-202", "Carlos Ruiz", 100.0, 200.0, 0.05);

        RegistroAuditoriaBancaria auditoria("auditoria.log");

        cout << "--- Retiro Individual con WAL ---" << endl;
        auto tx = cuentaAna.retirar(100.0, auditoria);
        cout << "Retiro completado. Saldo actual Ana: $" << fixed << setprecision(2) << cuentaAna.getSaldo() << endl;

        cout << "\n--- Transferencia Segura ---" << endl;
        ServicioTransferencia::transferir(cuentaAna, cuentaCarlos, 150.0, auditoria);
        cout << "Saldo Ana: $" << cuentaAna.getSaldo() << " | Saldo Carlos: $" << cuentaCarlos.getSaldo() << endl;

        cout << "\n--- Concurrencia Cruzada Multi-Hilo ---" << endl;

        thread t1([&]() {
            try {
                ServicioTransferencia::transferir(cuentaAna, cuentaCarlos, 50.0, auditoria);
            } catch (const exception& e) {
                cerr << "Error Hilo 1: " << e.what() << endl;
            }
        });

        thread t2([&]() {
            try {
                ServicioTransferencia::transferir(cuentaCarlos, cuentaAna, 30.0, auditoria);
            } catch (const exception& e) {
                cerr << "Error Hilo 2: " << e.what() << endl;
            }
        });

        t1.join();
        t2.join();

        cout << "\nSaldos Finales -> Ana: $" << cuentaAna.getSaldo() << " | Carlos: $" << cuentaCarlos.getSaldo() << endl;

    } catch (const exception& e) {
        cerr << "ERROR DE INFRAESTRUCTURA O DOMINIO: " << e.what() << endl;
    }

    return 0;
}