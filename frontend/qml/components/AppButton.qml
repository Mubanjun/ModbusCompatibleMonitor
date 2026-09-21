import QtQuick
import QtQuick.Controls

Button {
    id: control
    property var theme
    property bool primary: false
    property bool danger: false

    implicitHeight: 32
    leftPadding: 14
    rightPadding: 14
    font.pixelSize: 13

    contentItem: Text {
        text: control.text
        color: {
            if (!control.enabled) return control.theme ? control.theme.textDim : "#8fa3bf";
            if (control.primary || control.danger) return "#08111f";
            return control.theme ? control.theme.text : "#e6edf7";
        }
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
        font: control.font
    }

    background: Rectangle {
        radius: control.theme ? control.theme.radius - 2 : 8
        color: {
            var t = control.theme;
            if (!t) return "#1b2537";
            if (control.danger) return control.down ? Qt.darker(t.error, 1.2) : t.error;
            if (control.primary) return control.down ? t.accentDim : t.accent;
            return control.down ? t.surfaceAlt : "transparent";
        }
        border.width: (control.primary || control.danger) ? 0 : 1
        border.color: control.theme ? control.theme.border : "#2a3548"
        opacity: control.enabled ? 1.0 : 0.5
    }
}
