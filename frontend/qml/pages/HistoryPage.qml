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
                spacing: 10

                Text { text: "通道"; color: page.theme.textDim; font.pixelSize: 12 }
                ComboBox {
                    id: channelBox
                    implicitWidth: 120
                    model: { var a = []; for (var i = 1; i <= 20; ++i) a.push(i); return a; }
                    currentIndex: 0
                }
                Text { text: "条数"; color: page.theme.textDim; font.pixelSize: 12 }
                ComboBox {
                    id: limitBox
                    implicitWidth: 110
                    model: [100, 300, 1000, 3000]
                    currentIndex: 1
                }
                AppButton {
                    theme: page.theme
                    text: "查询"
                    primary: true
                    onClicked: app.loadHistory(channelBox.currentText, "", "", limitBox.currentText)
                }
                Item { Layout.fillWidth: true }
                Text {
                    text: app.historyPoints.length + " 点"
                    color: page.theme.textDim
                    font.pixelSize: 12
                }
            }
        }

        LineChart {
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.minimumHeight: 240
            theme: page.theme
            points: app.historyPoints
            lineColor: page.theme.accent
            emptyText: "选择通道后点击查询"
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 220
            radius: page.theme.radius
            color: page.theme.surface
            border.width: 1
            border.color: page.theme.border

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 8
                spacing: 4
                RowLayout {
                    Layout.fillWidth: true
                    Text { Layout.preferredWidth: 150; text: "时间"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.preferredWidth: 100; text: "数值"; color: page.theme.textDim; font.pixelSize: 12 }
                    Text { Layout.fillWidth: true; text: "质量"; color: page.theme.textDim; font.pixelSize: 12 }
                }
                ListView {
                    id: historyList
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    model: app.historyPoints
                    delegate: RowLayout {
                        width: historyList.width
                        Text { Layout.preferredWidth: 150; text: modelData.time; color: page.theme.text; font.pixelSize: 12 }
                        Text {
                            Layout.preferredWidth: 100
                            text: modelData.hasValue ? Number(modelData.v).toFixed(3) : "--"
                            color: page.theme.accent
                            font.pixelSize: 12
                            font.family: "Consolas"
                        }
                        Text { Layout.fillWidth: true; text: modelData.quality; color: page.theme.qualityColor(modelData.quality); font.pixelSize: 12 }
                    }
                }
            }
        }
    }
}
