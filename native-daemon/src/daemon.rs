use std::fs;
use std::io::{self, Write};
use std::path::Path;

/// Write the current PID to the specified file.
pub fn write_pid(path: &str) -> io::Result<()> {
    let pid = std::process::id();
    let parent = Path::new(path).parent().ok_or_else(|| {
        io::Error::new(io::ErrorKind::InvalidInput, "invalid pid path")
    })?;
    if !parent.exists() {
        fs::create_dir_all(parent)?;
    }
    let mut f = fs::File::create(path)?;
    write!(f, "{}", pid)?;
    f.sync_all()?;
    println!("PID {} written to {}", pid, path);
    Ok(())
}

/// Read PID from file.
pub fn read_pid(path: &str) -> io::Result<u32> {
    let content = fs::read_to_string(path)?;
    let pid: u32 = content.trim().parse().map_err(|e| {
        io::Error::new(io::ErrorKind::InvalidData, format!("invalid PID: {}", e))
    })?;
    Ok(pid)
}

/// Check if a process is still alive by PID file (signal 0).
pub fn is_process_alive(path: &str) -> bool {
    match read_pid(path) {
        Ok(pid) => unsafe { libc::kill(pid as i32, 0) == 0 },
        Err(_) => false,
    }
}

/// Remove PID file.
pub fn cleanup_pid(path: &str) -> io::Result<()> {
    if Path::new(path).exists() {
        fs::remove_file(path)?;
        println!("PID file {} removed", path);
    }
    Ok(())
}
