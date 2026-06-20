use crate::SharedState;
use std::time::Duration;

/// Run the watchdog loop: periodically check if the app process is alive.
/// If dead, attempt to restart it via `am start`.
pub async fn run_watchdog(state: SharedState) {
    let (interval, pkg, activity) = {
        let s = state.read().await;
        (
            Duration::from_secs(s.config.watchdog_interval_secs),
            s.config.app_package.clone(),
            s.config.app_activity.clone(),
        )
    };

    println!("Watchdog started (interval={:?}, package={}/{}))", interval, pkg, activity);

    loop {
        tokio::time::sleep(interval).await;

        let alive = is_process_alive_by_pidof(&pkg);

        let mut s = state.write().await;
        s.app_alive = alive;

        if !alive {
            println!("Watchdog: app process not found, attempting restart...");
            // Fire-and-forget; don't block the watchdog loop
            tokio::spawn(try_restart_app(pkg.clone(), activity.clone()));
        }
    }
}

/// Check if the app process is alive using `pidof`.
fn is_process_alive_by_pidof(_package: &str) -> bool {
    // Use pidof to find process by package name
    let output = std::process::Command::new("pidof")
        .arg(_package)
        .output();

    match output {
        Ok(out) => out.status.success(),
        Err(_) => {
            // Fallback: scan /proc for the package name
            fallback_proc_check(_package)
        }
    }
}

/// Fallback: scan `/proc/[pid]/cmdline` for the package name.
fn fallback_proc_check(package: &str) -> bool {
    let proc_dir = std::path::Path::new("/proc");
    let entries = match std::fs::read_dir(proc_dir) {
        Ok(e) => e,
        Err(_) => return false,
    };

    for entry in entries.flatten() {
        let name = entry.file_name();
        let name_str = name.to_string_lossy();
        if !name_str.chars().all(|c| c.is_ascii_digit()) {
            continue;
        }
        let cmdline_path = entry.path().join("cmdline");
        if let Ok(content) = std::fs::read_to_string(&cmdline_path) {
            if content.contains(package) {
                return true;
            }
        }
    }
    false
}

/// Attempt to restart the app via `am start`.
async fn try_restart_app(package: String, activity: String) {
    let component = format!("{}/{}", package, activity);

    // Try `am start` (requires root or shell uid)
    let result = std::process::Command::new("am")
        .args(["start", "-n", &component])
        .output();

    match result {
        Ok(out) => {
            if out.status.success() {
                println!("Watchdog: restart command sent successfully for {}", component);
            } else {
                let stderr = String::from_utf8_lossy(&out.stderr);
                eprintln!("Watchdog: am start failed: {}", stderr);
            }
        }
        Err(e) => {
            eprintln!("Watchdog: failed to execute am start: {}", e);
        }
    }
}
