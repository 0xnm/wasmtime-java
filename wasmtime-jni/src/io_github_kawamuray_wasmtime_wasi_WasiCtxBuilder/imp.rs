use super::JniWasiCtxBuilder;
use crate::errors;
use crate::errors::Result;
use crate::interop;
use crate::java_streams::{JavaInputStream, JavaOutputStream};
use crate::utils;
use jni::objects::*;
use jni::sys::*;
use jni::JNIEnv;
use wasmtime_wasi::p1::WasiP1Ctx;
use wasmtime_wasi::DirPerms;
use wasmtime_wasi::FilePerms;
use wasmtime_wasi::WasiCtxBuilder;

pub(super) struct JniWasiCtxBuilderImpl;

impl<'a> JniWasiCtxBuilder<'a> for JniWasiCtxBuilderImpl {
    type Error = errors::Error;

    fn native_build(
        env: &mut JNIEnv<'a>,
        _class: JClass,
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
    ) -> Result<jlong, Self::Error> {
        let mut builder = WasiCtxBuilder::new();

        let mut iter = utils::JavaArrayIter::new(env, envs)?;
        while let Some(pair) = iter.next(env) {
            let pair = pair?;
            let var = env.get_object_array_element(<&JObjectArray>::from(&pair), 0)?;
            let value = env.get_object_array_element(<&JObjectArray>::from(&pair), 1)?;

            builder.env(
                &utils::get_string(env, &var)?,
                &utils::get_string(env, &value)?,
            );
        }
        let mut iter = utils::JavaArrayIter::new(env, args)?;
        while let Some(arg) = iter.next(env) {
            builder.arg(&utils::get_string(env, &arg?)?);
        }
        if inherit_args != 0 {
            builder.inherit_args();
        }

        if inherit_stdin != 0 {
            builder.stdin(wasmtime_wasi::cli::stdin());
        } else if !stdin_stream.is_null() {
            let jvm = env.get_java_vm()?;
            let global_ref = env.new_global_ref(stdin_stream)?;
            let java_stdin = JavaInputStream::new(jvm, global_ref);
            builder.stdin(java_stdin);
        }

        if inherit_stdout != 0 {
            builder.stdout(wasmtime_wasi::cli::stdout());
        } else if !stdout_stream.is_null() {
            let jvm = env.get_java_vm()?;
            let global_ref = env.new_global_ref(stdout_stream)?;
            let java_stdout = JavaOutputStream::new(jvm, global_ref);
            builder.stdout(java_stdout);
        }

        if inherit_stderr != 0 {
            builder.stderr(wasmtime_wasi::cli::stderr());
        } else if !stderr_stream.is_null() {
            let jvm = env.get_java_vm()?;
            let global_ref = env.new_global_ref(stderr_stream)?;
            let java_stderr = JavaOutputStream::new(jvm, global_ref);
            builder.stderr(java_stderr);
        }

        builder.allow_blocking_current_thread(allow_blocking_current_thread != 0);

        let mut iter = utils::JavaArrayIter::new(env, preopen_dirs)?;
        while let Some(obj) = iter.next(env) {
            let obj = obj?;
            let host_path = utils::get_string_field(env, &obj, "hostPath")?;
            let guest_path = utils::get_string_field(env, &obj, "guestPath")?;
            let dir_perms = utils::get_u32_field(env, &obj, "dirPerms")?;
            let file_perms = utils::get_u32_field(env, &obj, "filePerms")?;
            builder.preopened_dir(
                host_path,
                guest_path,
                DirPerms::from_bits_truncate(dir_perms as usize),
                FilePerms::from_bits_truncate(file_perms as usize),
            )?;
        }

        let ctx = builder.build_p1();
        let ptr = interop::into_raw::<WasiP1Ctx>(ctx);
        Ok(ptr)
    }
}
