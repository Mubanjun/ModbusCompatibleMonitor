import QtQuick

Rectangle {
    id: pill
    property var theme
    property string text: ""
    property color statusColor: theme ? theme.idle : "#64748b"
    property bool filled: true

    implicitWidth: label.implicitWidth + 18
    implicitHeight: 22
    radius: 11
    color: filled ? Qt.rgba(statusColor.r, statusColor.g, statusColor.b, 0.18) : "transparent"
    border.width: 1
    border.color: Qt.rgba(statusColor.r, statusColor.g, statusColor.b, 0.65)

    Text {
        id: label
        anchors.centerIn: parent
        text: pill.text
        color: pill.statusColor
        font.pixelSize: 11
        font.bold: true
        elide: Text.ElideRight
    }
}
