package io.github.kawamuray.wasmtime;

import java.nio.file.Path;

public class Config implements Disposable{
    static {
        NativeLibraryLoader.init();
    }
    private final long innerPtr;

    public Config() {
        innerPtr = newConfig();
    }

    long innerPtr() {
        return innerPtr;
    }

    /// Configures whether DWARF debug information will be emitted during
    /// compilation.
    ///
    /// By default this option is `false`.
    public native Config debugInfo(boolean enable);

    /// Enables epoch-based interruption.
    ///
    /// When executing code in async mode, we sometimes want to
    /// implement a form of cooperative timeslicing: long-running Wasm
    /// guest code should periodically yield to the executor
    /// loop. This yielding could be implemented by using "fuel" (see
    /// [`consume_fuel`](Config::consume_fuel)). However, fuel
    /// instrumentation is somewhat expensive: it modifies the
    /// compiled form of the Wasm code so that it maintains a precise
    /// instruction count, frequently checking this count against the
    /// remaining fuel. If one does not need this precise count or
    /// deterministic interruptions, and only needs a periodic
    /// interrupt of some form, then It would be better to have a more
    /// lightweight mechanism.
    ///
    /// Epoch-based interruption is that mechanism. There is a global
    /// "epoch", which is a counter that divides time into arbitrary
    /// periods (or epochs). This counter lives on the
    /// [`Engine`](crate::Engine) and can be incremented by calling
    /// [`Engine::increment_epoch`](crate::Engine::increment_epoch).
    /// Epoch-based instrumentation works by setting a "deadline
    /// epoch". The compiled code knows the deadline, and at certain
    /// points, checks the current epoch against that deadline. It
    /// will yield if the deadline has been reached.
    ///
    /// The idea is that checking an infrequently-changing counter is
    /// cheaper than counting and frequently storing a precise metric
    /// (instructions executed) locally. The interruptions are not
    /// deterministic, but if the embedder increments the epoch in a
    /// periodic way (say, every regular timer tick by a thread or
    /// signal handler), then we can ensure that all async code will
    /// yield to the executor within a bounded time.
    ///
    /// The deadline check cannot be avoided by malicious wasm code. It is safe
    /// to use epoch deadlines to limit the execution time of untrusted
    /// code.
    ///
    /// The [`Store`](crate::Store) tracks the deadline, and controls
    /// what happens when the deadline is reached during
    /// execution. Several behaviors are possible:
    ///
    /// - Trap if code is executing when the epoch deadline is
    ///   met. See
    ///   [`Store::epoch_deadline_trap`](crate::Store::epoch_deadline_trap).
    ///
    /// - Call an arbitrary function. This function may chose to trap or
    ///   increment the epoch. See
    ///   [`Store::epoch_deadline_callback`](crate::Store::epoch_deadline_callback).
    ///
    /// - Yield to the executor loop, then resume when the future is
    ///   next polled. See
    ///   [`Store::epoch_deadline_async_yield_and_update`](crate::Store::epoch_deadline_async_yield_and_update).
    ///
    /// Trapping is the default. The yielding behaviour may be used for
    /// the timeslicing behavior described above.
    ///
    /// This feature is available with or without async support.
    /// However, without async support, the timeslicing behaviour is
    /// not available. This means epoch-based interruption can only
    /// serve as a simple external-interruption mechanism.
    ///
    /// An initial deadline must be set before executing code by calling
    /// [`Store::set_epoch_deadline`](crate::Store::set_epoch_deadline). If this
    /// deadline is not configured then wasm will immediately trap.
    ///
    /// ## When to use fuel vs. epochs
    ///
    /// In general, epoch-based interruption results in faster
    /// execution. This difference is sometimes significant: in some
    /// measurements, up to 2-3x. This is because epoch-based
    /// interruption does less work: it only watches for a global
    /// rarely-changing counter to increment, rather than keeping a
    /// local frequently-changing counter and comparing it to a
    /// deadline.
    ///
    /// Fuel, in contrast, should be used when *deterministic*
    /// yielding or trapping is needed. For example, if it is required
    /// that the same function call with the same starting state will
    /// always either complete or trap with an out-of-fuel error,
    /// deterministically, then fuel with a fixed bound should be
    /// used.
    ///
    /// # See Also
    ///
    /// - [`Engine::increment_epoch`](crate::Engine::increment_epoch)
    /// - [`Store::set_epoch_deadline`](crate::Store::set_epoch_deadline)
    /// - [`Store::epoch_deadline_trap`](crate::Store::epoch_deadline_trap)
    /// - [`Store::epoch_deadline_callback`](crate::Store::epoch_deadline_callback)
    /// - [`Store::epoch_deadline_async_yield_and_update`](crate::Store::epoch_deadline_async_yield_and_update)
    public native Config epochInterruption(boolean enable);

