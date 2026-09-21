import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../components"

Item {
    id: page
    property var theme

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
                Text { text: "报文调试台"; color: page.theme.text; font.pixelSize: 16; font.bold: true }
                Text { text: "原始 TX/RX、耗时、错误码实时可见（FR-08）"; color: page.theme.textDim; font.pixelSize: 12 }
                Item { Layout.fillWidth: true }
                AppButton { theme: page.theme; text: "清空"; onClicked: app.clearFrames() }
                AppButton { theme: page.theme; text: "刷新"; onClicked: app.refreshFrames() }
            }
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 10

            // 手工发帧
            Rectangle {
                Layout.fillWidth: true
                implicitHeight: 118
                radius: page.theme.radius
                color: page.theme.surface
                border.width: 1
                border.color: page.theme.border
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 6
                    Text { text: "手工发帧"; color: page.theme.text; font.pixelSize: 12; font.bold: true }
                    TextField {
                        id: rawHex
                        Layout.fillWidth: true
                        text: "01 03 00 00 00 02 C4 0B"
                        font.family: "Consolas"
                        font.pixelSize: 12
                    }
                    RowLayout {
                        spacing: 6
                        Text { text: "从站"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: rawAddr; Layout.preferredWidth: 44; text: "1"; font.pixelSize: 12 }
                        Text { text: "预期长度"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: rawLen; Layout.preferredWidth: 48; text: "9"; font.pixelSize: 12 }
                        Item { Layout.fillWidth: true }
                        AppButton {
                            theme: page.theme; text: "发送"; primary: true
                            onClicked: app.sendRaw(rawHex.text, Number(rawAddr.text), Number(rawLen.text))
                        }
                    }
                }
            }

            // 地址扫描
            Rectangle {
                Layout.fillWidth: true
                implicitHeight: 118
                radius: page.theme.radius
                color: page.theme.surface
                border.width: 1
                border.color: page.theme.border
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 6
                    Text { text: "地址扫描"; color: page.theme.text; font.pixelSize: 12; font.bold: true }
                    RowLayout {
                        spacing: 6
                        Text { text: "起"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: scanFrom; Layout.preferredWidth: 44; text: "1"; font.pixelSize: 12 }
                        Text { text: "止"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: scanTo; Layout.preferredWidth: 44; text: "32"; font.pixelSize: 12 }
                        Text { text: "起始寄存器"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: scanStart; Layout.preferredWidth: 50; text: "0"; font.pixelSize: 12 }
                        Text { text: "数量"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: scanCount; Layout.preferredWidth: 44; text: "2"; font.pixelSize: 12 }
                    }
                    RowLayout {
                        Item { Layout.fillWidth: true }
                        AppButton {
                            theme: page.theme; text: "扫描"; primary: true
                            onClicked: app.scan(Number(scanFrom.text), Number(scanTo.text), Number(scanStart.text), Number(scanCount.text))
                        }
                    }
                    Text {
                        text: "在线 " + app.scanResults.length + " 个（结果见下方详情）"
                        color: page.theme.textDim; font.pixelSize: 11
                    }
                }
            }

            // 读 / 写寄存器
            Rectangle {
                Layout.fillWidth: true
                implicitHeight: 118
                radius: page.theme.radius
                color: page.theme.surface
                border.width: 1
                border.color: page.theme.border
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 6
                    Text { text: "读 / 写寄存器"; color: page.theme.text; font.pixelSize: 12; font.bold: true }
                    RowLayout {
                        spacing: 6
                        Text { text: "从站"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: regAddr; Layout.preferredWidth: 44; text: "1"; font.pixelSize: 12 }
                        Text { text: "起始"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: regStart; Layout.preferredWidth: 50; text: "0"; font.pixelSize: 12 }
                        Text { text: "数量"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: regCount; Layout.preferredWidth: 44; text: "2"; font.pixelSize: 12 }
                        AppButton {
                            theme: page.theme; text: "读"
                            onClicked: app.readRegs(Number(regAddr.text), Number(regStart.text), Number(regCount.text))
                        }
                    }
                    RowLayout {
                        spacing: 6
                        Text { text: "写寄存器"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: wrAddr; Layout.preferredWidth: 44; text: "1"; font.pixelSize: 12 }
                        Text { text: "寄存器"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: wrReg; Layout.preferredWidth: 60; text: "690"; font.pixelSize: 12 }
                        Text { text: "值"; color: page.theme.textDim; font.pixelSize: 11 }
                        TextField { id: wrVal; Layout.preferredWidth: 60; text: "0"; font.pixelSize: 12 }
                        AppButton {
                            theme: page.theme; text: "写"
                            onClicked: app.writeReg(Number(wrAddr.text), Number(wrReg.text), Number(wrVal.text))
                        }
                    }
                }
            }
        }

        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 10

            // 帧日志
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                radius: page.theme.radius
                color: page.theme.surface
                border.width: 1
                border.color: page.theme.border
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 4
                    RowLayout {
                        Layout.fillWidth: true
                        Text { text: "报文日志"; color: page.theme.text; font.pixelSize: 13; font.bold: true }
                        Text { text: frameModel.count + " 帧"; color: page.theme.textDim; font.pixelSize: 11 }
                    }
                    ListView {
                        id: frameList
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        clip: true
                        model: frameModel
                        delegate: RowLayout {
                            width: frameList.width
                            spacing: 8
                            Text { Layout.preferredWidth: 78; text: time; color: page.theme.textDim; font.pixelSize: 11; font.family: "Consolas" }
                            Text {
                                Layout.preferredWidth: 34
                                text: direction
                                color: direction === "tx" ? page.theme.accent : page.theme.ok
                                font.pixelSize: 11
                                font.bold: true
                            }
                            Text {
                                Layout.fillWidth: true
                                text: bytes
                                color: page.theme.text
                                font.pixelSize: 11
                                font.family: "Consolas"
                                elide: Text.ElideRight
                            }
                            Text {
                                Layout.preferredWidth: 150
                                text: (rtt >= 0 ? rtt + "ms " : "") + (quality !== undefined && quality !== "" ? quality : "") + (note !== undefined && note !== "" ? " " + note : "")
                                color: page.theme.textDim
                                font.pixelSize: 11
                                elide: Text.ElideRight
                            }
                        }
                    }
                }
            }

            // 调试结果 / 扫描结果
            Rectangle {
                Layout.preferredWidth: 420
                Layout.fillHeight: true
                radius: page.theme.radius
                color: page.theme.surface
                border.width: 1
                border.color: page.theme.border
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 4
                    Text { text: "调试结果"; color: page.theme.text; font.pixelSize: 13; font.bold: true }
                    ListView {
                        id: scanList
                        Layout.fillWidth: true
                        Layout.preferredHeight: 120
                        clip: true
                        model: app.scanResults
                        delegate: Text {
                            width: scanList.width
                            text: "从站 " + modelData.addr + "  " + (modelData.online ? "在线 " + JSON.stringify(modelData.regs) : "离线 " + modelData.quality)
                            color: modelData.online ? page.theme.ok : page.theme.textDim
                            font.pixelSize: 11
                            font.family: "Consolas"
                        }
                    }
                    ScrollView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        TextArea {
                            readOnly: true
                            text: JSON.stringify(app.lastDebug, null, 2)
                            color: page.theme.text
                            font.family: "Consolas"
                            font.pixelSize: 11
                            wrapMode: TextEdit.WrapAnywhere
                        }
                    }
                }
            }
        }
    }
}
