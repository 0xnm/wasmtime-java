use jni::objects::GlobalRef;
use wasmtime_wasi::p1::WasiP1Ctx;

pub(crate) struct StoreData {
    pub wasi: Option<WasiP1Ctx>,
    pub java_data: Option<GlobalRef>,
}