    /// Configures the maximum amount of native stack space available to
    /// executing WebAssembly code.
    ///
    /// WebAssembly code currently executes on the native call stack for its own
    /// call frames. WebAssembly, however, also has well-defined semantics on
    /// stack overflow. This is intended to be a knob which can help configure
    /// how much native stack space a wasm module is allowed to consume. Note
    /// that the number here is not super-precise, but rather wasm will take at
    /// most "pretty close to this much" stack space.
    ///
    /// If a wasm call (or series of nested wasm calls) take more stack space
    /// than the `size` specified then a stack overflow trap will be raised.
    ///
    /// By default this option is 1 MB.
    public native Config maxWasmStack(long size);

    /// Configures whether the WebAssembly threads proposal will be enabled for
    /// compilation.
    ///
    /// The [WebAssembly threads proposal][threads] is not currently fully
    /// standardized and is undergoing development. Additionally the support in
    /// wasmtime itself is still being worked on. Support for this feature can
    /// be enabled through this method for appropriate wasm modules.
    ///
    /// This feature gates items such as shared memories and atomic
    /// instructions. Note that enabling the threads feature will
    /// also enable the bulk memory feature.
    ///
    /// This is `false` by default.
    ///
    /// > **Note**: Wasmtime does not implement everything for the wasm threads
    /// > spec at this time, so bugs, panics, and possibly segfaults should be
    /// > expected. This should not be enabled in a production setting right
    /// > now.
    ///
    /// [threads]: https://github.com/webassembly/threads
    public native Config wasmThreads(boolean enable);

    /// Configures whether the WebAssembly reference types proposal will be
    /// enabled for compilation.
    ///
    /// The [WebAssembly reference types proposal][proposal] is not currently
    /// fully standardized and is undergoing development. Additionally the
    /// support in wasmtime itself is still being worked on. Support for this
    /// feature can be enabled through this method for appropriate wasm
    /// modules.
    ///
    /// This feature gates items such as the `externref` type and multiple tables
    /// being in a module. Note that enabling the reference types feature will
    /// also enable the bulk memory feature.
    ///
    /// This is `false` by default.
    ///
    /// > **Note**: Wasmtime does not implement everything for the reference
    /// > types proposal spec at this time, so bugs, panics, and possibly
    /// > segfaults should be expected. This should not be enabled in a
    /// > production setting right now.
    ///
    /// [proposal]: https://github.com/webassembly/reference-types
    public native Config wasmReferenceTypes(boolean enable);

    /// Configures whether the WebAssembly SIMD proposal will be
    /// enabled for compilation.
    ///
    /// The [WebAssembly SIMD proposal][proposal] is not currently
    /// fully standardized and is undergoing development. Additionally the
    /// support in wasmtime itself is still being worked on. Support for this
    /// feature can be enabled through this method for appropriate wasm
    /// modules.
    ///
    /// This feature gates items such as the `v128` type and all of its
    /// operators being in a module.
    ///
    /// This is `false` by default.
    ///
    /// > **Note**: Wasmtime does not implement everything for the wasm simd
    /// > spec at this time, so bugs, panics, and possibly segfaults should be
    /// > expected. This should not be enabled in a production setting right
    /// > now.
    ///
    /// [proposal]: https://github.com/webassembly/simd
    public native Config wasmSimd(boolean enable);

    /// Configures whether the WebAssembly bulk memory operations proposal will
    /// be enabled for compilation.
    ///
    /// The [WebAssembly bulk memory operations proposal][proposal] is not
    /// currently fully standardized and is undergoing development.
    /// Additionally the support in wasmtime itself is still being worked on.
    /// Support for this feature can be enabled through this method for
    /// appropriate wasm modules.
    ///
    /// This feature gates items such as the `memory.copy` instruction, passive
    /// data/table segments, etc, being in a module.
    ///
    /// This is `false` by default.
    ///
    /// [proposal]: https://github.com/webassembly/bulk-memory-operations
    public native Config wasmBulkMemory(boolean enable);

