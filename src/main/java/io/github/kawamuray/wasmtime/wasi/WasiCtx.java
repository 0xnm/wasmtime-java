package io.github.kawamuray.wasmtime.wasi;

import io.github.kawamuray.wasmtime.Disposable;
import io.github.kawamuray.wasmtime.Linker;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@AllArgsConstructor
public class WasiCtx implements Disposable {
    @Getter
    private long innerPtr;

    public static void addToLinker(Linker linker) {
        nativeAddToLinker(linker.innerPtr());
    }

    @Override
    public native void dispose();

    private static native void nativeAddToLinker(long linkerPtr);

    public long takeInnerPtr() {
        long ptr = innerPtr;
        innerPtr = 0;
        return ptr;
    }
}
