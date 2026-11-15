package com.norday;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Para los tests de concurrencia que fuerzan una intercalación: un hilo se
 * detiene a mitad de su transacción hasta que el otro haya terminado o se
 * haya quedado esperando un bloqueo que tiene esa transacción. Lo segundo se
 * ve en Postgres: algún backend tiene el pid de la transacción en pausa entre
 * sus {@code pg_blocking_pids}. Así no se duerme unos segundos suponiendo que
 * para entonces ya espera, ni vale cualquier otra espera ajena al test.
 *
 * Se llama desde el hilo en pausa, dentro de su transacción: el pid se lee
 * con la conexión de esa transacción. La espera se consulta con una conexión
 * propia del pool en autocommit: dentro de una transacción, Postgres da
 * siempre la misma foto de pg_stat_activity.
 */
final class EsperaDeBloqueo {

    private static final long LIMITE_SEGUNDOS = 30;

    private EsperaDeBloqueo() {}

    /** El pid del backend de la transacción en curso de este hilo. */
    static int pidDeEstaTransaccion(DataSource dataSource) {
        Integer pid = new JdbcTemplate(dataSource).queryForObject("SELECT pg_backend_pid()", Integer.class);
        if (pid == null) throw new IllegalStateException("sin pid");
        return pid;
    }

    /** ¿Hay algún backend esperando un bloqueo que tiene el backend {@code pid}? */
    static boolean alguienEsperaA(int pid, DataSource dataSource) {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement consulta = conexion.prepareStatement(
                     "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))")) {
            consulta.setInt(1, pid);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Vuelve cuando {@code terminado} llega a cero o cuando algún backend se
     * queda esperando un bloqueo de la transacción de este hilo, lo que pase
     * antes. Si en {@value #LIMITE_SEGUNDOS} s no pasa ninguna de las dos,
     * falla.
     */
    static void hastaQueTermineOEspere(CountDownLatch terminado, DataSource dataSource)
            throws InterruptedException {
        int pid = pidDeEstaTransaccion(dataSource);
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMITE_SEGUNDOS);
        while (System.nanoTime() < fin) {
            if (terminado.await(20, TimeUnit.MILLISECONDS)) return;
            if (alguienEsperaA(pid, dataSource)) return;
        }
        throw new AssertionError("en " + LIMITE_SEGUNDOS + " s, ni terminó ni se quedó esperando a la transacción "
                + pid);
    }
}
