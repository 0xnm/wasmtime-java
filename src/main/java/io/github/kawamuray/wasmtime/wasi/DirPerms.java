package io.github.kawamuray.wasmtime.wasi;

import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public enum DirPerms {
    READ(0b1),
    MUTATE(0b10);

    @Getter
    private final int value;

    DirPerms(int value) {
        this.value = value;
    }
}
