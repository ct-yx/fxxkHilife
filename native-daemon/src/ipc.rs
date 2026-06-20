use crate::{DaemonState, SharedState};
use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::UnixListener;
use std::path::Path;

/// JSON-RPC-like request from the client.
#[derive(Deserialize)]
struct Request {
    id: String,
    method: String,
    #[serde(default)]
    params: serde_json::Value,
}

/// Successful response.
#[derive(Serialize)]
struct Response {
    id: String,
    result: serde_json::Value,
}

/// Error response.
#[derive(Serialize)]
struct ErrorResponse {
    id: String,
    error: String,
}

/// Start the Unix Domain Socket IPC server.
pub async fn run_server(state: SharedState) {
    let socket_path = {
        let s = state.read().await;
        s.config.socket_path.clone()
    };

    // Remove stale socket file
    if Path::new(&socket_path).exists() {
        let _ = std::fs::remove_file(&socket_path);
    }

    let listener = match UnixListener::bind(&socket_path) {
        Ok(l) => l,
        Err(e) => {
            eprintln!("IPC: failed to bind socket at {}: {}", socket_path, e);
            return;
        }
    };

    println!("IPC: listening on {}", socket_path);

    loop {
        match listener.accept().await {
            Ok((stream, _addr)) => {
                let state = state.clone();
                tokio::spawn(async move {
                    let (reader, mut writer) = stream.into_split();
                    let mut buf_reader = BufReader::new(reader);
                    let mut line = String::new();
                    loop {
                        line.clear();
                        match buf_reader.read_line(&mut line).await {
                            Ok(0) | Err(_) => break,
                            Ok(_) => {
                                let req: Request = match serde_json::from_str(line.trim()) {
                                    Ok(r) => r,
                                    Err(e) => {
                                        let err = ErrorResponse {
                                            id: "?".into(),
                                            error: format!("parse error: {}", e),
                                        };
                                        let _ = writer.write_all(
                                            serde_json::to_string(&err).unwrap().as_bytes()
                                        ).await;
                                        let _ = writer.write_all(b"\n").await;
                                        continue;
                                    }
                                };
                                let response = handle_request(&req, &state).await;
                                let _ = writer.write_all(response.as_bytes()).await;
                                let _ = writer.write_all(b"\n").await;
                            }
                        }
                    }
                });
            }
            Err(e) => eprintln!("IPC: accept error: {}", e),
        }
    }
}

async fn handle_request(req: &Request, state: &SharedState) -> String {
    match req.method.as_str() {
        "ping" => {
            let s = state.read().await;
            let uptime = s.started_at.elapsed().as_secs();
            ok(&req.id, serde_json::json!({
                "ok": true,
                "uptime_secs": uptime,
                "version": env!("CARGO_PKG_VERSION"),
            }))
        }
        "shutdown" => {
            tokio::spawn(async move {
                tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
                std::process::exit(0);
            });
            ok(&req.id, serde_json::json!({"ok": true}))
        }
        "bt_get_state" => {
            let s = state.read().await;
            ok(&req.id, serde_json::json!({
                "connected": s.bt_connected,
                "consecutive_failures": s.bt_consecutive_failures,
            }))
        }
        "wd_get_status" => {
            let s = state.read().await;
            ok(&req.id, serde_json::json!({
                "app_alive": s.app_alive,
                "interval_secs": s.config.watchdog_interval_secs,
            }))
        }
        "config_get" => {
            let s = state.read().await;
            ok(&req.id, serde_json::json!({
                "socket_path": s.config.socket_path,
                "pid_path": s.config.pid_path,
                "log_dir": s.config.log_dir,
                "app_package": s.config.app_package,
                "app_activity": s.config.app_activity,
                "watchdog_interval_secs": s.config.watchdog_interval_secs,
                "bt_health_interval_secs": s.config.bt_health_interval_secs,
                "bt_max_failures": s.config.bt_max_failures,
            }))
        }
        "notify_send" => {
            let title = req.params.get("title").and_then(|v| v.as_str()).unwrap_or("");
            let text = req.params.get("text").and_then(|v| v.as_str()).unwrap_or("");
            let priority = req.params.get("priority").and_then(|v| v.as_str()).unwrap_or("normal");
            match crate::notify::post_notification(title, text, priority) {
                Ok(_) => ok(&req.id, serde_json::json!({"ok": true})),
                Err(e) => err(&req.id, &format!("notification failed: {}", e)),
            }
        }
        "is_daemon_alive" => {
            let s = state.read().await;
            let alive = crate::daemon::is_process_alive(&s.config.pid_path);
            ok(&req.id, serde_json::json!({"alive": alive}))
        }
        _ => err(&req.id, &format!("unknown method: {}", req.method)),
    }
}

fn ok(id: &str, result: serde_json::Value) -> String {
    serde_json::to_string(&Response { id: id.into(), result }).unwrap()
}

fn err(id: &str, error: &str) -> String {
    serde_json::to_string(&ErrorResponse { id: id.into(), error: error.into() }).unwrap()
}
