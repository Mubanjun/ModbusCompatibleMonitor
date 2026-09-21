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
                Text { text: "告警记录"; color: page.theme.text; font.pixelSize: 16; font.bold: true }
                StatusPill {
                    theme: page.theme
                    text: alarmModel.count + " 条"
                    statusColor: alarmModel.count > 0 ? page.theme.warn : page.theme.idle
                }
                Item { Layout.fillWidth: true }
                AppButton { theme: page.theme; text: "刷新"; onClicked: app.refreshAlarms() }
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
                anchors.margins: 10
                spacing: 4

                RowLayout {
                    Layout.fillWidth: true
                    Text { Layout.preferredWidth: 70;  text: "通道"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.preferredWidth: 110; text: "名称"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.preferredWidth: 70;  text: "类型"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.preferredWidth: 90;  text: "数值"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.preferredWidth: 90;  text: "阈值"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.preferredWidth: 150; text: "产生时间"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.preferredWidth: 150; text: "复归时间"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.fillWidth: true; text: "操作"; color: page.theme.textDim; font.pixelSize: 12 }
                }

                ListView {
                    id: alarmList
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    model: alarmModel
                    delegate: Rectangle {
                        width: alarmList.width
                        height: 30
                        color: index % 2 === 0 ? "transparent" : Qt.rgba(1, 1, 1, 0.03)
                        RowLayout {
                            anchors.fill: parent
                            Text { Layout.preferredWidth: 70;  text: channelNo; color: page.theme.text; font.pixelSize: 12 }
                            Text { Layout.preferredWidth: 110; text: name; color: page.theme.text; font.pixelSize: 12 }
                            Text {
                                Layout.preferredWidth: 70
                                text: alarmType
                                color: alarmType === "high" ? page.theme.error : page.theme.warn
                                font.pixelSize: 12
                                font.bold: true
                            }
                            Text { Layout.preferredWidth: 90;  text: Number(value).toFixed(2); color: page.theme.text; font.pixelSize: 12 }
                            Text { Layout.preferredWidth: 90;  text: Number(threshold).toFixed(2); color: page.theme.textDim; font.pixelSize: 12 }
                            Text { Layout.preferredWidth: 150; text: raisedAt; color: page.theme.textDim; font.pixelSize: 12 }
                            Text {
                                Layout.preferredWidth: 150
                                text: clearedAt !== "" ? clearedAt : (active ? "未复归" : "")
                                color: active ? page.theme.error : page.theme.ok
                                font.pixelSize: 12
                            }
                            AppButton {
                                Layout.fillWidth: true
                                theme: page.theme
                                text: "复归"
                                enabled: active
                                onClicked: app.clearAlarm(channelNo)
                            }
                        }
                    }
                }
            }
        }
    }
}
