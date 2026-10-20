//! A counting `#[global_allocator]` over the system allocator.
//!
//! The harness is single-threaded while it measures, so the counters use relaxed
//! load + store instead of a locked read-modify-write: that keeps the bookkeeping to a few
//! plain adds per allocation, not a `lock xadd` that would tax allocation-heavy parsers.
//!
//! Objects created (`allocs_per_event`): every `alloc`/`alloc_zeroed` is one heap block. A
//! `realloc` that moves the block is one new allocation (what Go's and the JVM's
//! grow-by-copy is); one the system allocator grows or shrinks in place creates nothing.
//! Bytes count the full new size of every `realloc`, moved or not (what a malloc + copy
//! would cost), matching how Go's `TotalAlloc` and the JVM's allocated-bytes counter see growth.
//!
//! For retained heap the allocator also counts freed bytes (`dealloc`, and the old size of a
//! `realloc`), so live bytes = allocated - freed, and live blocks: +1 per `alloc`, -1 per
//! `dealloc`, unchanged by `realloc` (the old block is gone, the new one takes its place).
//! Memory that C/C++ code allocates with its own `malloc` (simdjson-rust's parser) is not seen.

use std::alloc::{GlobalAlloc, Layout, System};
use std::sync::atomic::{AtomicU64, Ordering::Relaxed};

pub struct Counting;

static BYTES: AtomicU64 = AtomicU64::new(0);
static COUNT: AtomicU64 = AtomicU64::new(0);
static FREED: AtomicU64 = AtomicU64::new(0);
/// Blocks currently allocated (wrapping: +1 per alloc, -1 per dealloc).
static LIVE: AtomicU64 = AtomicU64::new(0);

#[inline(always)]
fn add(c: &AtomicU64, n: u64) {
    c.store(c.load(Relaxed).wrapping_add(n), Relaxed);
}

#[inline(always)]
fn bump(size: usize) {
    add(&BYTES, size as u64);
    add(&COUNT, 1);
    add(&LIVE, 1);
}

unsafe impl GlobalAlloc for Counting {
    #[inline]
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        let p = unsafe { System.alloc(layout) };
        if !p.is_null() {
            bump(layout.size());
        }
        p
    }

    #[inline]
    unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
        let p = unsafe { System.alloc_zeroed(layout) };
        if !p.is_null() {
            bump(layout.size());
        }
        p
    }

    #[inline]
    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        add(&FREED, layout.size() as u64);
        LIVE.store(LIVE.load(Relaxed).wrapping_sub(1), Relaxed);
        unsafe { System.dealloc(ptr, layout) }
    }

    #[inline]
    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        let p = unsafe { System.realloc(ptr, layout, new_size) };
        if !p.is_null() {
            add(&BYTES, new_size as u64);
            add(&FREED, layout.size() as u64);
            if p != ptr {
                add(&COUNT, 1);
            }
        }
        p
    }
}

/// (bytes, allocations) since process start.
#[inline]
pub fn snapshot() -> (u64, u64) {
    (BYTES.load(Relaxed), COUNT.load(Relaxed))
}

/// (bytes, blocks) currently allocated through this allocator: bytes are allocated - freed
/// since process start, blocks are allocs - deallocs.
#[inline]
pub fn live() -> (u64, u64) {
    (
        BYTES.load(Relaxed).wrapping_sub(FREED.load(Relaxed)),
        LIVE.load(Relaxed),
    )
}
