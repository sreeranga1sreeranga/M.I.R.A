use std::process::Command;
use std::path::Path;
use tauri::{AppHandle, Manager, Emitter};

#[tauri::command]
fn convert_file_offline(app: AppHandle, file_path: String, action: String) -> Result<String, String> {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.hide();
    }

    // Calls your 100% offline Java conversion engine in the background
    std::thread::spawn(move || {
        let status = Command::new("java")
            .args(["-jar", "mira-engine.jar", &file_path, &action])
            .status();

        if status.is_ok() {
            #[cfg(target_os = "macos")]
            let _ = Command::new("afplay").arg("/System/Library/Sounds/Glass.aiff").spawn();
        }
    });

    Ok("Conversion started".into())
}

#[tauri::command]
fn hide_wheel(app: AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.hide();
    }
}