package io.github.kawamuray.wasmtime.wasi;

import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public enum FilePerms {
    READ(0b1),
    WRITE(0b10);

    @Getter
    private final int value;

    FilePerms(int value) {
        this.value = value;
    }
}
