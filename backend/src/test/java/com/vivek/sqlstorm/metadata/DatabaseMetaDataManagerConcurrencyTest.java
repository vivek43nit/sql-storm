package com.vivek.sqlstorm.metadata;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that concurrent readers and config reloaders do not cause
 * ConcurrentModificationException, NullPointerException, or other race conditions.
 */
class DatabaseMetaDataManagerConcurrencyTest {

    private DatabaseMetaDataManager manager;

    @BeforeEach
    void setUp() {
        ConnectionDTO h2 = new ConnectionDTO(
                "org.h2.Driver",
                "jdbc:h2:mem:conctest;DB_CLOSE_DELAY=-1",
                "sa", "", 1L, "grp", "db");
        h2.setMaxPoolSize(10);

        ConfigLoaderStrategy<ConnectionConfig> connLoader =
                () -> new ConnectionConfig(List.of(h2));

        CustomRelationConfig emptyConfig = new CustomRelationConfig(new HashMap<>());
        ConfigLoaderStrategy<CustomRelationConfig> mappingLoader = () -> emptyConfig;

        DatabaseConnectionManager connMgr = new DatabaseConnectionManager(connLoader, 5, null);
        manager = new DatabaseMetaDataManager(connMgr, mappingLoader);
    }

    @Test
    void concurrentReadersAndReloaders_neverThrow() throws InterruptedException {
        int readerCount = 20;
        int reloaderCount = 2;
        int totalThreads = readerCount + reloaderCount;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreads);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);

        // Reader threads: call getTables in a tight loop
        for (int i = 0; i < readerCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < 50; j++) {
                        try {
                            Collection<TableDTO> tables = manager.getTables("grp", "db");
                            // Iterate to expose any CME
                            for (TableDTO t : tables) {
                                t.getColumns().forEach(c -> {
                                    c.getReferTo().size();
                                    c.getReferencedBy().size();
                                });
                            }
                        } catch (ConnectionDetailNotFound | SQLException ignored) {
                            // H2 schema may not have tables — that's fine
                        }
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        // Reloader threads: trigger config reload repeatedly
        for (int i = 0; i < reloaderCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < 10; j++) {
                        CustomRelationConfig fresh = new CustomRelationConfig(new HashMap<>());
                        manager.reloadCustomRelationConfig(fresh);
                        Thread.sleep(5);
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(errors)
                .withFailMessage("Race condition errors: %s", errors)
                .isEmpty();
    }

    @Test
    void snapshotSwap_oldReferenceSafeToIterateAfterReload()
            throws ConnectionDetailNotFound, SQLException, InterruptedException {
        // Grab a reference to the current table collection
        Collection<TableDTO> oldSnapshot = manager.getTables("grp", "db");

        // Trigger a reload from another thread
        AtomicReference<Throwable> reloadError = new AtomicReference<>();
        Thread reloader = new Thread(() -> {
            try {
                manager.reloadCustomRelationConfig(new CustomRelationConfig(new HashMap<>()));
            } catch (Throwable t) {
                reloadError.set(t);
            }
        });
        reloader.start();
        reloader.join(5_000);

        // The old snapshot must still be safely iterable — no CME, no NPE
        List<String> names = new ArrayList<>();
        for (TableDTO t : oldSnapshot) {
            names.add(t.getTableName());
        }

        assertThat(reloadError.get()).isNull();
        // names may be empty (H2 might have no tables) — we only care no exception was thrown
    }
}
