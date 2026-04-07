package com.vivek.sqlstorm.config.loader;

/**
 * Strategy interface for loading a typed configuration object.
 * Implementations provide file-based, API-based, and DB-based loading.
 */
public interface ConfigLoaderStrategy<T> {
    T load() throws ConfigLoadException;
}
