use crate::errors::{Error, Result};
use crate::{interop, utils};
use jni::objects::JObject;
use jni::JNIEnv;
use wasmtime::{Extern, Func, Global, Memory, Table, Tag};

pub fn from_java<'a>(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Extern> {
    let ty = env
        .get_field(&obj, "type", "Lio/github/kawamuray/wasmtime/Extern$Type;")?
        .l()?;
    let name = utils::enum_name(env, ty)?;
    let extn = match name.as_str() {
        "FUNC" => {
            let fn_obj = env
                .call_method(obj, "func", "()Lio/github/kawamuray/wasmtime/Func;", &[])?
                .l()?;
            let func = interop::get_inner::<Func>(env, &fn_obj)?;
            Extern::from(func.clone())
        }
        "MEMORY" => {
            let mem_obj = env
                .call_method(
                    obj,
                    "memory",
                    "()Lio/github/kawamuray/wasmtime/Memory;",
                    &[],
                )?
                .l()?;
            let memory = interop::get_inner::<Memory>(env, &mem_obj)?;
            Extern::from(memory.clone())
        }
        "TABLE" => {
            let table_obj = env
                .call_method(obj, "table", "()Lio/github/kawamuray/wasmtime/Table;", &[])?
                .l()?;
            let table = interop::get_inner::<Table>(env, &table_obj)?;
            Extern::from(table.clone())
        }
        "GLOBAL" => {
            let global_obj = env
                .call_method(obj, "global", "()Lio/github/kawamuray/wasmtime/Global;", &[])?
                .l()?;
            let global = interop::get_inner::<Global>(env, &global_obj)?;
            Extern::from(global.clone())
        }
        "TAG" => {
            let tag_obj = env
                .call_method(obj, "tag", "()Lio/github/kawamuray/wasmtime/Tag;", &[])?
                .l()?;
            let tag = interop::get_inner::<Tag>(env, &tag_obj)?;
            Extern::from(tag.clone())
        }
        _ => return Err(Error::UnknownEnum(name)),
    };
    Ok(extn)
}

pub fn into_java<'a>(env: &mut JNIEnv<'a>, ext: Extern) -> Result<JObject<'a>> {
    Ok(match ext {
        Extern::Func(func) => {
            let fn_obj = env.new_object(
                "io/github/kawamuray/wasmtime/Func",
                "(J)V",
                &[interop::into_raw::<Func>(func).into()],
            )?;
            env.call_static_method(
                "io/github/kawamuray/wasmtime/Extern",
                "fromFunc",
                "(Lio/github/kawamuray/wasmtime/Func;)Lio/github/kawamuray/wasmtime/Extern;",
                &[(&fn_obj).into()],
            )?
            .l()?
        }
        Extern::Memory(memory) => {
            let mem_obj = env.new_object(
                "io/github/kawamuray/wasmtime/Memory",
                "(J)V",
                &[interop::into_raw::<Memory>(memory).into()],
            )?;
            env.call_static_method(
                "io/github/kawamuray/wasmtime/Extern",
                "fromMemory",
                "(Lio/github/kawamuray/wasmtime/Memory;)Lio/github/kawamuray/wasmtime/Extern;",
                &[(&mem_obj).into()],
            )?
            .l()?
        }
        Extern::Table(table) => {
            let table_obj = env.new_object(
                "io/github/kawamuray/wasmtime/Table",
                "(J)V",
                &[interop::into_raw::<Table>(table).into()],
            )?;
            env.call_static_method(
                "io/github/kawamuray/wasmtime/Extern",
                "fromTable",
                "(Lio/github/kawamuray/wasmtime/Table;)Lio/github/kawamuray/wasmtime/Extern;",
                &[(&table_obj).into()],
            )?
            .l()?
        }
        Extern::Global(global) => {
            let global_obj = env.new_object(
                "io/github/kawamuray/wasmtime/Global",
                "(J)V",
                &[interop::into_raw::<Global>(global).into()],
            )?;
            env.call_static_method(
                "io/github/kawamuray/wasmtime/Extern",
                "fromGlobal",
                "(Lio/github/kawamuray/wasmtime/Global;)Lio/github/kawamuray/wasmtime/Extern;",
                &[(&global_obj).into()],
            )?
            .l()?
        }
        Extern::Tag(tag) => {
            let tag_obj = env.new_object(
                "io/github/kawamuray/wasmtime/Tag",
                "(J)V",
                &[interop::into_raw::<Tag>(tag).into()],
            )?;
            env.call_static_method(
                "io/github/kawamuray/wasmtime/Extern",
                "fromTag",
                "(Lio/github/kawamuray/wasmtime/Tag;)Lio/github/kawamuray/wasmtime/Extern;",
                &[(&tag_obj).into()],
            )?
            .l()?
        }
        _ => return Err(Error::NotImplemented),
    })
}

pub fn unknown<'a>(env: &mut JNIEnv<'a>) -> Result<JObject<'a>> {
    Ok(env
        .get_static_field(
            "io/github/kawamuray/wasmtime/Extern",
            "UNKNOWN",
            "Lio/github/kawamuray/wasmtime/Extern;",
        )?
        .l()?)
}
