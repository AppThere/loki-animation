//! AniCraft's own: a session starts with a stage, and the sample animation renders.

use super::*;

#[test]
fn a_new_session_has_a_stage_that_renders() {
    let s = loki_session_new();
    let info = call(s, "loki.surface", None)["result"].clone();
    assert_eq!(info["pages"], 1, "{info}");
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    let px = unsafe { loki_render_page(s, 0, 0.25, &mut w, &mut h, &mut n) };
    assert!(!px.is_null() && w > 0 && h > 0 && n == 1);
    unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    unsafe { loki_session_free(s) };
}

#[test]
fn the_sample_animation_has_something_to_draw() {
    let s = loki_session_new();
    assert_eq!(call(s, "loki.demo", None)["ok"], true);
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    let px = unsafe { loki_render_page(s, 0, 0.25, &mut w, &mut h, &mut n) };
    assert!(!px.is_null());
    // SAFETY: a buffer of w*h*4 bytes just returned.
    let bytes = unsafe { std::slice::from_raw_parts(px, w as usize * h as usize * 4) };
    let first = bytes.get(..4).map(<[u8]>::to_vec);
    let varied = bytes.chunks(4).any(|p| Some(p.to_vec()) != first);
    unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    assert!(varied, "the sample renders as one flat colour");
    unsafe { loki_session_free(s) };
}
