/// Post a notification via `cmd notification post` (requires root/shell).
/// Returns an error if the command fails or if `cmd` is unavailable.
pub fn post_notification(title: &str, text: &str, priority: &str) -> Result<(), String> {
    // Build a simple notification using `cmd notification post`.
    // The tag/channel are hardcoded for fxxkHilife usage.
    let tag = "fxxkhilife_daemon";
    let channel = "fxxkhilife_daemon";

    // `cmd notification post` syntax (Android 8+):
    //   cmd notification post [--flags <flags>] <tag> <content>
    // We use --flags to set priority/ongoing style.
    let flags = match priority {
        "high" | "urgent" => "--flags 1",  // FLAG_HIGH_PRIORITY
        _ => "",
    };

    let content = format!("{}: {}", title, text);

    let result = std::process::Command::new("cmd")
        .args([
            "notification",
            "post",
            "--channel",
            channel,
            tag,
            &content,
        ])
        .output()
        .map_err(|e| format!("failed to execute cmd notification: {}", e))?;

    if result.status.success() {
        Ok(())
    } else {
        let stderr = String::from_utf8_lossy(&result.stderr);
        Err(format!("cmd notification failed: {}", stderr.trim()))
    }
}
