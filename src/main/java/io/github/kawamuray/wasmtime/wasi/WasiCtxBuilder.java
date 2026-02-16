package io.github.kawamuray.wasmtime.wasi;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.github.kawamuray.wasmtime.NativeLibraryLoader;
import lombok.Value;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public class WasiCtxBuilder {
    static {
        NativeLibraryLoader.init();
    }

    private final List<String[]> envs;
    private final List<String> args;
    private InputStream stdinStream;
    private boolean inheritStdin;
    private OutputStream stdoutStream;
    private boolean inheritStdout;
    private OutputStream stderrStream;
    private boolean inheritStderr;
    private boolean inheritArgs;
    private boolean allowBlockingCurrentThread;
    private final List<PreopenDir> preopenDirs;

    public WasiCtxBuilder() {
        envs = new ArrayList<>();
        args = new ArrayList<>();
        preopenDirs = new ArrayList<>();
    }

    @Value
    public static class PreopenDir {
        String hostPath;
        String guestPath;
        int dirPerms;
        int filePerms;
    }

    public WasiCtxBuilder env(String var, String value) {
        envs.add(new String[]{var, value});
        return this;
    }

    public WasiCtxBuilder envs(Map<String, String> envs) {
        for (Entry<String, String> entry : envs.entrySet()) {
            env(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public WasiCtxBuilder inheritEnv() {
        for (Entry<String, String> entry : System.getenv().entrySet()) {
            env(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public WasiCtxBuilder arg(String arg) {
        args.add(arg);
        inheritArgs = false;
        return this;
    }

    public WasiCtxBuilder args(Collection<String> args) {
        this.args.addAll(args);
        inheritArgs = false;
        return this;
    }

    public WasiCtxBuilder inheritArgs() {
        inheritArgs = true;
        args.clear();
        return this;
    }

    public WasiCtxBuilder allowBlockingCurrentThread(boolean enable) {
        allowBlockingCurrentThread = enable;
        return this;
    }

    public WasiCtxBuilder stdin(InputStream stream) {
        stdinStream = stream;
        inheritStdin = false;
        return this;
    }

    public WasiCtxBuilder stdout(OutputStream stream) {
        stdoutStream = stream;
        inheritStdout = false;
        return this;
    }

    public WasiCtxBuilder stderr(OutputStream stream) {
        stderrStream = stream;
        inheritStderr = false;
        return this;
    }

    public WasiCtxBuilder inheritStdin() {
        stdinStream = null;
        inheritStdin = true;
        return this;
    }

    public WasiCtxBuilder inheritStdout() {
        stdoutStream = null;
        inheritStdout = true;
        return this;
    }

    public WasiCtxBuilder inheritStderr() {
        stderrStream = null;
        inheritStderr = true;
        return this;
    }

    public WasiCtxBuilder inheritStdio() {
        return inheritStdin().inheritStdout().inheritStderr();
    }

    public WasiCtxBuilder preopenedDir(Path dir, Set<DirPerms> dirPerms, Set<FilePerms> filePerms, String guestPath) {
        preopenDirs.add(new PreopenDir(
                dir.toString(),
                guestPath,
                dirPerms.stream().mapToInt(DirPerms::value).reduce(0, (left, right) -> left | right),
                filePerms.stream().mapToInt(FilePerms::value).reduce(0, (left, right) -> left | right)
        ));
        return this;
    }

    public WasiCtx build() {
        long wasiCtxPtr = nativeBuild(
                envs.toArray(),
                args.toArray(),
                inheritArgs,
                inheritStdin,
                stdinStream,
                inheritStdout,
                stdoutStream,
                inheritStderr,
                stderrStream,
                allowBlockingCurrentThread,
                preopenDirs.toArray());
        return new WasiCtx(wasiCtxPtr);
    }

    private static native long nativeBuild(
            Object[] envs,
            Object[] args,
            boolean inheritArgs,
            boolean inheritStdin,
            InputStream stdinStream,
            boolean inheritStdout,
            OutputStream stdoutStream,
            boolean inheritStderr,
            OutputStream stderrStream,
            boolean allowBlockingCurrentThread,
            Object[] preopenDirs);
}
