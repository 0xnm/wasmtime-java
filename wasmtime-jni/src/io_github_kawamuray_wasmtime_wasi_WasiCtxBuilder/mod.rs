// THIS FILE IS GENERATED AUTOMATICALLY. DO NOT EDIT!
mod imp;

use self::imp::JniWasiCtxBuilderImpl;
use jni::descriptors::Desc;
use jni::objects::*;
use jni::sys::*;
use jni::JNIEnv;

macro_rules! wrap_error {
    ($env:expr, $body:expr, $default:expr) => {
        match $body {
            Ok(v) => v,
            Err(e) => {
                if let Err(err) = $env.throw(e) {
                    $env.exception_describe().ok();
                    panic!("error in throwing exception: {}", err);
                }
                $default
            }
        }
    };
}

trait JniWasiCtxBuilder<'a> {
    type Error: Desc<'a, JThrowable<'a>>;
    fn native_build(
        env: &mut JNIEnv<'a>,
        clazz: JClass<'a>,
        envs: jobjectArray,
        args: jobjectArray,
        inherit_args: jboolean,
        inherit_stdin: jboolean,
        stdin_stream: JObject<'a>,
        inherit_stdout: jboolean,
        stdout_stream: JObject<'a>,
        inherit_stderr: jboolean,
        stderr_stream: JObject<'a>,
        allow_blocking_current_thread: jboolean,
        preopen_dirs: jobjectArray,
    ) -> Result<jlong, Self::Error>;
}

#[unsafe(no_mangle)]
extern "system" fn Java_io_github_kawamuray_wasmtime_wasi_WasiCtxBuilder_nativeBuild___3Ljava_lang_Object_2_3Ljava_lang_Object_2ZZLjava_io_InputStream_2ZLjava_io_OutputStream_2ZLjava_io_OutputStream_2Z_3Ljava_lang_Object_2<
    'a,
>(
    mut env: JNIEnv<'a>,
    clazz: JClass<'a>,
    envs: jobjectArray,
    args: jobjectArray,
    inherit_args: jboolean,
    inherit_stdin: jboolean,
    stdin_stream: JObject<'a>,
    inherit_stdout: jboolean,
    stdout_stream: JObject<'a>,
    inherit_stderr: jboolean,
    stderr_stream: JObject<'a>,
    allow_blocking_current_thread: jboolean,
    preopen_dirs: jobjectArray,
) -> jlong {
    wrap_error!(
        env,
        JniWasiCtxBuilderImpl::native_build(
            &mut env,
            clazz,
            envs,
            args,
            inherit_args,
            inherit_stdin,
            stdin_stream,
            inherit_stdout,
            stdout_stream,
            inherit_stderr,
            stderr_stream,
            allow_blocking_current_thread,
            preopen_dirs
        ),
        Default::default()
    )
}
