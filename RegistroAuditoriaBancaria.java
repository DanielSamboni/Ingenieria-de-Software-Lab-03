import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class RegistroAuditoriaBancaria implements AutoCloseable {
    private final FileChannel channel;

    public RegistroAuditoriaBancaria(Path rutaArchivo) throws IOException {
        this.channel = FileChannel.open(
            rutaArchivo,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND
        );
        registrar("--- Inicio de Sesión de Auditoría ---");
    }

    public void registrar(String mensaje) {
        try {
            byte[] bytes = (mensaje + "\n").getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } catch (IOException e) {
            throw new AuditoriaException("CRÍTICO I/O: Fallo al persistir en la bitácora WAL.", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (channel != null && channel.isOpen()) {
            registrar("--- Cierre Seguro de Auditoría ---");
            channel.close();
        }
    }
}