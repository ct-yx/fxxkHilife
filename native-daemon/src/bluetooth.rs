use crate::SharedState;
use std::time::Duration;

/// Monitor Bluetooth health via HCI socket (root) or fallback IPC.
/// Updates `bt_connected` and `bt_consecutive_failures` in shared state.
pub async fn run_monitor(state: SharedState) {
    let interval = {
        let s = state.read().await;
        Duration::from_secs(s.config.bt_health_interval_secs)
    };

    let mut consecutive_failures: u32 = 0;
    let max_failures = {
        let s = state.read().await;
        s.config.bt_max_failures
    };

    println!("BT monitor started (interval={:?}, max_failures={})", interval, max_failures);

    loop {
        tokio::time::sleep(interval).await;

        let connected = check_hci_state();

        let mut s = state.write().await;
        s.bt_connected = connected;
        if connected {
            consecutive_failures = 0;
            s.bt_consecutive_failures = 0;
        } else {
            consecutive_failures = consecutive_failures.saturating_add(1);
            s.bt_consecutive_failures = consecutive_failures;
            if consecutive_failures >= max_failures {
                println!("BT monitor: {} consecutive failures — health check degraded", consecutive_failures);
            }
        }
    }
}

/// Attempt to read HCI device state via `/sys/class/bluetooth/`.
/// Returns `true` if at least one HCI interface reports UP state.
/// This is a best-effort check; root is not required for sysfs reads.
fn check_hci_state() -> bool {
    let hci_dir = std::path::Path::new("/sys/class/bluetooth");
    if !hci_dir.exists() {
        return false;
    }

    let entries = match std::fs::read_dir(hci_dir) {
        Ok(e) => e,
        Err(_) => return false,
    };

    for entry in entries.flatten() {
        let name = entry.file_name();
        let name_str = name.to_string_lossy();
        if !name_str.starts_with("hci") {
            continue;
        }
        let uevent_path = entry.path().join("uevent");
        if let Ok(content) = std::fs::read_to_string(&uevent_path) {
            if content.contains("HCIUP=1") || content.contains("HCI_UP=1") {
                return true;
            }
        }
    }
    false
}
