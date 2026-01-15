package com.fanaujie.ripple.storage.spi;

import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;
import java.util.function.Function;

public class RippleStorageLoader {
    private static final Logger logger = LoggerFactory.getLogger(RippleStorageLoader.class);

    public static RippleStorageFacade load(Function<String, String> propertyLoader) {
        ServiceLoader<RippleStorageProvider> loader = ServiceLoader.load(RippleStorageProvider.class);
        
        for (RippleStorageProvider provider : loader) {
            logger.info("Found RippleStorageProvider: {}", provider.getClass().getName());
            return provider.create(propertyLoader);
        }

        throw new IllegalStateException("No implementation of RippleStorageProvider found on classpath. " +
                "Please ensure ripple-storage-cassandra or ripple-storage-mongodb is included in dependencies.");
    }
}
