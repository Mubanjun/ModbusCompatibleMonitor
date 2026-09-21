import QtQuick

Rectangle {
    id: card
    property var theme
    property var info: ({})
    property bool alarmActive: false

    width: 208
    height: 118
    radius: theme ? theme.radius : 10
    color: theme ? theme.surface : "#151d2e"
    border.width: alarmActive ? 2 : 1
    border.color: alarmActive
                  ? (theme ? theme.error : "#ef4444")
                  : (theme ? theme.border : "#2a3548")

    Column {
        anchors.fill: parent
        anchors.margins: 12
        spacing: 6

        Row {
            width: parent.width
            spacing: 6
            Text {
                width: parent.width - statusPill.width - 6
                text: card.info.name !== undefined ? card.info.name : ("通道" + card.info.channelNo)
                color: card.theme ? card.theme.text : "#e6edf7"
                font.pixelSize: 14
                font.bold: true
                elide: Text.ElideRight
            }
            StatusPill {
                id: statusPill
                theme: card.theme
                text: card.info.quality !== undefined ? card.info.quality : "-"
                statusColor: card.theme ? card.theme.qualityColor(card.info.quality) : "#64748b"
            }
        }

        Row {
            spacing: 4
            Text {
                text: card.info.hasValue ? Number(card.info.value).toFixed(2) : "--"
                color: card.info.hasValue
                       ? (card.theme ? card.theme.accent : "#38bdf8")
                       : (card.theme ? card.theme.textDim : "#8fa3bf")
                font.pixelSize: 28
                font.bold: true
                font.family: "Consolas"
            }
            Text {
                anchors.baseline: parent.children[0].baseline
                text: card.info.unit !== undefined ? card.info.unit : ""
                color: card.theme ? card.theme.textDim : "#8fa3bf"
                font.pixelSize: 13
            }
        }

        Row {
            spacing: 10
            Text {
                text: "原始 " + (card.info.raw !== undefined ? card.info.raw : "--")
                color: card.theme ? card.theme.textDim : "#8fa3bf"
                font.pixelSize: 11
            }
            Text {
                text: (card.info.rtt !== undefined ? card.info.rtt : 0) + " ms"
                color: card.theme ? card.theme.textDim : "#8fa3bf"
                font.pixelSize: 11
            }
        }

        Text {
            text: "更新 " + (card.info.updated !== undefined && card.info.updated !== "" ? card.info.updated : "--")
            color: card.theme ? card.theme.textDim : "#8fa3bf"
            font.pixelSize: 11
        }
    }
}
