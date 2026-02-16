use super::JniModule;
use crate::errors::{self, Result};
use crate::wval::types_into_java_array;
use crate::{interop, utils, wmut, wval};
use anyhow::anyhow;
use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jbyteArray, jlong, jobjectArray};
use jni::JNIEnv;
use wasmtime::{Engine, ExternType, Module};

pub(super) struct JniModuleImpl;

const OBJECT_CLASS: &'static str = "java/lang/Object";
pub const IMPORT_TYPE_CLASS: &'static str = "io/github/kawamuray/wasmtime/ImportType$Type";

fn u64_to_jlong_checked(value: u64, what: &str) -> Result<jlong> {
    if value > jlong::MAX as u64 {
        return Err(errors::Error::Wasmtime(anyhow!(
            "{} exceeds Java long range: {}",
            what,
            value
        )));
    }
    Ok(value as jlong)
}

impl<'a> JniModule<'a> for JniModuleImpl {
    type Error = errors::Error;

    fn dispose(env: &mut JNIEnv<'a>, this: JObject<'a>) -> Result<(), Self::Error> {
        interop::dispose_inner::<Module>(env, &this)?;
        Ok(())
    }

    fn imports(
        env: &mut JNIEnv<'a>,
        this: JObject<'a>,
    ) -> std::result::Result<jobjectArray, Self::Error> {
        const STRING_CLASS: &str = "java/lang/String";
        const IMPORT_TYPE: &str = "io/github/kawamuray/wasmtime/ImportType";

        let module = interop::get_inner::<Module>(env, &this)?;
        let it = module.imports();
        let mut imports = Vec::with_capacity(it.len());
        for obj in it {
            let module = obj.module();
            let (ty_name, ty_obj) = extern_type_into_java(env, obj.ty())?;
            let ty = into_java_import_type(env, ty_name)?;

            let import = env.new_object(
                IMPORT_TYPE,
                format!(
                    "(L{};L{};L{};L{};)V",
                    IMPORT_TYPE_CLASS, OBJECT_CLASS, STRING_CLASS, STRING_CLASS
                ),
                &[
                    (&ty).into(),
                    (&ty_obj).into(),
                    (&(env.new_string(module)?)).into(),
                    (&(env.new_string(obj.name())?)).into(),
                ],
            )?;

            imports.push(import);
        }

        Ok(utils::into_java_array(env, IMPORT_TYPE, imports)?)
    }

    fn new_module(
        env: &mut JNIEnv<'a>,
        _clazz: JClass<'a>,
        engine_ptr: jlong,
        bytes: jbyteArray,
    ) -> Result<jlong, Self::Error> {
        let bytes = env.convert_byte_array(unsafe { JByteArray::from_raw(bytes) })?;
        let module = Module::new(&*interop::ref_from_raw::<Engine>(engine_ptr)?, &bytes)?;
        Ok(interop::into_raw::<Module>(module))
    }

    fn new_from_file(
        env: &mut JNIEnv<'a>,
        _clazz: JClass<'a>,
        engine_ptr: jlong,
        file_name: JString<'a>,
    ) -> Result<jlong, Self::Error> {
        let filename = utils::get_string(env, &file_name)?;
        let module = Module::from_file(&*interop::ref_from_raw::<Engine>(engine_ptr)?, &filename)?;
        Ok(interop::into_raw::<Module>(module))
    }

    fn new_from_binary(
        env: &mut JNIEnv<'a>,
        _clazz: JClass<'a>,
        engine_ptr: jlong,
        bytes: jbyteArray,
    ) -> Result<jlong, Self::Error> {
        let bytes = env.convert_byte_array(unsafe { JByteArray::from_raw(bytes) })?;
        let module = Module::from_binary(&*interop::ref_from_raw::<Engine>(engine_ptr)?, &bytes)?;
        Ok(interop::into_raw::<Module>(module))
    }

    fn native_serialize(
        env: &mut JNIEnv<'a>,
        this: JObject<'a>,
    ) -> Result<jbyteArray, Self::Error> {
        let module = interop::get_inner::<Module>(env, &this)?;
        let bytes = module.serialize()?;
        Ok(env.byte_array_from_slice(&bytes)?.into_raw())
    }

    fn native_deserialize(
        env: &mut JNIEnv<'a>,
        _clazz: JClass<'a>,
        engine_ptr: jlong,
        bytes: jbyteArray,
    ) -> Result<jlong, Self::Error> {
        let bytes = env.convert_byte_array(unsafe { JByteArray::from_raw(bytes) })?;
        let engine = interop::ref_from_raw::<Engine>(engine_ptr)?;
        let module = unsafe { Module::deserialize(&*engine, &bytes)? };
        Ok(interop::into_raw::<Module>(module))
    }
}

fn extern_type_into_java<'a>(
    env: &mut JNIEnv<'a>,
    ty: ExternType,
) -> Result<(&'static str, JObject<'a>)> {
    Ok(match ty {
        ExternType::Func(func) => {
            let results = types_into_java_array(env, func.results())?;
            let params = types_into_java_array(env, func.params())?;
            (
                "FUNC",
                env.new_object(
                    "io/github/kawamuray/wasmtime/FuncType",
                    format!("([L{};[L{};)V", wval::VAL_TYPE, wval::VAL_TYPE),
                    &[
                        (&unsafe { JObject::from_raw(params) }).into(),
                        (&unsafe { JObject::from_raw(results) }).into(),
                    ],
                )?,
            )
        }
        ExternType::Global(global) => {
            let java_ty = wval::val_type_into_java(env, global.content().to_owned())?;
            let java_muta = wmut::mutability_into_java(env, global.mutability())?;
            (
                "GLOBAL",
                env.new_object(
                    "io/github/kawamuray/wasmtime/GlobalType",
                    format!("(L{};L{};)V", wval::VAL_TYPE, wmut::MUT_TYPE),
                    &[(&java_ty).into(), (&java_muta).into()],
                )?,
            )
        }
        ExternType::Table(tab) => {
            let val = wval::ref_type_into_java(env, tab.element().to_owned())?;
            let min = u64_to_jlong_checked(tab.minimum().into(), "module table minimum elements")?;
            let max = match tab.maximum() {
                Some(v) => u64_to_jlong_checked(v.into(), "module table maximum elements")?,
                None => -1,
            };
            (
                "TABLE",
                env.new_object(
                    "io/github/kawamuray/wasmtime/TableType",
                    format!("(L{};JJ)V", wval::VAL_TYPE),
                    &[(&val).into(), JValue::Long(min), JValue::Long(max)],
                )?,
            )
        }
        ExternType::Memory(mem) => ("MEMORY", {
            let min = u64_to_jlong_checked(mem.minimum(), "module memory minimum pages")?;
            let max = match mem.maximum() {
                Some(v) => u64_to_jlong_checked(v, "module memory maximum pages")?,
                None => -1,
            };
            env.new_object(
                "io/github/kawamuray/wasmtime/MemoryType",
                "(JJZ)V",
                &[
                    JValue::Long(min),
                    JValue::Long(max),
                    JValue::Bool(mem.is_64() as u8),
                ],
            )?
        }),
        ExternType::Tag(_tag) => (
            "TAG",
            env.new_object("io/github/kawamuray/wasmtime/TagType", "()V", &[])?,
        ),
    })
}

pub fn into_java_import_type<'a>(
    env: &mut JNIEnv<'a>,
    ty: &'a str,
) -> jni::errors::Result<JObject<'a>> {
    env.get_static_field(IMPORT_TYPE_CLASS, ty, format!("L{};", IMPORT_TYPE_CLASS))?
        .l()
}
