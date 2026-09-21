import QtQuick

// 主题常量（由 Main.qml 实例化后传递给各页面/组件）
QtObject {
    readonly property color bg: "#0b1220"
    readonly property color surface: "#151d2e"
    readonly property color surfaceAlt: "#1b2537"
    readonly property color border: "#2a3548"
    readonly property color text: "#e6edf7"
    readonly property color textDim: "#8fa3bf"
    readonly property color accent: "#38bdf8"
    readonly property color accentDim: "#0ea5e9"
    readonly property color ok: "#22c55e"
    readonly property color warn: "#f59e0b"
    readonly property color error: "#ef4444"
    readonly property color idle: "#64748b"

    readonly property int radius: 10
    readonly property int gap: 12
    readonly property int fontSize: 13

    function qualityColor(q) {
        switch (q) {
        case "ok": return ok;
        case "timeout": return warn;
        case "crc_error": return error;
        case "illegal_addr": return error;
        case "slave_fault": return error;
        case "no_data": return idle;
        default: return idle;
        }
    }
}
