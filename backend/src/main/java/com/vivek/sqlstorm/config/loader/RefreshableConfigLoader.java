package com.vivek.sqlstorm.config.loader;

import java.util.function.Consumer;

/**
 * Extension of ConfigLoaderStrategy for loaders that support periodic polling and
 * hot-reload via a registered change listener.
 *
 * Implementations: DbConfigLoader (blob-based), RelationRowDbLoader (row-per-relation).
 */
public interface RefreshableConfigLoader<T> extends ConfigLoaderStrategy<T> {

    /**
     * Register a listener invoked when a config change is detected during {@link #refresh()}.
     */
    void setChangeListener(Consumer<T> listener);

    /**
     * Poll the underlying source. If the content has changed since the last load/refresh,
     * parse the new config, update the cache, and invoke the change listener.
     * Failures must be logged as WARN and must NOT propagate — the previous config is retained.
     */
    void refresh();
}
