use super::JniTag;
use crate::errors;
use crate::interop;
use jni::objects::JObject;
use jni::JNIEnv;
use wasmtime::Tag;

pub(super) struct JniTagImpl;

impl<'a> JniTag<'a> for JniTagImpl {
    type Error = errors::Error;

    fn dispose(env: &mut JNIEnv<'a>, this: JObject<'a>) -> Result<(), Self::Error> {
        interop::dispose_inner::<Tag>(env, &this)?;
        Ok(())
    }
}
