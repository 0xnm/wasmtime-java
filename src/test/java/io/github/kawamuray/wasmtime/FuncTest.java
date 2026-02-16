package io.github.kawamuray.wasmtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.kawamuray.wasmtime.WasmFunctionError.I32ExitError;
import io.github.kawamuray.wasmtime.WasmFunctionError.TrapError;
import io.github.kawamuray.wasmtime.wasi.WasiCtx;
import io.github.kawamuray.wasmtime.wasi.WasiCtxBuilder;

import org.junit.Test;

import io.github.kawamuray.wasmtime.Val.Type;

public class FuncTest {
    private static final byte[] WAT_BYTES_ADD = ("(module"
            + "  (func (export \"add\") (param $p1 i32) (param $p2 i32) (result i32)"
            + "    local.get $p1"
            + "    local.get $p2"
            + "    i32.add)"
            + ')').getBytes();

    private static final byte[] WAT_BYTES_TRAMPOLINE = ("(module"
            + "  (func $callback (import \"\" \"callback\") (param i64 i64) (result i64))"
            + "  (func (export \"trampoline\") (param $p1 i64) (param $p2 i64) (result i64)"
            + "    local.get $p1"
            + "    local.get $p2"
            + "    call $callback)"
            + ')').getBytes();
    private static final byte[] WAT_BYTES_WASI_EXIT = ("(module"
            + "(func $__wasi_proc_exit (import \"wasi_snapshot_preview1\" \"proc_exit\") (param i32))"
            + "(memory (export \"memory\") 0)"
            + "(func (export \"_start\")"
            + "    i32.const 42"
            + "    call $__wasi_proc_exit)"
            + ")").getBytes();
    private static final byte[] WAT_BYTES_REENTRANT_CALLBACK = ("(module"
            + "  (func $callback (import \"\" \"callback\") (param i32) (result i32))"
            + "  (func (export \"inc\") (param i32) (result i32)"
            + "    local.get 0"
            + "    i32.const 1"
            + "    i32.add)"
            + "  (func (export \"apply\") (param i32) (result i32)"
            + "    local.get 0"
            + "    call $callback)"
            + ')').getBytes();

    @Test
    public void testCall() {
        try (Store<Void> store = Store.withoutData();
             Engine engine = store.engine();
             Module module = new Module(engine, WAT_BYTES_ADD);
             Instance instance = new Instance(store, module, Collections.emptyList())) {
            try (Func func = instance.getFunc(store, "add").get()) {
                Val[] results = func.call(store, Val.fromI32(1), Val.fromI32(2));
                assertEquals(1, results.length);
                assertEquals(Val.fromI32(3), results[0]);
            }
        }
    }

    @Test
    public void testTrampoline() {
        FuncType fnType = new FuncType(new Type[]{Type.I64, Type.I64}, new Type[]{Type.I64});
        AtomicInteger callerValue = new AtomicInteger();
        try (Store<AtomicInteger> store = new Store<>(new AtomicInteger(1234));
             Engine engine = store.engine();
             Module module = new Module(engine, WAT_BYTES_TRAMPOLINE);
             Func callback = new Func(store, fnType,
                     (caller, params, results) -> {
                         callerValue.set(caller.data().get());
                         results[0] = Val.fromI64(params[0].i64() + params[1].i64());
                     });
             Instance instance = new Instance(store, module, Arrays.asList(Extern.fromFunc(callback)))) {
            try (Func func = instance.getFunc(store, "trampoline").get()) {
                Val[] results = func.call(store, Val.fromI64(1), Val.fromI64(2));
                assertEquals(1, results.length);
                assertEquals(Val.fromI64(3), results[0]);

                assertEquals(1234, callerValue.get());
            }
        }
    }

    @Test(expected = RuntimeException.class)
    public void testTrampolineErrorJavaException() {
        FuncType fnType = new FuncType(new Type[]{Type.I64, Type.I64}, new Type[]{Type.I64});
        try (Store<Void> store = Store.withoutData();
             Engine engine = store.engine();
             Module module = new Module(engine, WAT_BYTES_TRAMPOLINE);
             Func callback = new Func(store, fnType,
                     (caller, params, results) -> { throw new RuntimeException("no hope..."); });
             Instance instance = new Instance(store, module, Arrays.asList(Extern.fromFunc(callback)))) {
            try (Func func = instance.getFunc(store, "trampoline").get()) {
                func.call(store, Val.fromI64(1), Val.fromI64(2));
            }
        }
    }

    @Test(expected = TrapError.class)
    public void testTrampolineErrorTrap() {
        FuncType fnType = new FuncType(new Type[]{Type.I64, Type.I64}, new Type[]{Type.I64});
        try (Store<Void> store = Store.withoutData();
             Engine engine = store.engine();
             Module module = new Module(engine, WAT_BYTES_TRAMPOLINE);
             Func callback = new Func(store, fnType,
                     (caller, params, results) -> {
                         throw new TrapError(Trap.INTERRUPT);
                     });
             Instance instance = new Instance(store, module, Arrays.asList(Extern.fromFunc(callback)))) {
            try (Func func = instance.getFunc(store, "trampoline").get()) {
                func.call(store, Val.fromI64(1), Val.fromI64(2));
            }
        }
    }

