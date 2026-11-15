//! ratatui 渲染层。
use ratatui::layout::{Constraint, Direction, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Borders, List, ListItem, Paragraph, Tabs, Wrap};
use ratatui::Frame;

use crate::app::{App, VIEWS};

fn status_color(app: &App) -> Color {
    if app.busy.is_some() {
        Color::Yellow
    } else if app.heartbeat.is_healthy() {
        Color::Green
    } else {
        Color::Red
    }
}

fn status_text(app: &App) -> String {
    if let Some(label) = &app.busy {
        return label.clone();
    }
    if app.heartbeat.is_healthy() {
        "● 运行正常".to_string()
    } else {
        format!(
            "● 异常：{}",
            app.heartbeat
                .error
                .clone()
                .or_else(|| app.heartbeat.first_failure())
                .unwrap_or_else(|| "后端不可达或自检失败".to_string())
        )
    }
}

pub fn draw(frame: &mut Frame, app: &mut App) {
    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3),
            Constraint::Min(6),
            Constraint::Length(2),
        ])
        .split(frame.area());

    draw_tabs(frame, app, chunks[0]);
    match app.view {
        crate::app::View::Dashboard => draw_dashboard(frame, app, chunks[1]),
        crate::app::View::Services => draw_services(frame, app, chunks[1]),
        crate::app::View::Logs => draw_logs(frame, app, chunks[1]),
        crate::app::View::Config => draw_config(frame, app, chunks[1]),
        crate::app::View::Backup => draw_backup(frame, app, chunks[1]),
        crate::app::View::Reset => draw_reset(frame, app, chunks[1]),
        crate::app::View::About => draw_about(frame, app, chunks[1]),
    }
    draw_footer(frame, app, chunks[2]);
}

fn draw_tabs(frame: &mut Frame, app: &App, area: Rect) {
    let titles: Vec<Line> = VIEWS
        .iter()
        .map(|v| Line::from(format!(" {} ", v.title())))
        .collect();
    let selected = VIEWS.iter().position(|v| *v == app.view).unwrap_or(0);
    let tabs = Tabs::new(titles)
        .block(
            Block::default()
                .borders(Borders::ALL)
                .title(" 仁科兼容环境监测平台 · TUI 管理端 "),
        )
        .select(selected)
        .highlight_style(
            Style::default()
                .fg(Color::Black)
                .bg(Color::Cyan)
                .add_modifier(Modifier::BOLD),
        );
    frame.render_widget(tabs, area);
}

fn draw_dashboard(frame: &mut Frame, app: &App, area: Rect) {
    let mut lines = vec![
        Line::from(vec![
            Span::styled("状态：", Style::default().add_modifier(Modifier::BOLD)),
            Span::styled(status_text(app), Style::default().fg(status_color(app))),
        ]),
        Line::from(format!("平台地址：{}", app.config.api_base)),
        Line::from(format!("看板地址：{}", app.config.web_base)),
        Line::from(format!("后端 pid：{:?}", app.heartbeat.pid)),
    ];
    if let Some(uptime) = app.heartbeat.uptime_ms {
        lines.push(Line::from(format!(
            "运行时长：{} 分 {} 秒",
            uptime / 60000,
            (uptime / 1000) % 60
        )));
    }
    lines.push(Line::from(format!(
        "刷新周期：{} 秒",
        app.interval.as_secs()
    )));
    lines.push(Line::from(format!(
        "CPU：{:.1}%   内存：{} / {}",
        app.metrics.cpu_percent,
        crate::metrics::human(app.metrics.mem_used),
        crate::metrics::human(app.metrics.mem_total)
    )));
    lines.push(Line::from(format!(
        "磁盘：{} / {}",
        crate::metrics::human(app.metrics.disk_used),
        crate::metrics::human(app.metrics.disk_total)
    )));
    lines.push(Line::from(""));
    lines.push(Line::from(Span::styled(
        "自检项：",
        Style::default().add_modifier(Modifier::BOLD),
    )));
    if app.heartbeat.checks.is_empty() {
        lines.push(Line::from("  （无数据；后端不可达或检查项为空）"));
    } else {
        for item in &app.heartbeat.checks {
            let (mark, color) = match item.ok {
                Some(true) => ("✔", Color::Green),
                Some(false) => ("✘", Color::Red),
                None => ("•", Color::Gray),
            };
            lines.push(Line::from(vec![
                Span::styled(format!("  {} ", mark), Style::default().fg(color)),
                Span::raw(item.name.clone()),
                Span::styled(
                    format!(" {}", item.detail.clone().unwrap_or_default()),
                    Style::default().fg(Color::DarkGray),
                ),
            ]));
        }
    }
    let widget = Paragraph::new(lines)
        .block(Block::default().borders(Borders::ALL).title(" 状态总览 "))
        .wrap(Wrap { trim: false });
    frame.render_widget(widget, area);
}

