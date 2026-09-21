import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../components"

Item {
    id: page
    property var theme
    readonly property var forwardKeys: Object.keys(app.forwardStatus)

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 14
        spacing: 10

        Rectangle {
            Layout.fillWidth: true
            implicitHeight: 56
            radius: page.theme.radius
            color: page.theme.surface
            border.width: 1
            border.color: page.theme.border
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 14
                anchors.rightMargin: 14
                spacing: 12
                Text { text: "链路管理"; color: page.theme.text; font.pixelSize: 16; font.bold: true }
                StatusPill {
                    theme: page.theme
                    text: app.linkStatus.connected ? "已连接" : "未连接"
                    statusColor: app.linkStatus.connected ? page.theme.ok : page.theme.error
                }
                Text {
                    text: (app.linkStatus.profile !== undefined ? app.linkStatus.profile : "--")
                          + " ｜ " + (app.linkStatus.port !== undefined ? app.linkStatus.port : "--")
                          + " ｜ " + (app.linkStatus.baud !== undefined ? app.linkStatus.baud : "--") + " bps"
                          + " ｜ " + (app.linkStatus.protocol !== undefined ? app.linkStatus.protocol : "--")
                    color: page.theme.textDim
                    font.pixelSize: 12
                }
                Item { Layout.fillWidth: true }
                AppButton { theme: page.theme; text: "重载链路"; onClicked: app.reloadLink() }
                AppButton { theme: page.theme; text: "刷新"; onClicked: { app.refreshLink(); app.refreshHealth(); } }
            }
        }

        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 10

            // 左：链路档案
            Rectangle {
                Layout.preferredWidth: 430
                Layout.fillHeight: true
                radius: page.theme.radius
                color: page.theme.surface
                border.width: 1
                border.color: page.theme.border

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 8
                    Text { text: "链路档案（一键切换，FR-11）"; color: page.theme.text; font.pixelSize: 13; font.bold: true }

                    ListView {
                        id: linkList
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        clip: true
                        model: app.linkProfiles
                        spacing: 8
                        delegate: Rectangle {
                            width: linkList.width
                            height: 108
                            radius: 8
                            color: modelData.name === app.activeLink ? Qt.rgba(0.22, 0.74, 0.97, 0.12) : page.theme.surfaceAlt
                            border.width: 1
                            border.color: modelData.name === app.activeLink ? page.theme.accent : page.theme.border

                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 10
                                spacing: 4
                                RowLayout {
                                    Layout.fillWidth: true
                                    Text { text: modelData.name; color: page.theme.text; font.pixelSize: 14; font.bold: true }
                                    StatusPill {
                                        theme: page.theme
                                        text: modelData.kind
                                        statusColor: modelData.kind === "simulator" ? page.theme.warn : page.theme.accent
                                    }
                                    Item { Layout.fillWidth: true }
                                    AppButton {
                                        theme: page.theme
                                        text: modelData.name === app.activeLink ? "当前" : "激活"
                                        enabled: modelData.name !== app.activeLink
                                        primary: modelData.name !== app.activeLink
                                        onClicked: app.activateLink(modelData.name)
                                    }
                                }
                                Text {
                                    text: "串口 " + modelData.port + "　波特率 " + modelData.baud
                                          + "　规约 " + modelData.protocol
                                    color: page.theme.textDim; font.pixelSize: 12
                                }
                                Text {
                                    text: "RTS " + modelData.rts_mode + "　收帧 " + modelData.recv_mode
                                          + "　超时 " + modelData.response_timeout_ms + " ms　重试 " + modelData.retries
                                    color: page.theme.textDim; font.pixelSize: 12
                                }
                            }
                        }
                    }
                }
            }

            // 右：链路质量 + 调度 + 回传
            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 10

                Rectangle {
                    Layout.fillWidth: true
                    implicitHeight: 190
                    radius: page.theme.radius
                    color: page.theme.surface
                    border.width: 1
                    border.color: page.theme.border
                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 12
                        spacing: 6
                        Text { text: "链路质量统计"; color: page.theme.text; font.pixelSize: 13; font.bold: true }
                        GridLayout {
                            columns: 2
                            columnSpacing: 24
                            rowSpacing: 4
                            Text { text: "发送帧"; color: page.theme.textDim; font.pixelSize: 12 }
                            Text { text: app.linkStatus.tx_count !== undefined ? app.linkStatus.tx_count : 0; color: page.theme.text; font.pixelSize: 12 }
                            Text { text: "接收帧"; color: page.theme.textDim; font.pixelSize: 12 }
                            Text { text: app.linkStatus.rx_count !== undefined ? app.linkStatus.rx_count : 0; color: page.theme.text; font.pixelSize: 12 }
                            Text { text: "成功"; color: page.theme.textDim; font.pixelSize: 12 }
                            Text { text: app.linkStatus.ok_count !== undefined ? app.linkStatus.ok_count : 0; color: page.theme.ok; font.pixelSize: 12 }
                            Text { text: "超时"; color: page.theme.textDim; font.pixelSize: 12 }
                            Text { text: app.linkStatus.timeout_count !== undefined ? app.linkStatus.timeout_count : 0; color: page.theme.warn; font.pixelSize: 12 }
                            Text { text: "CRC 错误"; color: page.theme.textDim; font.pixelSize: 12 }
                            Text { text: app.linkStatus.crc_error_count !== undefined ? app.linkStatus.crc_error_count : 0; color: page.theme.error; font.pixelSize: 12 }
                            Text { text: "重试次数"; color: page.theme.textDim; font.pixelSize: 12 }
                            Text { text: app.linkStatus.retry_count !== undefined ? app.linkStatus.retry_count : 0; color: page.theme.text; font.pixelSize: 12 }
                            Text { text: "最近 RTT"; color: page.theme.textDim; font.pixelSize: 12 }
                            Text { text: (app.linkStatus.last_rtt_ms !== undefined ? app.linkStatus.last_rtt_ms : "--") + " ms"; color: page.theme.text; font.pixelSize: 12 }
                        }
                        Text {
                            Layout.fillWidth: true
                            text: "最近错误：" + (app.linkStatus.last_error !== undefined && app.linkStatus.last_error !== null ? app.linkStatus.last_error : "无")
                            color: (app.linkStatus.last_error !== undefined && app.linkStatus.last_error !== null) ? page.theme.error : page.theme.textDim
                            font.pixelSize: 12
                            wrapMode: Text.Wrap
                        }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    implicitHeight: 96
                    radius: page.theme.radius
                    color: page.theme.surface
                    border.width: 1
                    border.color: page.theme.border
                    RowLayout {
                        anchors.fill: parent
                        anchors.margins: 12
                        spacing: 12
                        Text { text: "轮询调度"; color: page.theme.text; font.pixelSize: 13; font.bold: true }
                        Switch {
                            checked: app.pollEnabled
                            onToggled: app.setPollEnabled(checked)
                        }
                        Text { text: "周期(ms)"; color: page.theme.textDim; font.pixelSize: 12 }
                        SpinBox {
                            id: intervalBox
                            from: 500; to: 600000; stepSize: 500
                            value: app.pollIntervalMs
                            onValueModified: app.setPollInterval(value)
                        }
                        AppButton { theme: page.theme; text: "立即采集"; primary: true; onClicked: app.pollNow() }
                        Item { Layout.fillWidth: true }
                        Text {
                            text: "设备 " + (app.health.version !== undefined ? app.health.version : "--")
                                  + "　行数 " + (app.health.rows !== undefined ? app.health.rows : "--")
                                  + "　发件箱 " + (app.health.outbox !== undefined ? app.health.outbox : "--")
                            color: page.theme.textDim; font.pixelSize: 12
                        }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    radius: page.theme.radius
                    color: page.theme.surface
                    border.width: 1
                    border.color: page.theme.border
                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 12
                        spacing: 6
                        Text { text: "数据库回传"; color: page.theme.text; font.pixelSize: 13; font.bold: true }
                        ListView {
                            id: fwdList
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            clip: true
                            model: page.forwardKeys
                            delegate: RowLayout {
                                width: fwdList.width
                                spacing: 10
                                property var st: app.forwardStatus[modelData] !== undefined ? app.forwardStatus[modelData] : ({})
                                Text { text: modelData; color: page.theme.text; font.pixelSize: 12; font.bold: true }
                                StatusPill {
                                    theme: page.theme
                                    text: st.connected ? "在线" : (st.enabled ? "异常" : "停用")
                                    statusColor: st.connected ? page.theme.ok : (st.enabled ? page.theme.error : page.theme.idle)
                                }
                                Text {
                                    text: "滞后 " + (st.pending !== undefined ? st.pending : 0)
                                          + "　成功 " + (st.sent_total !== undefined ? st.sent_total : 0)
                                          + "　失败 " + (st.failed_total !== undefined ? st.failed_total : 0)
                                    color: page.theme.textDim; font.pixelSize: 12
                                }
                                Item { Layout.fillWidth: true }
                                AppButton { theme: page.theme; text: "测试"; onClicked: app.testForward(modelData) }
                            }
                        }
                    }
                }
            }
        }
    }
}
