package net.common;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class JsonField {

    private final Type type;

    protected JsonField(Type type) {
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }

    public abstract void write(JsonGenerator out) throws IOException;

    public abstract void writeAsField(String name, JsonGenerator out) throws IOException;

    public boolean getBooleanValue() {
        throw new UnsupportedOperationException("JsonField is not of type BOOLEAN");
    }

    public boolean isBoolean() {
        return this.type == Type.BOOLEAN;
    }

    public long getLongValue() {
        throw new UnsupportedOperationException("JsonField is not of type NUMBER_INT");
    }

    public boolean isInt() {
        return this.type == Type.NUMBER_INT;
    }

    public float getFloatValue() {
        throw new UnsupportedOperationException("JsonField is not of type NUMBER_FLOAT");
    }

    public boolean isFloat() { return this.type == Type.NUMBER_FLOAT; }

    public String getStringValue() {
        throw new UnsupportedOperationException("JsonField is not of type STRING");
    }

    public boolean isString() { return this.type == Type.STRING; }

    public boolean containsKey(String key) {
        throw new UnsupportedOperationException("JsonField is not of type OBJECT");
    }

    public JsonField getProperty(String key) {
        throw new UnsupportedOperationException("JsonField is not of type OBJECT");
    }

    public void setProperty(String key, JsonField value) {
        throw new UnsupportedOperationException("JsonField is not of type OBJECT");
    }

    public Map<String, JsonField> getProperties() {
        throw new UnsupportedOperationException("JsonField is not of type OBJECT");
    }

    public boolean isObject() { return this.type == Type.OBJECT; }

    public int getLength() {
        throw new UnsupportedOperationException("JsonField is not of type ARRAY");
    }

    public List<JsonField> getElements() {
        throw new UnsupportedOperationException("JsonField is not of type ARRAY");
    }

    public boolean isArray() { return this.type == Type.ARRAY; }

    public enum Type {
        BOOLEAN, NUMBER_INT, NUMBER_FLOAT, STRING, OBJECT, ARRAY
    }

    public static BooleanField fromBoolean(boolean value) { return new BooleanField(value); }

    public static class BooleanField extends JsonField {
        private boolean value;

        protected BooleanField(boolean value) {
            super(Type.BOOLEAN);
            this.value = value;
        }

        @Override
        public void write(JsonGenerator out) throws IOException {
            out.writeBoolean(this.value);
        }

        @Override
        public void writeAsField(String name, JsonGenerator out) throws IOException {
            out.writeBooleanField(name, this.value);
        }

        @Override
        public boolean getBooleanValue() {
            return this.value;
        }
    }

    public static IntegerField fromInt(long value) { return new IntegerField(value); }

    public static class IntegerField extends JsonField {
        private long value;

        protected IntegerField(long value) {
            super(Type.NUMBER_INT);
            this.value = value;
        }

        @Override
        public void write(JsonGenerator out) throws IOException {
            out.writeNumber(this.value);
        }

        @Override
        public void writeAsField(String name, JsonGenerator out) throws IOException {
            out.writeNumberField(name, this.value);
        }

        @Override
        public long getLongValue() {
            return this.value;
        }
    }

    public static FloatField fromFloat(float value) { return new FloatField(value); }

    public static class FloatField extends JsonField {
        private float value;

        public FloatField(float value) {
            super(Type.NUMBER_FLOAT);
            this.value = value;
        }

        @Override
        public void write(JsonGenerator out) throws IOException {
            out.writeNumber(this.value);
        }

        @Override
        public void writeAsField(String name, JsonGenerator out) throws IOException {
            out.writeNumberField(name, this.value);
        }

        @Override
        public float getFloatValue() {
            return this.value;
        }
    }

    public static StringField fromString(String value) { return new StringField(value); }

    public static class StringField extends JsonField {
        private String value;

        public StringField(String value) {
            super(Type.STRING);
            this.value = value;
        }

        @Override
        public void write(JsonGenerator out) throws IOException {
            out.writeString(this.value);
            System.out.println("[StringField][write] Write String \"" + this.value + "\"");
        }

        @Override
        public void writeAsField(String name, JsonGenerator out) throws IOException {
            out.writeStringField(name, this.value);
        }

        public String getStringValue() {
            return this.value;
        }
    }

    public static ObjectField fromObject(Map<String, JsonField> properties) { return new ObjectField(properties); }

    public static ObjectField emptyObject() { return new ObjectField(new HashMap<>()); }

    public static class ObjectField extends JsonField {
        private Map<String, JsonField> properties;

        public ObjectField(Map<String, JsonField> properties) {
            super(Type.OBJECT);
            this.properties = properties;
        }

        @Override
        public void write(JsonGenerator out) throws IOException {
            out.writeStartObject();
            for (Map.Entry<String, JsonField> entry : this.properties.entrySet()) {
                entry.getValue().writeAsField(entry.getKey(), out);
            }
            out.writeEndObject();
        }

        @Override
        public void writeAsField(String name, JsonGenerator out) throws IOException {
            out.writeObjectFieldStart(name);
            for (Map.Entry<String, JsonField> entry : this.properties.entrySet()) {
                entry.getValue().writeAsField(entry.getKey(), out);
            }
            out.writeEndObject();
        }

        @Override
        public boolean containsKey(String key) {
            return this.properties.containsKey(key);
        }

        @Override
        public JsonField getProperty(String key) {
            return this.properties.get(key);
        }

        @Override
        public void setProperty(String name, JsonField value) {
            this.properties.put(name, value);
        }

        @Override
        public Map<String, JsonField> getProperties() {
            return Collections.unmodifiableMap(this.properties);
        }

        public void setProperty(String name, boolean value) {
            this.properties.put(name, JsonField.fromBoolean(value));
        }

        public void setProperty(String name, long value) {
            this.properties.put(name, JsonField.fromInt(value));
        }

        public void setProperty(String name, float value) {
            this.properties.put(name, JsonField.fromFloat(value));
        }

        public void setProperty(String name, String value) {
            this.properties.put(name, JsonField.fromString(value));
        }

        public void setProperty(String name, Map<String, JsonField> value) {
            this.properties.put(name, JsonField.fromObject(value));
        }
    }

    public static ArrayField fromArray(List<JsonField> elements) { return new ArrayField(elements); }

    public static class ArrayField extends JsonField {
        private List<JsonField> elements;

        public ArrayField(List<JsonField> elements) {
            super(Type.ARRAY);
            this.elements = elements;
        }

        @Override
        public void write(JsonGenerator out) throws IOException {
            out.writeStartArray();
            for (JsonField e : this.elements) {
                e.write(out);
            }
            out.writeEndArray();
        }

        @Override
        public void writeAsField(String name, JsonGenerator out) throws IOException {
            out.writeArrayFieldStart(name);
            for (JsonField e : this.elements) {
                e.write(out);
            }
            out.writeEndArray();
        }

        @Override
        public int getLength() {
            return this.elements.size();
        }

        @Override
        public List<JsonField> getElements() {
            return Collections.unmodifiableList(this.elements);
        }
    }
}
