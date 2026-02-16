use jni::objects::GlobalRef;
use jni::JavaVM;
use std::io;
use std::pin::Pin;
use std::sync::Arc;
use std::task::{Context, Poll};
use tokio::io::{AsyncRead, AsyncWrite, ReadBuf};
use wasmtime_wasi::cli::{IsTerminal, StdinStream, StdoutStream};

fn jni_error_to_io(env: &mut jni::AttachGuard<'_>, e: jni::errors::Error, context: &str) -> io::Error {
    if let jni::errors::Error::JavaException = e {
        let _ = env.exception_clear();
        return io::Error::other(format!("{}: Java exception", context));
    }
    io::Error::other(format!("{}: {}", context, e))
}

/// Wrapper for Java InputStream that implements wasmtime-wasi's StdinStream trait
pub struct JavaInputStream {
    jvm: Arc<JavaVM>,
    stream: Arc<GlobalRef>,
}

impl JavaInputStream {
    pub fn new(jvm: JavaVM, stream: GlobalRef) -> Self {
        Self {
            jvm: Arc::new(jvm),
            stream: Arc::new(stream),
        }
    }
}

impl IsTerminal for JavaInputStream {
    fn is_terminal(&self) -> bool {
        false
    }
}

impl StdinStream for JavaInputStream {
    fn async_stream(&self) -> Box<dyn AsyncRead + Send + Sync> {
        Box::new(JavaAsyncReader {
            jvm: Arc::clone(&self.jvm),
            stream: Arc::clone(&self.stream),
        })
    }
}

/// Async reader that wraps a Java InputStream
struct JavaAsyncReader {
    jvm: Arc<JavaVM>,
    stream: Arc<GlobalRef>,
}

// SAFETY: The JNI GlobalRef is safe to send between threads
unsafe impl Send for JavaAsyncReader {}
unsafe impl Sync for JavaAsyncReader {}

impl AsyncRead for JavaAsyncReader {
    fn poll_read(
        self: Pin<&mut Self>,
        _cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        let this = self.get_mut();

        let mut env = match this.jvm.attach_current_thread() {
            Ok(env) => env,
            Err(e) => return Poll::Ready(Err(io::Error::other(format!("Failed to attach JVM: {}", e)))),
        };

        let size = buf.remaining();
        if size == 0 {
            return Poll::Ready(Ok(()));
        }

        // Create byte array in Java
        let byte_array = match env.new_byte_array(size as i32) {
            Ok(arr) => arr,
            Err(e) => return Poll::Ready(Err(io::Error::other(format!("Failed to create byte array: {}", e)))),
        };

        // Call Java InputStream.read(byte[])
        let bytes_read = match env.call_method(
            this.stream.as_obj(),
            "read",
            "([B)I",
            &[(&byte_array).into()],
        ) {
            Ok(val) => match val.i() {
                Ok(i) => i,
                Err(e) => return Poll::Ready(Err(jni_error_to_io(&mut env, e, "Failed to get read result"))),
            },
            Err(e) => return Poll::Ready(Err(jni_error_to_io(&mut env, e, "Failed to call read"))),
        };

        if bytes_read == -1 {
            // End of stream
            return Poll::Ready(Ok(()));
        }

        if bytes_read == 0 {
            return Poll::Ready(Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "InputStream.read returned 0 for a non-empty buffer",
            )));
        }

        // Get bytes from Java array
        let mut temp_buf = vec![0i8; bytes_read as usize];
        if let Err(e) = env.get_byte_array_region(&byte_array, 0, &mut temp_buf) {
            return Poll::Ready(Err(jni_error_to_io(&mut env, e, "Failed to get bytes")));
        }

        // Copy to output buffer
        let bytes: Vec<u8> = temp_buf.iter().map(|&b| b as u8).collect();
        buf.put_slice(&bytes);

        Poll::Ready(Ok(()))
    }
}

/// Wrapper for Java OutputStream that implements wasmtime-wasi's StdoutStream trait
pub struct JavaOutputStream {
    jvm: Arc<JavaVM>,
    stream: Arc<GlobalRef>,
}

impl JavaOutputStream {
    pub fn new(jvm: JavaVM, stream: GlobalRef) -> Self {
        Self {
            jvm: Arc::new(jvm),
            stream: Arc::new(stream),
        }
    }
}

impl IsTerminal for JavaOutputStream {
    fn is_terminal(&self) -> bool {
        false
    }
}

impl StdoutStream for JavaOutputStream {
    fn async_stream(&self) -> Box<dyn AsyncWrite + Send + Sync> {
        Box::new(JavaAsyncWriter {
            jvm: Arc::clone(&self.jvm),
            stream: Arc::clone(&self.stream),
        })
    }
}

/// Async writer that wraps a Java OutputStream
struct JavaAsyncWriter {
    jvm: Arc<JavaVM>,
    stream: Arc<GlobalRef>,
}

// SAFETY: The JNI GlobalRef is safe to send between threads
unsafe impl Send for JavaAsyncWriter {}
unsafe impl Sync for JavaAsyncWriter {}

impl AsyncWrite for JavaAsyncWriter {
    fn poll_write(
        self: Pin<&mut Self>,
        _cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<io::Result<usize>> {
        let this = self.get_mut();

        let mut env = match this.jvm.attach_current_thread() {
            Ok(env) => env,
            Err(e) => return Poll::Ready(Err(io::Error::other(format!("Failed to attach JVM: {}", e)))),
        };

        // Create byte array in Java
        let byte_array = match env.byte_array_from_slice(buf) {
            Ok(arr) => arr,
            Err(e) => return Poll::Ready(Err(io::Error::other(format!("Failed to create byte array: {}", e)))),
        };

        // Call Java OutputStream.write(byte[])
        if let Err(e) = env.call_method(
            this.stream.as_obj(),
            "write",
            "([B)V",
            &[(&byte_array).into()],
        ) {
            return Poll::Ready(Err(jni_error_to_io(&mut env, e, "Failed to call write")));
        }

        Poll::Ready(Ok(buf.len()))
    }

    fn poll_flush(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        let this = self.get_mut();

        let mut env = match this.jvm.attach_current_thread() {
            Ok(env) => env,
            Err(e) => return Poll::Ready(Err(io::Error::other(format!("Failed to attach JVM: {}", e)))),
        };

        // Call Java OutputStream.flush()
        if let Err(e) = env.call_method(
            this.stream.as_obj(),
            "flush",
            "()V",
            &[],
        ) {
            return Poll::Ready(Err(jni_error_to_io(&mut env, e, "Failed to call flush")));
        }

        Poll::Ready(Ok(()))
    }

    fn poll_shutdown(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        // Flush on shutdown
        self.poll_flush(cx)
    }
}
