//! What is AniCraft's own: the session, how methods reach it, and the stage's frame.

use anicraft_engine::Session;
use serde_json::{Value, json};

use crate::Rendered;

pub(crate) struct Inner {
    s: Session,
}

/// A session with a new, empty document.
pub(crate) fn new() -> Inner {
    Inner { s: Session::default() }
}

/// `engine.execute {command, params}`, `engine.commands`, and `loki.demo`, which opens the engine's
/// sample animation.
pub(crate) fn call(inner: &mut Inner, method: &str, params: Value) -> Result<Value, String> {
    match method {
        "engine.execute" | "command" => {
            let id = params.get("command").or_else(|| params.get("id")).and_then(Value::as_str).ok_or("`command` required")?;
            let p = params.get("params").cloned().filter(|v| !v.is_null()).unwrap_or_else(|| json!({}));
            inner.s.execute(id, &p).map_err(|e| e.to_string())
        }
        "engine.commands" => Ok(Value::Array(anicraft_engine::commands().iter().map(anicraft_engine::CommandSpec::to_json).collect())),
        "loki.demo" => {
            inner.s = Session::new(anicraft_doc::sample::demo());
            Ok(json!({"ok": true}))
        }
        other => Err(format!("unknown method `{other}`")),
    }
}

/// Nothing is queued for the front end yet (the playbook's phase 6).
pub(crate) fn surface(inner: &mut Inner) -> Value {
    let st = &inner.s.doc.stage;
    json!({"kind": "stage", "pages": 1, "current": 0, "x": 0, "y": 0, "width": st.width, "height": st.height})
}

pub(crate) fn ui_requests(_inner: &mut Inner) -> Value {
    json!([])
}

/// The stage at the current frame, at `scale` pixels per stage unit, straight alpha.
pub(crate) fn render(inner: &mut Inner, page: u32, scale: f32, max_pixels: u64) -> Option<Rendered> {
    if page != 0 {
        return None;
    }
    let st = &inner.s.doc.stage;
    let (w, h) = (f64::from(st.width) * f64::from(scale), f64::from(st.height) * f64::from(scale));
    if w * h > max_pixels as f64 {
        return None;
    }
    let pix = anicraft_render::render_frame(&inner.s.doc, inner.s.frame, scale).ok()?;
    let rgba = pix.to_rgba8();
    if rgba.len() as u64 != u64::from(pix.width) * u64::from(pix.height) * 4 {
        return None;
    }
    Some(Rendered { width: pix.width, height: pix.height, pages: 1, rgba })
}