    /// Configures whether the WebAssembly multi-value proposal will
    /// be enabled for compilation.
    ///
    /// This feature gates functions and blocks returning multiple values in a
    /// module, for example.
    ///
    /// This is `true` by default.
    ///
    /// [proposal]: https://github.com/webassembly/multi-value
    public native Config wasmMultiValue(boolean enable);

    /// Configures which compilation strategy will be used for wasm modules.
    ///
    /// This method can be used to configure which compiler is used for wasm
    /// modules, and for more documentation consult the [`Strategy`] enumeration
    /// and its documentation.
    ///
    /// The default value for this is `Strategy::Auto`.
    ///
    /// # Errors
    ///
    /// Some compilation strategies require compile-time options of `wasmtime`
    /// itself to be set, but if they're not set and the strategy is specified
    /// here then an error will be returned.
    public native Config strategy(Strategy strategy);

    /// Creates a default profiler based on the profiling strategy choosen
    ///
    /// Profiler creation calls the type's default initializer where the purpose is
    /// really just to put in place the type used for profiling.
    public native Config profiler(ProfilingStrategy profile);

    /// Set a custom [`Cache`].
    ///
    /// To load a cache configuration from a file, use [`Cache::from_file`]. Otherwise, you can
    /// create a new cache config using [`CacheConfig::new`] and passing that to [`Cache::new`].
    ///
    /// If you want to disable the cache, you can call this method with `None`.
    ///
    /// By default, new configs do not have caching enabled.
    /// Every call to [`Module::new(my_wasm)`][crate::Module::new] will recompile `my_wasm`,
    /// even when it is unchanged, unless an enabled `CacheConfig` is provided.
    ///
    /// This method is only available when the `cache` feature of this crate is
    /// enabled.
    ///
    /// [docs]: https://bytecodealliance.github.io/wasmtime/cli-cache.html
    ///
    /// The file must be a valid Wasmtime cache TOML configuration. Passing `null`
    /// disables cache for this `Config`.
    public native Config cache(String path);

    /// Configures whether the debug verifier of Cranelift is enabled or not.
    ///
    /// When Cranelift is used as a code generation backend this will configure
    /// it to have the `enableVerifier` flag which will enable a number of debug
    /// checks inside of Cranelift. This is largely only useful for the
    /// developers of wasmtime itself.
    ///
    /// The default value for this is `false`
    public native Config craneliftDebugVerifier(boolean enable);

    /// Configures the Cranelift code generator optimization level.
    ///
    /// When the Cranelift code generator is used you can configure the
    /// optimization level used for generated code in a few various ways. For
    /// more information see the documentation of [`OptLevel`].
    ///
    /// The default value for this is `OptLevel::None`.
    public native Config craneliftOptLevel(OptLevel level);

    /// Configures whether Cranelift should perform a NaN-canonicalization pass.
    ///
    /// When Cranelift is used as a code generation backend this will configure
    /// it to replace NaNs with a single canonical value. This is useful for users
    /// requiring entirely deterministic WebAssembly computation.
    /// This is not required by the WebAssembly spec, so it is not enabled by default.
    ///
    /// The default value for this is `false`
    public native Config craneliftNanCanonicalization(boolean enable);

    /// Sets a custom memory creator
//    public native Config withHostMemory(MemoryCreator memCreator);

