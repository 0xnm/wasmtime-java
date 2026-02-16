package io.github.kawamuray.wasmtime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Tag implements Disposable {
    @Getter(AccessLevel.PACKAGE)
    private long innerPtr;

    @Override
    public native void dispose();
}
