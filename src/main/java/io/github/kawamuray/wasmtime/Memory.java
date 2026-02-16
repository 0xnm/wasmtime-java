package io.github.kawamuray.wasmtime;

import java.nio.ByteBuffer;

import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@AllArgsConstructor
public class Memory implements Disposable {
    private long innerPtr;

    public <T> Memory(Store<T> store, MemoryType type) {
        this(newMemory(store.innerPtr(), type.minimum(), type.maximum(), type.is64()));
    }

    public <T> ByteBuffer buffer(Store<T> store) {
        return nativeBuffer(store.innerPtr());
    }

    public <T> long dataSize(Store<T> store) {
        return nativeDataSize(store.innerPtr());
    }

    public <T> long size(Store<T> store) {
        return nativeSize(store.innerPtr());
    }

    public <T> long grow(Store<T> store, long deltaPages) {
        return nativeGrow(store.innerPtr(), deltaPages);
    }

    @Override
    public native void dispose();

    private static native long newMemory(long innerPtr, long minimum, long maximum, boolean is64);

    private native ByteBuffer nativeBuffer(long storePtr);

    private native long nativeDataSize(long storePtr);

    private native long nativeSize(long storePtr);

    private native long nativeGrow(long storePtr, long deltaPages);
}
