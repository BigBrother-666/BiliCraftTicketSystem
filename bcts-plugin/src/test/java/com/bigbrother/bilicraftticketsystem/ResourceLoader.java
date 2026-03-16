package com.bigbrother.bilicraftticketsystem;

import java.io.InputStream;

public class ResourceLoader {
    public static InputStream load(String path) {

        InputStream stream = ResourceLoader.class
                .getClassLoader()
                .getResourceAsStream(path);

        if (stream == null) {
            throw new RuntimeException("Resource not found: " + path);
        }

        return stream;
    }
}