    /// Specifies the capacity of linear memories, in bytes, in their initial
    /// allocation.
    ///
    /// > Note: this value has important performance ramifications, be sure to
    /// > benchmark when setting this to a non-default value and read over this
    /// > documentation.
    ///
    /// This function will change the size of the initial memory allocation made
    /// for linear memories. This setting is only applicable when the initial
    /// size of a linear memory is below this threshold. Linear memories are
    /// allocated in the virtual address space of the host process with OS APIs
    /// such as `mmap` and this setting affects how large the allocation will
    /// be.
    ///
    /// ## Background: WebAssembly Linear Memories
    ///
    /// WebAssembly linear memories always start with a minimum size and can
    /// possibly grow up to a maximum size. The minimum size is always specified
    /// in a WebAssembly module itself and the maximum size can either be
    /// optionally specified in the module or inherently limited by the index
    /// type. For example for this module:
    ///
    /// ```wasm
    /// (module
    ///     (memory $a 4)
    ///     (memory $b 4096 4096 (pagesize 1))
    ///     (memory $c i64 10)
    /// )
    /// ```
    ///
    /// * Memory `$a` initially allocates 4 WebAssembly pages (256KiB) and can
    ///   grow up to 4GiB, the limit of the 32-bit index space.
    /// * Memory `$b` initially allocates 4096 WebAssembly pages, but in this
    ///   case its page size is 1, so it's 4096 bytes. Memory can also grow no
    ///   further meaning that it will always be 4096 bytes.
    /// * Memory `$c` is a 64-bit linear memory which starts with 640KiB of
    ///   memory and can theoretically grow up to 2^64 bytes, although most
    ///   hosts will run out of memory long before that.
    ///
    /// All operations on linear memories done by wasm are required to be
    /// in-bounds. Any access beyond the end of a linear memory is considered a
    /// trap.
    ///
    /// ## What this setting affects: Virtual Memory
    ///
    /// This setting is used to configure the behavior of the size of the linear
    /// memory allocation performed for each of these memories. For example the
    /// initial linear memory allocation looks like this:
    ///
    /// ```text
    ///              memory_reservation
    ///                    |
    ///          ◄─────────┴────────────────►
    /// ┌───────┬─────────┬──────────────────┬───────┐
    /// │ guard │ initial │ ... capacity ... │ guard │
    /// └───────┴─────────┴──────────────────┴───────┘
    ///  ◄──┬──►                              ◄──┬──►
    ///     │                                    │
    ///     │                             memory_guard_size
    ///     │
    ///     │
    ///  memory_guard_size (if guard_before_linear_memory)
    /// ```
    ///
    /// Memory in the `initial` range is accessible to the instance and can be
    /// read/written by wasm code. Memory in the `guard` regions is never
    /// accessible to wasm code and memory in `capacity` is initially
    /// inaccessible but may become accessible through `memory.grow` instructions
    /// for example.
    ///
    /// This means that this setting is the size of the initial chunk of virtual
    /// memory that a linear memory may grow into.
    ///
    /// ## What this setting affects: Runtime Speed
    ///
    /// This is a performance-sensitive setting which is taken into account
    /// during the compilation process of a WebAssembly module. For example if a
    /// 32-bit WebAssembly linear memory has a `memory_reservation` size of 4GiB
    /// then bounds checks can be elided because `capacity` will be guaranteed
    /// to be unmapped for all addressable bytes that wasm can access (modulo a
    /// few details).
    ///
    /// If `memory_reservation` was something smaller like 256KiB then that
    /// would have a much smaller impact on virtual memory but the compile code
    /// would then need to have explicit bounds checks to ensure that
    /// loads/stores are in-bounds.
    ///
    /// The goal of this setting is to enable skipping bounds checks in most
    /// modules by default. Some situations which require explicit bounds checks
    /// though are:
    ///
    /// * When `memory_reservation` is smaller than the addressable size of the
    ///   linear memory. For example if 64-bit linear memories always need
    ///   bounds checks as they can address the entire virtual address spacce.
    ///   For 32-bit linear memories a `memory_reservation` minimum size of 4GiB
    ///   is required to elide bounds checks.
    ///
    /// * When linear memories have a page size of 1 then bounds checks are
    ///   required. In this situation virtual memory can't be relied upon
    ///   because that operates at the host page size granularity where wasm
    ///   requires a per-byte level granularity.
    ///
    /// * Configuration settings such as [`Config::signals_based_traps`] can be
    ///   used to disable the use of signal handlers and virtual memory so
    ///   explicit bounds checks are required.
    ///
    /// * When [`Config::memory_guard_size`] is too small a bounds check may be
    ///   required. For 32-bit wasm addresses are actually 33-bit effective
    ///   addresses because loads/stores have a 32-bit static offset to add to
    ///   the dynamic 32-bit address. If the static offset is larger than the
    ///   size of the guard region then an explicit bounds check is required.
    ///
    /// ## What this setting affects: Memory Growth Behavior
    ///
    /// In addition to affecting bounds checks emitted in compiled code this
    /// setting also affects how WebAssembly linear memories are grown. The
    /// `memory.grow` instruction can be used to make a linear memory larger and
    /// this is also affected by APIs such as
    /// [`Memory::grow`](crate::Memory::grow).
    ///
    /// In these situations when the amount being grown is small enough to fit
    /// within the remaining capacity then the linear memory doesn't have to be
    /// moved at runtime. If the capacity runs out though then a new linear
    /// memory allocation must be made and the contents of linear memory is
    /// copied over.
    ///
    /// For example here's a situation where a copy happens:
    ///
    /// * The `memory_reservation` setting is configured to 128KiB.
    /// * A WebAssembly linear memory starts with a single 64KiB page.
    /// * This memory can be grown by one page to contain the full 128KiB of
    ///   memory.
    /// * If grown by one more page, though, then a 192KiB allocation must be
    ///   made and the previous 128KiB of contents are copied into the new
    ///   allocation.
    ///
    /// This growth behavior can have a significant performance impact if lots
    /// of data needs to be copied on growth. Conversely if memory growth never
    /// needs to happen because the capacity will always be large enough then
    /// optimizations can be applied to cache the base pointer of linear memory.
    ///
    /// When memory is grown then the
    /// [`Config::memory_reservation_for_growth`] is used for the new
    /// memory allocation to have memory to grow into.
    ///
    /// When using the pooling allocator via [`PoolingAllocationConfig`] then
    /// memories are never allowed to move so requests for growth are instead
    /// rejected with an error.
    ///
    /// ## When this setting is not used
    ///
    /// This setting is ignored and unused when the initial size of linear
    /// memory is larger than this threshold. For example if this setting is set
    /// to 1MiB but a wasm module requires a 2MiB minimum allocation then this
    /// setting is ignored. In this situation the minimum size of memory will be
    /// allocated along with [`Config::memory_reservation_for_growth`]
    /// after it to grow into.
    ///
    /// That means that this value can be set to zero. That can be useful in
    /// benchmarking to see the overhead of bounds checks for example.
    /// Additionally it can be used to minimize the virtual memory allocated by
    /// Wasmtime.
    ///
    /// ## Default Value
    ///
    /// The default value for this property depends on the host platform. For
    /// 64-bit platforms there's lots of address space available, so the default
    /// configured here is 4GiB. When coupled with the default size of
    /// [`Config::memory_guard_size`] this means that 32-bit WebAssembly linear
    /// memories with 64KiB page sizes will skip almost all bounds checks by
    /// default.
    ///
    /// For 32-bit platforms this value defaults to 10MiB. This means that
    /// bounds checks will be required on 32-bit platforms.
    public native Config memoryReservation(long maxSize);

