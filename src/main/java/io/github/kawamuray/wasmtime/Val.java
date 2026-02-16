package io.github.kawamuray.wasmtime;

import lombok.Value;

@Value
public class Val {
    public enum Type {
        I32,
        I64,
        F32,
        F64,
        EXTERN_REF,
        FUNC_REF,
        NULL_FUNC_REF,
        V128,
    }

    // For `Val` class we decided to not to map it to inner type directly.
    // Compared to other classes Val is often constructed and disposed as a temporary
    // values and hence it will be just too bothering to be required disposing it every time.
    // This value then passed to those methods directly as an object and then converted into/from
    // Rust Val each time so that we can let its memory management to Rust/JVM GC totally, respectively.
    Type type;
    Object value;

    public static Val fromI32(int val) {
        return new Val(Type.I32, val);
    }

    public static Val fromI64(long val) {
        return new Val(Type.I64, val);
    }

    public static Val fromF32(float val) {
        return new Val(Type.F32, val);
    }

    public static Val fromF64(double val) {
        return new Val(Type.F64, val);
    }

    /** Creates a {@code v128} value from exactly 16 bytes (little-endian). */
    public static Val fromV128(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("v128 must be exactly 16 bytes");
        }
        return new Val(Type.V128, bytes.clone());
    }

    /** Creates a null funcref value. */
    public static Val nullFuncRef() {
        return new Val(Type.NULL_FUNC_REF, null);
    }

    /** Creates a funcref value, currently supporting only {@code null}. */
    public static Val fromFuncRef(Func func) {
        if (func == null) {
            return nullFuncRef();
        }
        throw new UnsupportedOperationException("Non-null FUNC_REF values are not supported yet");
    }

    /** Creates an externref value, currently supporting only {@code null}. */
    public static Val fromExternRef(Object ref) {
        if (ref != null) {
            throw new UnsupportedOperationException("Non-null EXTERN_REF values are not supported yet");
        }
        return new Val(Type.EXTERN_REF, ref);
    }

    private void ensureType(Type expected) {
        if (type != expected) {
            throw new RuntimeException(
                    String.format("value expected to have type %s but is actually %s", expected, type));
        }
    }

    public int i32() {
        ensureType(Type.I32);
        return (int) value;
    }

    public long i64() {
        ensureType(Type.I64);
        return (long) value;
    }

    public float f32() {
        ensureType(Type.F32);
        return (float) value;
    }

    public double f64() {
        ensureType(Type.F64);
        return (double) value;
    }

    /** Returns this value as a defensive copy of 16-byte {@code v128}. */
    public byte[] v128() {
        ensureType(Type.V128);
        return ((byte[]) value).clone();
    }

    /** Returns this value as funcref ({@code null} for {@code NULL_FUNC_REF}). */
    public Func funcRef() {
        if (type == Type.NULL_FUNC_REF) {
            return null;
        }
        ensureType(Type.FUNC_REF);
        return (Func) value;
    }

    /** Returns this value as externref. */
    public Object externRef() {
        ensureType(Type.EXTERN_REF);
        return value;
    }
}
