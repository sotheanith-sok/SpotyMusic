package net.common;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

@FunctionalInterface
public interface JsonSerializer<T> {
    void serialize(T t, JsonGenerator gen) throws IOException;
}