    /// Configures the size, in bytes, of the guard region used at the end of a
    /// linear memory's address space reservation.
    ///
    /// > Note: this value has important performance ramifications, be sure to
    /// > understand what this value does before tweaking it and benchmarking.
    ///
    /// This setting controls how many bytes are guaranteed to be unmapped after
    /// the virtual memory allocation of a linear memory. When
    /// combined with sufficiently large values of
    /// [`Config::memory_reservation`] (e.g. 4GiB for 32-bit linear memories)
    /// then a guard region can be used to eliminate bounds checks in generated
    /// code.
    ///
    /// This setting additionally can be used to help deduplicate bounds checks
    /// in code that otherwise requires bounds checks. For example with a 4KiB
    /// guard region then a 64-bit linear memory which accesses addresses `x+8`
    /// and `x+16` only needs to perform a single bounds check on `x`. If that
    /// bounds check passes then the offset is guaranteed to either reside in
    /// linear memory or the guard region, resulting in deterministic behavior
    /// either way.
    ///
    /// ## How big should the guard be?
    ///
    /// In general, like with configuring [`Config::memory_reservation`], you
    /// probably don't want to change this value from the defaults. Removing
    /// bounds checks is dependent on a number of factors where the size of the
    /// guard region is only one piece of the equation. Other factors include:
    ///
    /// * [`Config::memory_reservation`]
    /// * The index type of the linear memory (e.g. 32-bit or 64-bit)
    /// * The page size of the linear memory
    /// * Other settings such as [`Config::signals_based_traps`]
    ///
    /// Embeddings using virtual memory almost always want at least some guard
    /// region, but otherwise changes from the default should be profiled
    /// locally to see the performance impact.
    ///
    /// ## Default
    ///
    /// The default value for this property is 32MiB on 64-bit platforms. This
    /// allows eliminating almost all bounds checks on loads/stores with an
    /// immediate offset of less than 32MiB. On 32-bit platforms this defaults
    /// to 64KiB.
    public native Config memoryGuardSize(long guardSize);

    private static native long newConfig();

    @Override
    public native void dispose();
}
