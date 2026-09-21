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
                spacing: 16

                Text {
                    text: "实时看板"
                    color: page.theme.text
                    font.pixelSize: 16
                    font.bold: true
                }
                StatusPill {
                    theme: page.theme
                    text: app.linkStatus.connected ? "链路在线" : "链路离线"
                    statusColor: app.linkStatus.connected ? page.theme.ok : page.theme.error
                }
                Text {
                    text: "最近一轮 " + (app.lastRound.elapsed_ms !== undefined ? app.lastRound.elapsed_ms : "--")
                          + " ms｜成功 " + (app.lastRound.ok !== undefined ? app.lastRound.ok : "--")
                          + " / 失败 " + (app.lastRound.fail !== undefined ? app.lastRound.fail : "--")
                    color: page.theme.textDim
                    font.pixelSize: 12
                }
                Item { Layout.fillWidth: true }
                AppButton {
                    theme: page.theme
                    text: "立即采集"
                    primary: true
                    onClicked: app.pollNow()
                }
            }
        }

        GridView {
            id: grid
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            model: channelModel
            cellWidth: 214
            cellHeight: 132
            delegate: Item {
                width: grid.cellWidth
                height: grid.cellHeight
                ChannelCard {
                    anchors.centerIn: parent
                    theme: page.theme
                    alarmActive: false
                    info: ({
                        "channelNo": channelNo,
                        "name": name,
                        "unit": unit,
                        "value": value,
                        "raw": raw,
                        "quality": quality,
                        "rtt": rtt,
                        "updated": updated,
                        "hasValue": hasValue
                    })
                }
            }
        }
    }
}
