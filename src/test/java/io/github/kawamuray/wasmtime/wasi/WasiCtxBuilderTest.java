package io.github.kawamuray.wasmtime.wasi;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import io.github.kawamuray.wasmtime.Store;
import io.github.kawamuray.wasmtime.Func;
import io.github.kawamuray.wasmtime.Linker;
import io.github.kawamuray.wasmtime.Module;
import io.github.kawamuray.wasmtime.WasmFunctions;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public class WasiCtxBuilderTest {
    private static final byte[] PROC_EXIT_WAT = ("(module"
            + "  (import \"wasi_snapshot_preview1\" \"proc_exit\" (func $proc_exit (param i32)))"
            + "  (func (export \"run\")"
            + "    i32.const 0"
            + "    call $proc_exit)"
            + ")").getBytes();
    private static final byte[] FD_WRITE_WAT = ("(module"
            + "  (import \"wasi_snapshot_preview1\" \"fd_write\""
            + "    (func $fd_write (param i32 i32 i32 i32) (result i32)))"
            + "  (memory (export \"memory\") 1)"
            + "  (data (i32.const 8) \"hello-out\\n\")"
            + "  (data (i32.const 32) \"hello-err\\n\")"
            + "  (func (export \"_start\")"
            + "    (i32.store (i32.const 0) (i32.const 8))"
            + "    (i32.store (i32.const 4) (i32.const 10))"
            + "    (drop (call $fd_write"
            + "      (i32.const 1)"
            + "      (i32.const 0)"
            + "      (i32.const 1)"
            + "      (i32.const 64)))"
            + "    (i32.store (i32.const 16) (i32.const 32))"
            + "    (i32.store (i32.const 20) (i32.const 10))"
            + "    (drop (call $fd_write"
            + "      (i32.const 2)"
            + "      (i32.const 16)"
            + "      (i32.const 1)"
            + "      (i32.const 68))))"
            + ")").getBytes();

    @Test
    public void testNewConfig() {
        WasiCtx ctx = new WasiCtxBuilder().build();
        Store<Void> store = Store.withoutData(ctx);
        store.close();
    }

    @Test
    public void testNewConfigWithArgs() {
        WasiCtx ctx = new WasiCtxBuilder().args(Arrays.asList("foo", "bar")).build();
        Store<Void> store = Store.withoutData(ctx);
        store.close();
    }

    @Test
    public void testNewConfigWithStdOutput() {
        WasiCtx ctx = new WasiCtxBuilder().stdout(System.out).stderr(System.err).build();
        Store<Void> store = Store.withoutData(ctx);
        store.close();
    }

    @Test
    public void testNewConfigWithInheritedArgs() {
        WasiCtx ctx = new WasiCtxBuilder().inheritArgs().build();
        Store<Void> store = Store.withoutData(ctx);
        store.close();
    }

    @Test
    public void testNewConfigWithBlockingAllowed() {
        WasiCtx ctx = new WasiCtxBuilder().allowBlockingCurrentThread(true).build();
        Store<Void> store = Store.withoutData(ctx);
        store.close();
    }

    @Test
    public void testNewConfigWithEnvsMap() {
        WasiCtx ctx = new WasiCtxBuilder().envs(Collections.singletonMap("FOO", "BAR")).build();
        Store<Void> store = Store.withoutData(ctx);
        store.close();
    }

    @Test
    public void testAddToLinkerWithoutExplicitWasiCtxDoesNotPanic() {
        try (Store<Void> store = Store.withoutData();
             Linker linker = new Linker(store.engine());
             Module module = new Module(store.engine(), PROC_EXIT_WAT)) {
            WasiCtx.addToLinker(linker);
            linker.module(store, "", module);
            try (Func run = linker.get(store, "", "run").get().func()) {
                WasmFunctions.Consumer0 fn = WasmFunctions.consumer(store, run);
                assertThrows(RuntimeException.class, fn::accept);
            }
        }
    }

    @Test
    public void testCustomStdoutAndStderrCaptureWasiOutput() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        WasiCtx wasi = new WasiCtxBuilder().stdout(stdout).stderr(stderr).build();
        try (Store<Void> store = Store.withoutData(wasi);
             Linker linker = new Linker(store.engine());
             Module module = new Module(store.engine(), FD_WRITE_WAT)) {
            WasiCtx.addToLinker(linker);
            linker.module(store, "", module);
            try (Func start = linker.get(store, "", "_start").get().func()) {
                WasmFunctions.Consumer0 fn = WasmFunctions.consumer(store, start);
                fn.accept();
            }
        }

        String out = stdout.toString(StandardCharsets.UTF_8);
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("hello-out"));
        assertTrue(err.contains("hello-err"));
    }
}
