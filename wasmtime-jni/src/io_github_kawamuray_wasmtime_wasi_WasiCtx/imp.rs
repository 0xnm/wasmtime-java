use super::JniWasiCtx;
use crate::errors;
use crate::interop;
use crate::store::StoreData;
use jni::objects::*;
use jni::sys::*;
use jni::{self, JNIEnv};
use wasmtime::Linker;
use wasmtime_wasi::p1::WasiP1Ctx;

pub(super) struct JniWasiCtxImpl;

impl<'a> JniWasiCtx<'a> for JniWasiCtxImpl {
    type Error = errors::Error;

    fn native_add_to_linker(
        _env: &mut JNIEnv<'a>,
        _clazz: JClass<'a>,
        linker_ptr: jlong,
    ) -> Result<(), Self::Error> {
        let mut linker = interop::ref_from_raw::<Linker<StoreData>>(linker_ptr)?;
        wasmtime_wasi::p1::add_to_linker_sync(&mut linker, |s| {
            &mut *s.wasi.as_mut().expect("WasiCtx in store must not empty")
        })?;
        Ok(())
    }

    fn dispose(env: &mut JNIEnv<'a>, this: JObject<'a>) -> Result<(), Self::Error> {
        interop::dispose_inner::<WasiP1Ctx>(env, &this)?;
        Ok(())
    }
}
