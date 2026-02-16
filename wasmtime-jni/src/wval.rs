use crate::errors::{Error, Result};
use crate::utils;
use crate::utils::into_java_array;
use anyhow::anyhow;
use jni::objects::{JByteArray, JObject};
use jni::sys::jobjectArray;
use jni::JNIEnv;
use wasmtime::{RefType, Val, ValType};

pub const VAL_TYPE: &str = "io/github/kawamuray/wasmtime/Val$Type";

pub fn from_java<'a>(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Val> {
    let ty = env
        .get_field(&obj, "type", "Lio/github/kawamuray/wasmtime/Val$Type;")?
        .l()?;
    let name = utils::enum_name(env, ty)?;
    Ok(match name.as_str() {
        "I32" => {
            let val = env.call_method(obj, "i32", "()I", &[])?.i()?;
            Val::from(val)
        }
        "I64" => {
            let val = env.call_method(obj, "i64", "()J", &[])?.j()?;
            Val::from(val)
        }
        "F32" => {
            let val = env.call_method(obj, "f32", "()F", &[])?.f()?;
            Val::from(val)
        }
        "F64" => {
            let val = env.call_method(obj, "f64", "()D", &[])?.d()?;
            Val::from(val)
        }
        "V128" => {
            let bytes = env
                .call_method(obj, "v128", "()[B", &[])?
                .l()?
                .into_raw();
            let bytes = env.convert_byte_array(unsafe { JByteArray::from_raw(bytes as _) })?;
            if bytes.len() != 16 {
                return Err(Error::Wasmtime(anyhow!(
                    "v128 value must contain exactly 16 bytes"
                )));
            }
            let mut raw = [0u8; 16];
            raw.copy_from_slice(&bytes);
            Val::V128(u128::from_le_bytes(raw).into())
        }
        "NULL_FUNC_REF" => Val::FuncRef(None),
        "FUNC_REF" => {
            let func_obj = env
                .call_method(obj, "funcRef", "()Lio/github/kawamuray/wasmtime/Func;", &[])?
                .l()?;
            if func_obj.is_null() {
                Val::FuncRef(None)
            } else {
                return Err(Error::NotImplemented);
            }
        }
        "EXTERN_REF" => {
            let extern_ref = env
                .call_method(obj, "externRef", "()Ljava/lang/Object;", &[])?
                .l()?;
            if extern_ref.is_null() {
                Val::AnyRef(None)
            } else {
                return Err(Error::NotImplemented);
            }
        }
        _ => return Err(Error::UnknownEnum(name)),
    })
}

pub fn into_java<'a>(env: &mut JNIEnv<'a>, val: Val) -> Result<JObject<'a>> {
    Ok(match val {
        Val::I32(v) => env
            .call_static_method(
                "io/github/kawamuray/wasmtime/Val",
                "fromI32",
                "(I)Lio/github/kawamuray/wasmtime/Val;",
                &[v.into()],
            )?
            .l()?,
        Val::I64(v) => env
            .call_static_method(
                "io/github/kawamuray/wasmtime/Val",
                "fromI64",
                "(J)Lio/github/kawamuray/wasmtime/Val;",
                &[v.into()],
            )?
            .l()?,
        Val::F32(v) => env
            .call_static_method(
                "io/github/kawamuray/wasmtime/Val",
                "fromF32",
                "(F)Lio/github/kawamuray/wasmtime/Val;",
                &[f32::from_bits(v).into()],
            )?
            .l()?,
        Val::F64(v) => env
            .call_static_method(
                "io/github/kawamuray/wasmtime/Val",
                "fromF64",
                "(D)Lio/github/kawamuray/wasmtime/Val;",
                &[f64::from_bits(v).into()],
            )?
            .l()?,
        Val::V128(v) => {
            let bytes = env.byte_array_from_slice(&v.as_u128().to_le_bytes())?;
            env.call_static_method(
                "io/github/kawamuray/wasmtime/Val",
                "fromV128",
                "([B)Lio/github/kawamuray/wasmtime/Val;",
                &[(&bytes).into()],
            )?
            .l()?
        }
        Val::FuncRef(None) => env
            .call_static_method(
                "io/github/kawamuray/wasmtime/Val",
                "nullFuncRef",
                "()Lio/github/kawamuray/wasmtime/Val;",
                &[],
            )?
            .l()?,
        Val::FuncRef(Some(_)) => return Err(Error::NotImplemented),
        Val::AnyRef(None) => env
            .call_static_method(
                "io/github/kawamuray/wasmtime/Val",
                "fromExternRef",
                "(Ljava/lang/Object;)Lio/github/kawamuray/wasmtime/Val;",
                &[(&JObject::null()).into()],
            )?
            .l()?,
        Val::AnyRef(Some(_)) => return Err(Error::NotImplemented),
        _ => return Err(Error::NotImplemented),
    })
}

