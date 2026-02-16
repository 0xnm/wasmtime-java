use super::JniMemory;
use crate::errors;
use crate::errors::Result;
use crate::interop;
use crate::store::StoreData;
use anyhow::anyhow;
use jni::objects::{JClass, JObject};
use jni::sys::{jboolean, jlong, jobject, JNI_TRUE};
use jni::JNIEnv;
use wasmtime::{Memory, MemoryType, Store};

pub(super) struct JniMemoryImpl;

fn u64_to_jlong_checked(value: u64, what: &str) -> Result<jlong, errors::Error> {
    if value > jlong::MAX as u64 {
        return Err(errors::Error::Wasmtime(anyhow!(
            "{} exceeds Java long range: {}",
            what,
            value
        )));
    }
    Ok(value as jlong)
}

impl<'a> JniMemory<'a> for JniMemoryImpl {
    type Error = errors::Error;

    fn dispose(env: &mut JNIEnv<'a>, this: JObject<'a>) -> Result<(), Self::Error> {
        interop::dispose_inner::<Memory>(env, &this)?;
        Ok(())
    }

    fn native_buffer(
        env: &mut JNIEnv<'a>,
        this: JObject<'a>,
        store_ptr: jlong,
    ) -> Result<jobject, Self::Error> {
        let mut store = interop::ref_from_raw::<Store<StoreData>>(store_ptr)?;
        let mem = interop::get_inner::<Memory>(env, &this)?;
        let ptr = mem.data_mut(&mut *store);
        Ok(unsafe { env.new_direct_byte_buffer(ptr.as_mut_ptr(), ptr.len()) }?.into_raw())
    }

    fn native_data_size(
        env: &mut JNIEnv<'a>,
        this: JObject<'a>,
        store_ptr: jlong,
    ) -> Result<jlong, Self::Error> {
        let mut store = interop::ref_from_raw::<Store<StoreData>>(store_ptr)?;
        let mem = interop::get_inner::<Memory>(env, &this)?;
        u64_to_jlong_checked(mem.data_size(&mut *store) as u64, "memory data size")
    }

    fn new_memory(
        _env: &mut JNIEnv<'a>,
        _clazz: JClass,
        store_ptr: jlong,
        min: jlong,
        max: jlong,
        is_64: jboolean,
    ) -> Result<jlong, Self::Error> {
        if min < 0 {
            return Err(errors::Error::Wasmtime(anyhow!(
                "memory minimum must be non-negative"
            )));
        }
        if max < -1 {
            return Err(errors::Error::Wasmtime(anyhow!(
                "memory maximum must be -1 or non-negative"
            )));
        }
        let mut store = interop::ref_from_raw::<Store<StoreData>>(store_ptr)?;
        let max = if max < 0 { None } else { Some(max as u64) };
        let ty = if is_64 == JNI_TRUE {
            MemoryType::new64(min as u64, max)
        } else {
            if min > u32::MAX as jlong {
                return Err(errors::Error::Wasmtime(anyhow!(
                    "32-bit memory minimum is too large"
                )));
            }
            if let Some(max) = max {
                if max > u32::MAX as u64 {
                    return Err(errors::Error::Wasmtime(anyhow!(
                        "32-bit memory maximum is too large"
                    )));
                }
            }
            MemoryType::new(min as u32, max.map(|v| v as u32))
        };
        let mem = Memory::new(&mut *store, ty)?;
        Ok(interop::into_raw::<Memory>(mem))
    }

    fn native_size(
        env: &mut JNIEnv<'a>,
        this: JObject<'a>,
        store_ptr: jlong,
    ) -> Result<jlong, Self::Error> {
        let mut store = interop::ref_from_raw::<Store<StoreData>>(store_ptr)?;
        let mem = interop::get_inner::<Memory>(env, &this)?;
        Ok(mem.size(&mut *store) as jlong)
    }

    fn native_grow(
        env: &mut JNIEnv<'a>,
        this: JObject<'a>,
        store_ptr: jlong,
        delta_pages: jlong,
    ) -> Result<jlong, Self::Error> {
        if delta_pages < 0 {
            return Err(errors::Error::Wasmtime(anyhow!(
                "memory grow delta must be non-negative"
            )));
        }
        let mut store = interop::ref_from_raw::<Store<StoreData>>(store_ptr)?;
        let mem = interop::get_inner::<Memory>(env, &this)?;
        u64_to_jlong_checked(
            mem.grow(&mut *store, delta_pages as u64)?,
            "previous memory size",
        )
    }
}
