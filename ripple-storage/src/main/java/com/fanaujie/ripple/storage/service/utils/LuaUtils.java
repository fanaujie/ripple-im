package com.fanaujie.ripple.storage.service.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class LuaUtils {
    public static String loadScript(String script) {
        try (InputStream is = LuaUtils.class.getClassLoader().getResourceAsStream(script)) {
            if (is == null) {
                throw new RuntimeException("Failed to load Lua script: " + script);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }
    }
}
