import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "components"
import "pages"

ApplicationWindow {
    id: window
    width: 1460
    height: 920
    minimumWidth: 1100
    minimumHeight: 720
    visible: true
    title: "JDRK 水质监控平台 · 上位机"
    color: theme.bg

    Theme { id: theme }

    property var navItems: [
        { "title": "实时看板", "page": 0 },
        { "title": "历史曲线", "page": 1 },
        { "title": "告警记录", "page": 2 },
        { "title": "通道配置", "page": 3 },
        { "title": "链路管理", "page": 4 },
        { "title": "报文调试台", "page": 5 }
    ]
    property int currentPage: 0
    property string clock: ""

    Timer {
        interval: 1000
        running: true
        repeat: true
        onTriggered: window.clock = Qt.formatDateTime(new Date(), "yyyy-MM-dd HH:mm:ss")
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ---------------- 侧边导航 ----------------
        Rectangle {
            Layout.preferredWidth: 196
            Layout.fillHeight: true
            color: theme.surface

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 6

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8
                    Rectangle {
                        width: 30; height: 30; radius: 8
                        color: theme.accent
                        Text {
                            anchors.centerIn: parent
                            text: "J"
                            color: "#08111f"
                            font.pixelSize: 18
                            font.bold: true
                        }
                    }
                    ColumnLayout {
                        spacing: 0
                        Text { text: "JDRK 监控"; color: theme.text; font.pixelSize: 14; font.bold: true }
                        Text { text: "水质在线监测"; color: theme.textDim; font.pixelSize: 10 }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    height: 1
                    color: theme.border
                }

                Repeater {
                    model: window.navItems
                    delegate: Rectangle {
                        Layout.fillWidth: true
                        height: 38
                        radius: 8
                        color: window.currentPage === modelData.page ? Qt.rgba(0.22, 0.74, 0.97, 0.16) : "transparent"
                        border.width: window.currentPage === modelData.page ? 1 : 0
                        border.color: theme.accent

                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.left: parent.left
                            anchors.leftMargin: 14
                            text: modelData.title
                            color: window.currentPage === modelData.page ? theme.accent : theme.text
                            font.pixelSize: 13
                            font.bold: window.currentPage === modelData.page
                        }

                        MouseArea {
                            anchors.fill: parent
                            cursorShape: Qt.PointingHandCursor
                            onClicked: window.currentPage = modelData.page
                        }
                    }
                }

                Item { Layout.fillHeight: true }

                Rectangle {
                    Layout.fillWidth: true
                    height: 1
                    color: theme.border
                }
                Text {
                    text: "后端 " + (app.serverVersion !== "" ? app.serverVersion : "--")
                    color: theme.textDim
                    font.pixelSize: 11
                }
                Text {
                    text: app.baseUrl
                    color: theme.textDim
                    font.pixelSize: 10
                    elide: Text.ElideMiddle
                    Layout.fillWidth: true
                }
            }
        }

        // ---------------- 主区域 ----------------
        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            // 顶部状态栏
            Rectangle {
                Layout.fillWidth: true
                implicitHeight: 48
                color: theme.surface

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 16
                    anchors.rightMargin: 16
                    spacing: 12

                    StatusPill {
                        theme: theme
                        text: app.connected ? "WS 已连接" : "WS 断开"
                        statusColor: app.connected ? theme.ok : theme.error
                    }
                    StatusPill {
                        theme: theme
                        text: app.linkStatus.connected ? "链路在线" : "链路离线"
                        statusColor: app.linkStatus.connected ? theme.ok : theme.error
                    }
                    Text {
                        text: "档案 " + (app.activeLink !== "" ? app.activeLink : "--")
                              + "｜" + (app.linkStatus.port !== undefined ? app.linkStatus.port : "--")
                              + "｜" + (app.linkStatus.baud !== undefined ? app.linkStatus.baud : "--") + " bps"
                              + "｜" + (app.pollEnabled ? "轮询中" : "已暂停") + " " + app.pollIntervalMs + " ms"
                        color: theme.textDim
                        font.pixelSize: 12
                    }
                    Item { Layout.fillWidth: true }
                    Text {
                        text: "行数 " + (app.health.rows !== undefined ? app.health.rows : "--")
                              + "　发件箱 " + (app.health.outbox !== undefined ? app.health.outbox : "--")
                        color: theme.textDim
                        font.pixelSize: 12
                    }
                    Text { text: window.clock; color: theme.text; font.pixelSize: 12; font.family: "Consolas" }
                }
            }

            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: window.currentPage

                DashboardPage { theme: theme }
                HistoryPage { theme: theme }
                AlarmPage { theme: theme }
                ChannelConfigPage { theme: theme }
                LinkPage { theme: theme }
                DebugPage { theme: theme }
            }

            // 底部消息条
            Rectangle {
                Layout.fillWidth: true
                implicitHeight: 30
                color: theme.surface
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 16
                    anchors.rightMargin: 16
                    Text { text: app.message !== "" ? app.message : "就绪"; color: theme.textDim; font.pixelSize: 11 }
                    Item { Layout.fillWidth: true }
                    Text {
                        text: "Qt6 QML 前端 ⇄ Rust 后端 (REST + WebSocket)"
                        color: theme.textDim
                        font.pixelSize: 10
                    }
                }
            }
        }
    }
}
