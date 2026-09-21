import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../components"

Item {
    id: page
    property var theme
    readonly property var typeOptions: ["ai1", "ai2", "ai1_ai2", "u32", "i32", "f32", "switch"]

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
                Text { text: "通道配置"; color: page.theme.text; font.pixelSize: 16; font.bold: true }
                Text { text: "共 " + app.channelsConfig.length + " 路；修改后点右侧保存（FR-07）"; color: page.theme.textDim; font.pixelSize: 12 }
                Item { Layout.fillWidth: true }
                AppButton { theme: page.theme; text: "刷新"; onClicked: app.refreshChannels() }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            implicitHeight: 34
            color: "transparent"
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 10
                anchors.rightMargin: 10
                Text { Layout.preferredWidth: 46;  text: "通道"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 130; text: "名称"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 80;  text: "单位"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 110; text: "型号"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 100; text: "类型"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 70;  text: "系数A"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 70;  text: "系数B"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 50;  text: "小数"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 80;  text: "上限"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 80;  text: "下限"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 50;  text: "地址"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.preferredWidth: 60;  text: "启用"; color: page.theme.textDim; font.pixelSize: 11 }
                Text { Layout.fillWidth: true;     text: "操作"; color: page.theme.textDim; font.pixelSize: 11 }
            }
        }

        ListView {
            id: configList
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            model: app.channelsConfig
            spacing: 2
            delegate: Rectangle {
                id: rowItem
                width: configList.width
                height: 38
                radius: 6
                color: index % 2 === 0 ? page.theme.surface : page.theme.surfaceAlt
                property var row: modelData
                property string fName: row.name !== undefined ? row.name : ""
                property string fUnit: row.unit !== undefined ? row.unit : ""
                property string fModel: row.sensor_model !== undefined ? row.sensor_model : ""
                property int fType: {
                    var idx = page.typeOptions.indexOf(row.data_type);
                    return idx < 0 ? 0 : idx;
                }

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 10
                    anchors.rightMargin: 10
                    spacing: 4

                    Text { Layout.preferredWidth: 46; text: row.no; color: page.theme.text; font.pixelSize: 12; font.bold: true }

                    TextField {
                        Layout.preferredWidth: 130; implicitHeight: 26
                        text: rowItem.fName
                        onTextEdited: rowItem.fName = text
                        font.pixelSize: 12
                    }
                    TextField {
                        Layout.preferredWidth: 80; implicitHeight: 26
                        text: rowItem.fUnit
                        onTextEdited: rowItem.fUnit = text
                        font.pixelSize: 12
                    }
                    TextField {
                        Layout.preferredWidth: 110; implicitHeight: 26
                        text: rowItem.fModel
                        onTextEdited: rowItem.fModel = text
                        font.pixelSize: 12
                    }
                    ComboBox {
                        id: typeCombo
                        Layout.preferredWidth: 100; implicitHeight: 26
                        model: page.typeOptions
                        currentIndex: rowItem.fType
                        font.pixelSize: 12
                    }
                    TextField {
                        id: coefAField
                        Layout.preferredWidth: 70; implicitHeight: 26
                        text: row.coef_a !== undefined ? row.coef_a : "1"
                        font.pixelSize: 12
                    }
                    TextField {
                        id: coefBField
                        Layout.preferredWidth: 70; implicitHeight: 26
                        text: row.coef_b !== undefined ? row.coef_b : "0"
                        font.pixelSize: 12
                    }
                    TextField {
                        id: decField
                        Layout.preferredWidth: 50; implicitHeight: 26
                        text: row.decimals !== undefined ? row.decimals : "2"
                        font.pixelSize: 12
                    }
                    TextField {
                        id: upField
                        Layout.preferredWidth: 80; implicitHeight: 26
                        text: row.upper_limit !== undefined && row.upper_limit !== null ? row.upper_limit : ""
                        placeholderText: "空=无"
                        font.pixelSize: 12
                    }
                    TextField {
                        id: lowField
                        Layout.preferredWidth: 80; implicitHeight: 26
                        text: row.lower_limit !== undefined && row.lower_limit !== null ? row.lower_limit : ""
                        placeholderText: "空=无"
                        font.pixelSize: 12
                    }
                    TextField {
                        id: addrField
                        Layout.preferredWidth: 50; implicitHeight: 26
                        text: row.slave_addr !== undefined ? row.slave_addr : row.no
                        font.pixelSize: 12
                    }
                    Switch {
                        id: enabledSwitch
                        Layout.preferredWidth: 60
                        checked: row.enabled === undefined ? true : row.enabled
                        scale: 0.8
                    }
                    AppButton {
                        Layout.fillWidth: true
                        theme: page.theme
                        text: "保存"
                        primary: true
                        onClicked: {
                            var patch = {
                                "name": rowItem.fName,
                                "unit": rowItem.fUnit,
                                "sensor_model": rowItem.fModel,
                                "data_type": page.typeOptions[typeCombo.currentIndex],
                                "coef_a": Number(coefAField.text),
                                "coef_b": Number(coefBField.text),
                                "decimals": Number(decField.text),
                                "slave_addr": Number(addrField.text),
                                "enabled": enabledSwitch.checked
                            };
                            if (upField.text !== "") patch["upper_limit"] = Number(upField.text);
                            if (lowField.text !== "") patch["lower_limit"] = Number(lowField.text);
                            app.saveChannel(row.no, patch);
                        }
                    }
                }
            }
        }
    }
}