pub fn type_from_java(env: &mut JNIEnv, obj: JObject) -> Result<ValType> {
    let name = utils::enum_name(env, obj)?;
    Ok(match name.as_str() {
        "I32" => ValType::I32,
        "I64" => ValType::I64,
        "F32" => ValType::F32,
        "F64" => ValType::F64,
        "V128" => ValType::V128,
        "EXTERN_REF" => ValType::Ref(RefType::EXTERNREF),
        "FUNC_REF" => ValType::Ref(RefType::FUNCREF),
        "NULL_FUNC_REF" => ValType::Ref(RefType::NULLFUNCREF),
        _ => return Err(Error::UnknownEnum(name)),
    })
}

fn type_from_enum<'a>(env: &mut JNIEnv<'a>, ty: &'a str) -> Result<JObject<'a>> {
    Ok(env
        .get_static_field(VAL_TYPE, ty, "Lio/github/kawamuray/wasmtime/Val$Type;")?
        .l()?)
}

pub fn types_into_java_array(
    env: &mut JNIEnv,
    it: impl ExactSizeIterator<Item = ValType>,
) -> Result<jobjectArray> {
    let mut vec = Vec::with_capacity(it.len());
    for result in it {
        let x = self::val_type_into_java(env, result)?;
        vec.push(x)
    }
    into_java_array(env, VAL_TYPE, vec)
}

pub fn val_type_into_java<'a>(env: &mut JNIEnv<'a>, val: ValType) -> Result<JObject<'a>> {
    match val {
        ValType::I32 => type_from_enum(env, "I32"),
        ValType::I64 => type_from_enum(env, "I64"),
        ValType::F32 => type_from_enum(env, "F32"),
        ValType::F64 => type_from_enum(env, "F64"),
        ValType::V128 => type_from_enum(env, "V128"),
        ValType::Ref(ref_type) => ref_type_into_java(env, ref_type)
    }
}

pub fn ref_type_into_java<'a>(env: &mut JNIEnv<'a>, ref_type: RefType) -> Result<JObject<'a>> {
            if ref_type.matches(&RefType::EXTERNREF) {
                type_from_enum(env, "EXTERN_REF")
            } else if ref_type.matches(&RefType::FUNCREF) {
                type_from_enum(env, "FUNC_REF")
            } else if ref_type.matches(&RefType::NULLFUNCREF) {
                type_from_enum(env, "NULL_FUNC_REF")
            } else {
                Err(Error::UnknownEnum(format!("Cannot match ref type {}", &ref_type)))
            }
        }

pub fn types_from_java<'a>(env: &mut JNIEnv<'a>, array: jobjectArray) -> Result<Vec<ValType>> {
    let mut iter = utils::JavaArrayIter::new(env, array)?;
    let mut ret = Vec::with_capacity(iter.len());
    while let Some(obj) = iter.next(env) {
        let ty = type_from_java(env, obj?)?;
        ret.push(ty);
    }
    Ok(ret)
}