fn draw_services(frame: &mut Frame, app: &App, area: Rect) {
    const ACTIONS: [&str; 6] = [
        "启动服务（经看门狗）",
        "停止服务（经看门狗）",
        "重启服务（经看门狗）",
        "立即刷新状态",
        "打开看板（系统默认浏览器）",
        "依赖自检（打开看板看门狗页）",
    ];
    let items: Vec<ListItem> = ACTIONS
        .iter()
        .map(|action| ListItem::new(Line::from(format!("  {}", action))))
        .collect();
    let list = List::new(items)
        .block(
            Block::default()
                .borders(Borders::ALL)
                .title(" 服务控制（上下选择，回车执行，启停/重启需二次确认） "),
        )
        .highlight_style(
            Style::default()
                .bg(Color::DarkGray)
                .add_modifier(Modifier::BOLD),
        )
        .highlight_symbol("▶ ");
    frame.render_stateful_widget(
        list,
        area,
        &mut ratatui::widgets::ListState::default().with_selected(Some(app.service_selected)),
    );
}

fn draw_logs(frame: &mut Frame, app: &App, area: Rect) {
    let cols = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(32), Constraint::Percentage(68)])
        .split(area);
    let files: Vec<ListItem> = if app.log_files.is_empty() {
        vec![ListItem::new("（未找到日志文件）")]
    } else {
        app.log_files
            .iter()
            .map(|p| {
                ListItem::new(Line::from(format!(
                    " {}",
                    p.file_name().and_then(|n| n.to_str()).unwrap_or("<未知>")
                )))
            })
            .collect()
    };
    let file_list = List::new(files)
        .block(Block::default().borders(Borders::ALL).title(" 日志文件 "))
        .highlight_style(Style::default().bg(Color::DarkGray))
        .highlight_symbol("▶ ");
    frame.render_stateful_widget(
        file_list,
        cols[0],
        &mut ratatui::widgets::ListState::default().with_selected(Some(app.log_selected)),
    );

    let filter = if app.log_filter.is_empty() {
        "（空：显示全部；格式如 ERROR 关键字）".to_string()
    } else {
        app.log_filter.clone()
    };
    let body: Vec<Line> = app
        .log_visible
        .iter()
        .map(|l| Line::from(l.clone()))
        .collect();
    let title = format!(
        " 日志内容（{} 行 / 原始 {} 行） 过滤：{} ",
        app.log_visible.len(),
        app.log_lines.len(),
        filter
    );
    let paragraph = Paragraph::new(body)
        .block(Block::default().borders(Borders::ALL).title(title))
        .wrap(Wrap { trim: false });
    frame.render_widget(paragraph, cols[1]);
}

fn draw_config(frame: &mut Frame, app: &App, area: Rect) {
    let lines = vec![
        Line::from("以下参数由环境变量决定（现场免改代码）："),
        Line::from(""),
        Line::from(format!(
            "程序目录 JDRK_HOME      ：{}",
            app.config.home.display()
        )),
        Line::from(format!("后端地址 JDRK_API_BASE  ：{}", app.config.api_base)),
        Line::from(format!("看板地址 JDRK_WEB_BASE  ：{}", app.config.web_base)),
        Line::from(format!(
            "后端包   JDRK_JAR       ：{}",
            app.config.jar.display()
        )),
        Line::from(format!(
            "Java     JDRK_JAVA      ：{}",
            app.config.java.display()
        )),
        Line::from(format!(
            "启动命令 JDRK_START_CMD ：{}",
            app.config
                .start_command
                .clone()
                .unwrap_or_else(|| "（未设置，走看门狗）".to_string())
        )),
        Line::from(format!(
            "停止命令 JDRK_STOP_CMD  ：{}",
            app.config
                .stop_command
                .clone()
                .unwrap_or_else(|| "（未设置，走看门狗）".to_string())
        )),
        Line::from(format!(
            "重启命令 JDRK_RESTART_CMD：{}",
            app.config
                .restart_command
                .clone()
                .unwrap_or_else(|| "（未设置，走看门狗）".to_string())
        )),
        Line::from(""),
        Line::from("回车打开 Web「系统参数配置」页（/system/config）进行修改。"),
    ];
    let widget = Paragraph::new(lines)
        .block(Block::default().borders(Borders::ALL).title(" 参数 "))
        .wrap(Wrap { trim: false });
    frame.render_widget(widget, area);
}

