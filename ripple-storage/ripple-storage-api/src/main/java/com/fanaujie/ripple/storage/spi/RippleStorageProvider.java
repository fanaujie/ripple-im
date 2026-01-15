package com.fanaujie.ripple.storage.spi;

import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import java.util.function.Function;

public interface RippleStorageProvider {
    /**
     * Creates and initializes the storage facade.
     *
     * @param propertyLoader A function that accepts a configuration key and returns its value.
     * @return An initialized RippleStorageFacade.
     */
    RippleStorageFacade create(Function<String, String> propertyLoader);
}