    @Test(expected = I32ExitError.class)
    public void testTrampolineErrorI32Exit() {
        FuncType fnType = new FuncType(new Type[]{Type.I64, Type.I64}, new Type[]{Type.I64});
        try (Store<Void> store = Store.withoutData();
             Engine engine = store.engine();
             Module module = new Module(engine, WAT_BYTES_TRAMPOLINE);
             Func callback = new Func(store, fnType,
                                      (caller, params, results) -> {
                                          throw new I32ExitError(-1);
                                      });
             Instance instance = new Instance(store, module, Arrays.asList(Extern.fromFunc(callback)))) {
            try (Func func = instance.getFunc(store, "trampoline").get()) {
                func.call(store, Val.fromI64(1), Val.fromI64(2));
            }
        }
    }

    @Test
    public void testTrampolineDrop() {
        FuncType fnType = new FuncType(new Type[]{Type.I64, Type.I64}, new Type[]{Type.I64});
        try (Store<Void> store = Store.withoutData()) {
            try (Func ignored = new Func(store, fnType, (caller, params, results) -> Optional.empty())) {
                assertEquals(1, Func.registry.map.size());
            }
        }
        assertEquals(0, Func.registry.map.size());
    }

    @Test
    public void testWasiExitTrap() {
        WasiCtx wasi = new WasiCtxBuilder().build();
        try (Store<Void> store = Store.withoutData(wasi);
             Linker linker = new Linker(store.engine());
             Engine engine = store.engine();
             Module module = new Module(engine, WAT_BYTES_WASI_EXIT)) {
            WasiCtx.addToLinker(linker);
            linker.module(store, "", module);
            try (Func func = linker.get(store, "", "_start").get().func()) {
                func.call(store);
                fail("exit normally");
            } catch (I32ExitError e) {
                assertEquals(42, e.exitCode());
            }
        }
    }

    @Test
    public void testNullFuncRefRoundtrip() {
        FuncType fnType = new FuncType(new Type[]{Type.FUNC_REF}, new Type[]{Type.FUNC_REF});
        try (Store<Void> store = Store.withoutData();
             Func func = new Func(store, fnType, (caller, params, results) -> results[0] = params[0])) {
            Val[] results = func.call(store, Val.nullFuncRef());
            assertEquals(1, results.length);
            assertEquals(Type.NULL_FUNC_REF, results[0].getType());
            assertEquals(null, results[0].funcRef());
        }
    }

    @Test
    public void testV128Roundtrip() {
        FuncType fnType = new FuncType(new Type[]{Type.V128}, new Type[]{Type.V128});
        byte[] payload = new byte[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
        };
        try (Store<Void> store = Store.withoutData();
             Func func = new Func(store, fnType, (caller, params, results) -> results[0] = params[0])) {
            Val[] results = func.call(store, Val.fromV128(payload));
            assertEquals(1, results.length);
            assertEquals(Type.V128, results[0].getType());
            assertTrue(Arrays.equals(payload, results[0].v128()));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNonNullFuncRefConstructionUnsupported() {
        try (Store<Void> store = Store.withoutData();
             Func func = new Func(store, new FuncType(new Type[]{}, new Type[]{}), (c, p, r) -> Optional.empty())) {
            Val.fromFuncRef(func);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNonNullExternRefConstructionUnsupported() {
        Val.fromExternRef(new Object());
    }

    @Test
    public void testStoreGcWithFuncRefNullRoundtrip() {
        FuncType fnType = new FuncType(new Type[]{Type.FUNC_REF}, new Type[]{Type.FUNC_REF});
        try (Store<Void> store = Store.withoutData();
             Func func = new Func(store, fnType, (caller, params, results) -> results[0] = params[0])) {
            for (int i = 0; i < 32; i++) {
                store.gc();
                Val[] results = func.call(store, Val.nullFuncRef());
                assertEquals(1, results.length);
                assertEquals(Type.NULL_FUNC_REF, results[0].getType());
                assertEquals(null, results[0].funcRef());
            }
        }
    }

    @Test
    public void testReentrantCallbackCallingBackIntoWasm() {
        FuncType fnType = new FuncType(new Type[]{Type.I32}, new Type[]{Type.I32});
        AtomicInteger callbackCalls = new AtomicInteger();
        try (Store<Void> store = Store.withoutData();
             Engine engine = store.engine();
             Module module = new Module(engine, WAT_BYTES_REENTRANT_CALLBACK);
             Func callback = new Func(store, fnType, (caller, params, results) -> {
                 callbackCalls.incrementAndGet();
                 try (Func inc = caller.getExport("inc")
                         .orElseThrow(() -> new RuntimeException("missing export: inc"))
                         .func()) {
                     Val[] vals = inc.call(store, params[0]);
                     results[0] = vals[0];
                 }
             });
             Instance instance = new Instance(store, module, Arrays.asList(Extern.fromFunc(callback)));
             Func apply = instance.getFunc(store, "apply").orElseThrow(() -> new RuntimeException("missing apply"))) {
            Val[] results = apply.call(store, Val.fromI32(41));
            assertEquals(1, results.length);
            assertEquals(Val.fromI32(42), results[0]);
            assertEquals(1, callbackCalls.get());
        }
    }
}