fn draw_backup(frame: &mut Frame, app: &App, area: Rect) {
    let lines = vec![
        Line::from("备份与恢复入口（FR-QT-07）：终端提供入口与进度提示，执行由 Web/后端完成。"),
        Line::from(""),
        Line::from(format!("备份与恢复页：{}", app.config.web_base) + "/env/io"),
        Line::from(""),
        Line::from("回车：在默认浏览器打开备份与恢复页。"),
        Line::from("说明：无图形/远程场景下可手动复制上面的地址。"),
    ];
    let widget = Paragraph::new(lines)
        .block(Block::default().borders(Borders::ALL).title(" 备份恢复 "))
        .wrap(Wrap { trim: false });
    frame.render_widget(widget, area);
}

fn draw_reset(frame: &mut Frame, app: &App, area: Rect) {
    let lines = vec![
        Line::from("离线重置超管密码（FR-QT-04）：要求后端已停止，凭操作系统管理员权限执行。"),
        Line::from(""),
        Line::from("回车进入输入模式（Esc 取消，输入完成后回车执行；至少 8 位）。"),
        Line::from(""),
        Line::from(if app.reset_mode {
            format!("新密码：{}", "*".repeat(app.reset_input.chars().count()))
        } else if app.reset_input.is_empty() {
            "新密码：（未输入）".to_string()
        } else {
            "新密码：（已输入，回车重新编辑）".to_string()
        }),
    ];
    let widget = Paragraph::new(lines)
        .block(Block::default().borders(Borders::ALL).title(" 离线重置 "))
        .wrap(Wrap { trim: false });
    frame.render_widget(widget, area);
}

fn draw_about(frame: &mut Frame, app: &App, area: Rect) {
    let lines = vec![
        Line::from(format!("jdrk-tui 版本：{}", env!("CARGO_PKG_VERSION"))),
        Line::from("后端版本接口：/monitor/system/version"),
        Line::from(""),
        Line::from(format!(
            "版本信息：{}",
            serde_json::to_string_pretty(&app.version).unwrap_or_else(|_| "{}".to_string())
        )),
    ];
    let widget = Paragraph::new(lines)
        .block(Block::default().borders(Borders::ALL).title(" 关于 "))
        .wrap(Wrap { trim: false });
    frame.render_widget(widget, area);
}

fn draw_footer(frame: &mut Frame, app: &App, area: Rect) {
    let mut lines: Vec<Line> = Vec::new();
    if let Some(pending) = app.confirm {
        let crate::app::Pending::Service(action) = pending;
        {
            let name = match action {
                "start" => "启动",
                "stop" => "停止",
                _ => "重启",
            };
            lines.push(Line::from(Span::styled(
                format!("确认要{}服务吗？(y/N)", name),
                Style::default()
                    .fg(Color::Yellow)
                    .add_modifier(Modifier::BOLD),
            )));
        }
    } else if let Some((text, is_error)) = &app.message {
        lines.push(Line::from(Span::styled(
            text.clone(),
            Style::default().fg(if *is_error { Color::Red } else { Color::Green }),
        )));
    } else {
        lines.push(Line::from(
            "Tab/←→ 切换页 · 1-7 直达 · r 刷新 · Enter 执行 · f 过滤日志 · q 退出",
        ));
    }
    lines.push(Line::from(format!(
        "{}  状态：{}",
        app.config.api_base,
        status_text(app)
    )));
    frame.render_widget(Paragraph::new(lines), area);
}
