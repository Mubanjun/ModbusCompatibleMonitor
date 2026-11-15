//! TUI 状态机与事件循环。
use std::io;
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver, TryRecvError};
use std::time::{Duration, Instant};

use crossterm::event::{self, Event, KeyCode, KeyEventKind};
use crossterm::execute;
use crossterm::terminal::{
    disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen,
};
use ratatui::backend::CrosstermBackend;
use ratatui::Terminal;

use crate::api::{self, Heartbeat};
use crate::config::{env_seconds, ShellConfig};
use crate::{logs, reset, service, ui};

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum View {
    Dashboard,
    Services,
    Logs,
    Config,
    Backup,
    Reset,
    About,
}

pub const VIEWS: [View; 7] = [
    View::Dashboard,
    View::Services,
    View::Logs,
    View::Config,
    View::Backup,
    View::Reset,
    View::About,
];

impl View {
    pub fn title(self) -> &'static str {
        match self {
            View::Dashboard => "状态总览",
            View::Services => "服务控制",
            View::Logs => "日志",
            View::Config => "参数",
            View::Backup => "备份恢复",
            View::Reset => "离线重置",
            View::About => "关于",
        }
    }
}

#[derive(Clone, Copy)]
pub enum Pending {
    Service(&'static str),
}

pub struct App {
    pub config: ShellConfig,
    pub view: View,
    pub heartbeat: Heartbeat,
    pub version: serde_json::Value,
    pub message: Option<(String, bool)>,
    pub confirm: Option<Pending>,
    pub service_selected: usize,
    pub log_files: Vec<PathBuf>,
    pub log_selected: usize,
    pub log_lines: Vec<String>,
    pub log_visible: Vec<String>,
    pub log_filter: String,
    pub filter_mode: bool,
    pub reset_input: String,
    pub reset_mode: bool,
    pub rx: Option<Receiver<Result<String, String>>>,
    pub busy: Option<String>,
    pub metrics: crate::metrics::Metrics,
    pub system: sysinfo::System,
    pub last_refresh: Instant,
    pub interval: Duration,
    pub should_quit: bool,
}

impl App {
    pub fn new(config: ShellConfig, interval_override: Option<u64>) -> Self {
        let interval = match interval_override {
            Some(secs) => Duration::from_secs(secs.max(1)),
            None => env_seconds("JDRK_MONITOR_INTERVAL_SECONDS", 2, 1, 3600),
        };
        App {
            config,
            view: View::Dashboard,
            heartbeat: Heartbeat::default(),
            version: serde_json::json!({}),
            message: None,
            confirm: None,
            service_selected: 0,
            log_files: Vec::new(),
            log_selected: 0,
            log_lines: Vec::new(),
            log_visible: Vec::new(),
            log_filter: String::new(),
            filter_mode: false,
            reset_input: String::new(),
            reset_mode: false,
            rx: None,
            busy: None,
            metrics: crate::metrics::Metrics::default(),
            system: sysinfo::System::new(),
            last_refresh: Instant::now() - Duration::from_secs(3600),
            interval,
            should_quit: false,
        }
    }

    /// 周期刷新：只拉心跳与本机指标。
    ///
    /// 审计缺陷 77：原实现每次刷新都拉 /monitor/system/version，版本信息本应按需获取
    /// （原 Tauri 壳的运行时自检明确断言「版本接口不在轮询路径上」），改成按需后与旧口径一致。
    pub fn refresh(&mut self) {
        self.heartbeat = api::fetch_heartbeat(&self.config);
        self.metrics = crate::metrics::snapshot(&mut self.system);
        self.last_refresh = Instant::now();
    }

    pub fn refresh_version(&mut self) {
        self.version = api::fetch_version(&self.config);
    }

    pub fn set_message(&mut self, result: Result<String, String>) {
        match result {
            Ok(text) => self.message = Some((text, false)),
            Err(text) => self.message = Some((text, true)),
        }
    }

    fn spawn(
        &mut self,
        label: String,
        work: impl FnOnce() -> Result<String, String> + Send + 'static,
    ) {
        // 审计缺陷 74：忙时再次回车会覆盖 rx，旧命令的接收端被丢弃且两条命令并发执行；
        // 服务启停是重操作（最长 180s），这里直接拒绝并发。
        if self.rx.is_some() {
            self.message = Some(("已有操作正在执行，请稍候".to_string(), true));
            return;
        }
        let (tx, rx) = channel();
        self.busy = Some(label);
        self.rx = Some(rx);
        std::thread::spawn(move || {
            let _ = tx.send(work());
        });
    }

    pub fn start_service(&mut self, action: &'static str) {
        let config = self.config.clone();
        let label = format!("执行 {} …", action);
        self.spawn(label, move || service::run_service(action, &config));
    }

