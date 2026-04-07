package com.vivek.sqlstorm.config.loader;

import com.vivek.utils.parser.ConfigParserFactory;
import com.vivek.utils.parser.ConfigParserInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Loads config from the filesystem using the existing ConfigParserFactory / ResorceFinder
 * search-path logic (checks /etc/fkblitz/, ~/fkblitz/, ~/, classpath in order).
 *
 * Parser registration happens here so callers (DatabaseConnectionManager, etc.)
 * no longer need to do it themselves.
 */
public class FileConfigLoader<T> implements ConfigLoaderStrategy<T> {
    private static final Logger log = LoggerFactory.getLogger(FileConfigLoader.class);

    private final Class<T> configType;
    private final String fileNameWithoutExtension;

    public FileConfigLoader(Class<T> configType,
                            String fileNameWithoutExtension,
                            List<ConfigParserInterface<T>> parsers) {
        this.configType = configType;
        this.fileNameWithoutExtension = fileNameWithoutExtension;
        parsers.forEach(p -> ConfigParserFactory.registerParser(configType, p));
    }

    @Override
    public T load() throws ConfigLoadException {
        try {
            T result = ConfigParserFactory.getParser(configType).parse(fileNameWithoutExtension);
            if (result == null) {
                throw new ConfigLoadException("Parser returned null for config: " + fileNameWithoutExtension);
            }
            log.info("Loaded config '{}' from filesystem", fileNameWithoutExtension);
            return result;
        } catch (ConfigLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigLoadException("Failed to load config '" + fileNameWithoutExtension + "' from filesystem", e);
        }
    }
}
