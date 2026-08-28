public class AuditoriaException extends RuntimeException {
    public AuditoriaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}