    pub fn do_reset(&mut self) {
        let password = self.reset_input.clone();
        let config = self.config.clone();
        self.reset_mode = false;
        self.spawn("离线重置中 …".to_string(), move || {
            reset::reset_password(&config, &password)
        });
    }

    pub fn refresh_logs(&mut self) {
        self.log_files = logs::list_logs(&logs::log_dir(&self.config));
        if self.log_selected >= self.log_files.len() {
            self.log_selected = 0;
        }
        self.reload_log_tail();
    }

    pub fn reload_log_tail(&mut self) {
        self.log_visible.clear();
        self.log_lines.clear();
        if let Some(path) = self.log_files.get(self.log_selected) {
            self.log_lines = logs::tail_lines(path, 2000);
            self.apply_log_filter();
        }
    }

    fn apply_log_filter(&mut self) {
        let (level, keyword) = split_filter(&self.log_filter);
        self.log_visible = logs::filter_lines(&self.log_lines, &level, &keyword);
    }

    pub fn open_web(&mut self, suffix: &str) {
        let url = format!("{}{}", self.config.web_base, suffix);
        match webbrowser::open(&url) {
            Ok(_) => self.message = Some((format!("已尝试打开 {}", url), false)),
            Err(e) => {
                self.message = Some((format!("打开浏览器失败：{}（地址：{}）", e, url), true))
            }
        }
    }

    pub fn handle_key(&mut self, code: KeyCode) {
        if self.filter_mode {
            match code {
                KeyCode::Esc | KeyCode::Enter => self.filter_mode = false,
                KeyCode::Backspace => {
                    self.log_filter.pop();
                    self.apply_log_filter();
                }
                KeyCode::Char(c) => {
                    self.log_filter.push(c);
                    self.apply_log_filter();
                }
                _ => {}
            }
            return;
        }
        if self.reset_mode {
            match code {
                KeyCode::Esc => self.reset_mode = false,
                KeyCode::Enter => self.do_reset(),
                KeyCode::Backspace => {
                    self.reset_input.pop();
                }
                KeyCode::Char(c) => self.reset_input.push(c),
                _ => {}
            }
            return;
        }
        if let Some(pending) = self.confirm {
            match code {
                KeyCode::Char('y') | KeyCode::Char('Y') => {
                    self.confirm = None;
                    let Pending::Service(action) = pending;
                    self.start_service(action);
                }
                KeyCode::Char('n') | KeyCode::Char('N') | KeyCode::Esc => self.confirm = None,
                _ => {}
            }
            return;
        }
        match code {
            KeyCode::Char('q') => self.should_quit = true,
            KeyCode::Tab | KeyCode::Right => self.next_view(1),
            KeyCode::BackTab | KeyCode::Left => self.next_view(-1),
            KeyCode::Char('1') => self.view = View::Dashboard,
            KeyCode::Char('2') => self.view = View::Services,
            KeyCode::Char('3') => self.view = View::Logs,
            KeyCode::Char('4') => self.view = View::Config,
            KeyCode::Char('5') => self.view = View::Backup,
            KeyCode::Char('6') => self.view = View::Reset,
            KeyCode::Char('7') => {
                self.view = View::About;
                self.refresh_version();
            }
            KeyCode::Char('r') => {
                self.refresh();
                if self.view == View::About {
                    self.refresh_version();
                }
            }
            _ => match self.view {
                View::Services => self.handle_service_key(code),
                View::Logs => self.handle_log_key(code),
                View::Backup => {
                    if code == KeyCode::Enter {
                        self.open_web("/env/io");
                    }
                }
                View::Config => {
                    if code == KeyCode::Enter {
                        self.open_web("/system/config");
                    }
                }
                View::Reset => {
                    if code == KeyCode::Enter {
                        self.reset_mode = true;
                    }
                }
                _ => {}
            },
        }
    }

    fn next_view(&mut self, delta: i32) {
        let idx = VIEWS.iter().position(|v| *v == self.view).unwrap_or(0) as i32;
        let next = (idx + delta).rem_euclid(VIEWS.len() as i32);
        self.view = VIEWS[next as usize];
        if self.view == View::About {
            self.refresh_version();
        }
    }

