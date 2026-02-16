package io.github.kawamuray.wasmtime;

public class MemoryAccessException extends WasmtimeException {
    public MemoryAccessException(String message) {
        super(message);
    }
}
