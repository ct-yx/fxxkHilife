mod ipc;
mod bluetooth;
mod keepalive;
mod notify;
mod daemon;

use std::sync::Arc;
use tokio::sync::RwLock;
use tokio::signal;

/// Daemon configuration, read from environment variables with sensible defaults.
#[derive(Debug, Clone)]
pub struct DaemonConfig {
    /// Path to the Unix domain socket for IPC.
    pub socket_path: String,
    /// PID file path.
    pub pid_path: String,
    /// Directory for log output.
    pub log_dir: String,
    /// App package name (used for `am start`).
    pub app_package: String,
    /// App main activity (used for `am start`).
    pub app_activity: String,
    /// Interval in seconds between watchdog process checks.
    pub watchdog_interval_secs: u64,
    /// Interval in seconds between Bluetooth health checks.
    pub bt_health_interval_secs: u64,
    /// Maximum consecutive failures before declaring BT dead.
    pub bt_max_failures: u32,
}

impl Default for DaemonConfig {
    fn default() -> Self {
        Self {
            socket_path: "/tmp/fh_daemon.sock".into(),
            pid_path: "/data/local/tmp/fh_daemon.pid".into(),
            log_dir: "/data/local/tmp/fh_logs".into(),
            app_package: "com.freebuds.controller".into(),
            app_activity: "com.freebuds.controller.MainActivity".into(),
            watchdog_interval_secs: 30,
            bt_health_interval_secs: 15,
            bt_max_failures: 3,
        }
    }
}

impl DaemonConfig {
    /// Load config from environment variables, falling back to defaults.
    pub fn from_env() -> Self {
        Self {
            socket_path: std::env::var("FH_SOCKET_PATH").unwrap_or_else(|_| Self::default().socket_path),
            pid_path: std::env::var("FH_PID_PATH").unwrap_or_else(|_| Self::default().pid_path),
            log_dir: std::env::var("FH_LOG_DIR").unwrap_or_else(|_| Self::default().log_dir),
            app_package: std::env::var("FH_APP_PACKAGE").unwrap_or_else(|_| Self::default().app_package),
            app_activity: std::env::var("FH_APP_ACTIVITY").unwrap_or_else(|_| Self::default().app_activity),
            watchdog_interval_secs: std::env::var("FH_WATCHDOG_INTERVAL")
                .ok().and_then(|v| v.parse().ok())
                .unwrap_or(Self::default().watchdog_interval_secs),
            bt_health_interval_secs: std::env::var("FH_BT_HEALTH_INTERVAL")
                .ok().and_then(|v| v.parse().ok())
                .unwrap_or(Self::default().bt_health_interval_secs),
            bt_max_failures: std::env::var("FH_BT_MAX_FAILURES")
                .ok().and_then(|v| v.parse().ok())
                .unwrap_or(Self::default().bt_max_failures),
        }
    }
}

/// Shared mutable daemon state.
pub struct DaemonState {
    pub config: DaemonConfig,
    pub started_at: tokio::time::Instant,
    pub bt_connected: bool,
    pub bt_consecutive_failures: u32,
    pub app_alive: bool,
}

impl DaemonState {
    pub fn new(config: DaemonConfig) -> Self {
        Self {
            started_at: tokio::time::Instant::now(),
            config,
            bt_connected: false,
            bt_consecutive_failures: 0,
            app_alive: false,
        }
    }
}

pub type SharedState = Arc<RwLock<DaemonState>>;

#[tokio::main]
async fn main() {
    let config = DaemonConfig::from_env();
    let state: SharedState = Arc::new(RwLock::new(DaemonState::new(config.clone())));

    // Write PID file
    if let Err(e) = daemon::write_pid(&config.pid_path) {
        eprintln!("Warning: failed to write PID file: {}", e);
    }

    // Setup Ctrl+C handler for graceful shutdown
    let state_shutdown = state.clone();
    let pid_path = config.pid_path.clone();
    ctrlc::set_handler(move || {
        eprintln!("Shutdown signal received, cleaning up...");
        let rt = tokio::runtime::Handle::current();
        rt.block_on(async {
            let s = state_shutdown.read().await;
            let _ = daemon::cleanup_pid(&s.config.pid_path);
        });
        std::process::exit(0);
    })
    .expect("Error setting Ctrl+C handler");

    println!(
        "fxxkhilife_daemon v{} — socket={} pid={}",
        env!("CARGO_PKG_VERSION"),
        config.socket_path,
        config.pid_path,
    );

    // Start subsystems
    let ipc_handle = tokio::spawn(ipc::run_server(state.clone()));
    let bt_handle = tokio::spawn(bluetooth::run_monitor(state.clone()));
    let wd_handle = tokio::spawn(keepalive::run_watchdog(state.clone()));

    // Wait for shutdown signal (Ctrl+C or SIGTERM via tokio signal)
    tokio::select! {
        _ = signal::ctrl_c() => {
            println!("Ctrl+C received, shutting down...");
        }
        _ = ipc_handle => {}
        _ = bt_handle => {}
        _ = wd_handle => {}
    }

    // Cleanup
    let _ = daemon::cleanup_pid(&config.pid_path);
    println!("Daemon stopped.");
}