    fn handle_service_key(&mut self, code: KeyCode) {
        const ACTIONS: [(&str, &str); 6] = [
            ("启动服务", "start"),
            ("停止服务", "stop"),
            ("重启服务", "restart"),
            ("运行状态面板（心跳）", "refresh"),
            ("打开看板（浏览器）", "open"),
            ("依赖自检（打开看板页）", "selfcheck"),
        ];
        match code {
            KeyCode::Up => self.service_selected = self.service_selected.saturating_sub(1),
            KeyCode::Down => {
                if self.service_selected + 1 < ACTIONS.len() {
                    self.service_selected += 1;
                }
            }
            KeyCode::Enter => {
                let (_, action) = ACTIONS[self.service_selected];
                match action {
                    "start" => self.confirm = Some(Pending::Service("start")),
                    "stop" => self.confirm = Some(Pending::Service("stop")),
                    "restart" => self.confirm = Some(Pending::Service("restart")),
                    "refresh" => self.refresh(),
                    "open" => self.open_web(""),
                    "selfcheck" => self.open_web("/env/watchdog"),
                    _ => {}
                }
            }
            _ => {}
        }
    }

    fn handle_log_key(&mut self, code: KeyCode) {
        match code {
            KeyCode::Up => self.log_selected = self.log_selected.saturating_sub(1),
            KeyCode::Down => {
                if self.log_selected + 1 < self.log_files.len() {
                    self.log_selected += 1;
                }
            }
            KeyCode::Enter => self.reload_log_tail(),
            KeyCode::Char('f') => self.filter_mode = true,
            _ => {}
        }
    }

    /// 轮询后台命令结果；返回 true 表示本轮有更新。
    pub fn poll_background(&mut self) -> bool {
        let Some(rx) = self.rx.take() else {
            return false;
        };
        match rx.try_recv() {
            Ok(result) => {
                self.busy = None;
                self.set_message(result);
                self.refresh();
                true
            }
            Err(TryRecvError::Empty) => {
                self.rx = Some(rx);
                false
            }
            Err(TryRecvError::Disconnected) => {
                self.busy = None;
                false
            }
        }
    }
}

fn split_filter(filter: &str) -> (String, String) {
    let mut parts = filter.split_whitespace();
    let first = parts.next().unwrap_or("").to_string();
    const LEVELS: [&str; 5] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];
    if LEVELS.contains(&first.to_ascii_uppercase().as_str()) {
        (first, parts.collect::<Vec<_>>().join(" "))
    } else {
        (String::new(), filter.to_string())
    }
}

pub fn run(config: ShellConfig, interval_override: Option<u64>) -> Result<(), String> {
    enable_raw_mode().map_err(|e| format!("无法进入原始模式：{}", e))?;
    let mut stdout = io::stdout();
    execute!(stdout, EnterAlternateScreen).map_err(|e| format!("无法切换备用屏幕：{}", e))?;
    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend).map_err(|e| format!("初始化终端失败：{}", e))?;
    let mut app = App::new(config, interval_override);
    app.refresh_version();
    app.refresh();
    app.refresh_logs();
    let tick = Duration::from_millis(250);
    let result = loop {
        if let Err(e) = terminal.draw(|frame| ui::draw(frame, &mut app)) {
            break Err(format!("绘制失败：{}", e));
        }
        match event::poll(tick) {
            Ok(true) => match event::read() {
                Ok(Event::Key(key)) => {
                    if key.kind == KeyEventKind::Press {
                        app.handle_key(key.code);
                    }
                }
                Ok(_) => {}
                Err(e) => break Err(format!("读取事件失败：{}", e)),
            },
            Ok(false) => {}
            Err(e) => break Err(format!("事件轮询失败：{}", e)),
        }
        app.poll_background();
        if app.should_quit {
            break Ok(());
        }
        if app.last_refresh.elapsed() >= app.interval {
            app.refresh();
        }
    };
    disable_raw_mode().ok();
    execute!(terminal.backend_mut(), LeaveAlternateScreen).ok();
    terminal.show_cursor().ok();
    result
}

/// 非交互模式（FR-QT-08）：输出一次状态后退出，便于脚本与本机自检。
pub fn run_headless(config: &ShellConfig) -> Result<(), String> {
    let beat = api::fetch_heartbeat(config);
    let version = api::fetch_version(config);
    println!("平台地址: {}", config.api_base);
    println!("看板地址: {}", config.web_base);
    println!(
        "后端状态: {}",
        if beat.is_healthy() {
            "正常"
        } else {
            "异常"
        }
    );
    println!("pid: {:?}", beat.pid);
    if let Some(uptime) = beat.uptime_ms {
        println!("运行时长: {}s", uptime / 1000);
    }
    if let Some(error) = &beat.error {
        println!("错误: {}", error);
    }
    for item in &beat.checks {
        println!(
            "  检查 {}: {}",
            item.name,
            match item.ok {
                Some(true) => "通过",
                Some(false) => "失败",
                None => "未知",
            }
        );
    }
    println!("版本: {}", version);
    println!("程序目录: {}", config.home.display());
    println!("后端包: {}", config.jar.display());
    Ok(())
}
