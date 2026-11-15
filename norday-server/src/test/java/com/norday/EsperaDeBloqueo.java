package com.norday;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Para los tests de concurrencia que fuerzan una intercalación: un hilo se
 * detiene a mitad de su transacción hasta que el otro haya terminado o se
 * haya quedado esperando un bloqueo de Postgres. Lo segundo se ve en
 * {@code pg_stat_activity} (wait_event_type = 'Lock'), en vez de dormir unos
 * segundos y suponer que para entonces ya espera.
 *
 * Consulta con una conexión propia del pool y en autocommit: dentro de una
 * transacción, Postgres da siempre la misma foto de pg_stat_activity.
 */
final class EsperaDeBloqueo {

    private static final long LIMITE_SEGUNDOS = 30;

    private EsperaDeBloqueo() {}

    /** ¿Hay ahora otra conexión a esta base esperando un bloqueo? */
    static boolean alguienEspera(DataSource dataSource) {
        try (Connection conexion = dataSource.getConnection();
             Statement consulta = conexion.createStatement();
             ResultSet fila = consulta.executeQuery(
                     "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                             + "AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'")) {
            fila.next();
            return fila.getInt(1) > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Vuelve cuando {@code terminado} llega a cero o cuando alguna conexión
     * se queda esperando un bloqueo, lo que pase antes. Si en
     * {@value #LIMITE_SEGUNDOS} s no pasa ninguna de las dos, falla.
     */
    static void hastaQueTermineOEspere(CountDownLatch terminado, DataSource dataSource)
            throws InterruptedException {
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMITE_SEGUNDOS);
        while (System.nanoTime() < fin) {
            if (terminado.await(20, TimeUnit.MILLISECONDS)) return;
            if (alguienEspera(dataSource)) return;
        }
        throw new AssertionError("en " + LIMITE_SEGUNDOS + " s, ni terminó ni se quedó esperando un bloqueo");
    }
}
