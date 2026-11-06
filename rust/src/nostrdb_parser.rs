//! nostrdb's JSON parser, the one Damus and Notedeck parse every relay event with.
//!
//! nostrdb (damus-io, C) parses an event straight into its own note format: one block holding
//! the fixed fields, with `id`, `pubkey` and `sig` hex-decoded to bytes, the content and tag
//! strings decoded into a string table, and 64-hex tag values packed as 32-byte ids. Notedeck
//! hands it whole relay frames (`ndb_ws_event_from_json` on an ingester thread); an event line
//! goes through `ndb_note_from_json`, the same JSON parser (`ndb_json_parser_parse` +
//! `ndb_parse_json_note`) without the relay-message wrapper. nostrdb-rs keeps its C bindings
//! private and has no public "parse this JSON into a note", so the function is declared here
//! from `nostrdb.h` (nostrdb 12398c2, the submodule of nostrdb-rs e22e5632a75e, the revision
//! Notedeck pins) and resolves against the C library the `nostrdb` crate builds and links. The
//! note is then read with nostrdb-rs's own `Note` accessors.

use crate::model::{R, View, digest_fields};
use nostrdb::{NdbStrVariant, Note};
use sha2::Sha256;
use std::ffi::{c_char, c_int, c_void};

unsafe extern "C" {
    fn ndb_note_from_json(json: *const c_char, len: c_int, note: *mut *mut c_void, buf: *mut u8, bufsize: c_int) -> c_int;
}

/// Parses `json` into `buf`, sized as nostrdb's ingester sizes it (max(len x 8, 4096) bytes),
/// and returns the note and its size in bytes. The buffer is `u64`s so the note is aligned.
#[inline(always)]
fn parse_into<'b>(buf: &'b mut Vec<u64>, json: &str) -> R<(Note<'b>, usize)> {
    let need = (json.len() * 8).max(4096).div_ceil(8);
    if buf.len() < need {
        buf.resize(need, 0);
    }
    let mut note: *mut c_void = std::ptr::null_mut();
    let size = unsafe {
        ndb_note_from_json(
            json.as_ptr().cast(),
            json.len() as c_int,
            &mut note,
            buf.as_mut_ptr().cast(),
            (buf.len() * 8) as c_int,
        )
    };
    if size <= 0 || note.is_null() {
        return Err(format!("nostrdb: ndb_note_from_json returned {size}"));
    }
    Ok((unsafe { note_at(note) }, size as usize))
}

/// A nostrdb-rs `Note` over a note somewhere in memory (the `Unowned` variant: not freed on drop).
#[inline(always)]
unsafe fn note_at<'a>(p: *const c_void) -> Note<'a> {
    Note::Unowned { ptr: unsafe { &*(p as *const _) } }
}

/// A note in the parser's reused buffer: valid until the next parse.
pub struct NdbNote<'b>(Note<'b>);

/// A note copied out of the parser's buffer into a block of its own, as nostrdb's ingester does
/// before it hands the note on (`realloc(note, note_size)`): what an app can keep.
pub struct NdbOwnedNote(Vec<u64>);

impl NdbOwnedNote {
    #[inline(always)]
    fn note(&self) -> Note<'_> {
        unsafe { note_at(self.0.as_ptr().cast()) }
    }
}

pub fn parse_borrowed<'b>(buf: &'b mut Vec<u64>, json: &str) -> R<NdbNote<'b>> {
    Ok(NdbNote(parse_into(buf, json)?.0))
}

pub fn parse_owned(buf: &mut Vec<u64>, json: &str) -> R<NdbOwnedNote> {
    let size = parse_into(buf, json)?.1;
    Ok(NdbOwnedNote(buf[..size.div_ceil(8)].to_vec()))
}

#[inline(always)]
fn fold_note(n: &Note<'_>) -> u64 {
    (n.kind() as u64).wrapping_add(n.tags().count() as u64).wrapping_add(n.content().len() as u64)
}

fn hex(bytes: &[u8]) -> String {
    const H: &[u8; 16] = b"0123456789abcdef";
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push(H[(b >> 4) as usize] as char);
        s.push(H[(b & 15) as usize] as char);
    }
    s
}

/// Untimed: the decoded event, with the bytes nostrdb decoded (id, pubkey, sig, packed tag
/// ids) written back as lowercase hex, as the corpus has them.
fn digest_note(n: &Note<'_>, h: &mut Sha256) -> R<()> {
    let tags: Vec<Vec<String>> = n
        .tags()
        .iter()
        .map(|t| {
            t.into_iter()
                .map(|s| match s.variant() {
                    NdbStrVariant::Str(s) => s.to_string(),
                    NdbStrVariant::Id(id) => hex(id),
                })
                .collect()
        })
        .collect();
    digest_fields(
        h,
        &hex(n.id()),
        &hex(n.pubkey()),
        n.created_at(),
        n.kind() as u64,
        tags.iter().map(|t| t.iter().map(|s| s.as_str())),
        n.content(),
        &hex(n.sig()),
    );
    Ok(())
}

impl View for NdbNote<'_> {
    #[inline(always)]
    fn fold(&self) -> u64 {
        fold_note(&self.0)
    }
    fn digest(&self, h: &mut Sha256) -> R<()> {
        digest_note(&self.0, h)
    }
}

impl View for NdbOwnedNote {
    #[inline(always)]
    fn fold(&self) -> u64 {
        fold_note(&self.note())
    }
    fn digest(&self, h: &mut Sha256) -> R<()> {
        digest_note(&self.note(), h)
    }
}
