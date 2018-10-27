package net.common;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * A simple FunctionalInterface for a function that takes a JsonGenerator and uses it to generate
 * some JSON.
 */
@FunctionalInterface
public interface DeferredJsonGenerator {
    void generate(JsonGenerator gen) throws IOException;
}
