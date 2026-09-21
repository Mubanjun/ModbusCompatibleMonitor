QT += core gui qml quick network quickcontrols2
CONFIG += c++17
TEMPLATE = app
TARGET = jdrk-monitor-ui

DEFINES += QT_DEPRECATED_WARNINGS

# 前端版本号（与后端保持同一主版本，安装包脚本也可覆盖）
isEmpty(JDRK_UI_VERSION): JDRK_UI_VERSION = 0.1.0
DEFINES += JDRK_UI_VERSION=\\\"$$JDRK_UI_VERSION\\\"

INCLUDEPATH += src

SOURCES += \
    src/main.cpp \
    src/rest_client.cpp \
    src/ws_client.cpp \
    src/channel_model.cpp \
    src/alarm_model.cpp \
    src/frame_model.cpp \
    src/app_controller.cpp

HEADERS += \
    src/rest_client.h \
    src/ws_client.h \
    src/channel_model.h \
    src/alarm_model.h \
    src/frame_model.h \
    src/app_controller.h

RESOURCES += qml.qrc

# Windows 下发布时用 windeployqt 拷贝依赖
win32 {
    QMAKE_TARGET_PRODUCT = "JDRK Water Monitor"
    QMAKE_TARGET_DESCRIPTION = "JDRK 水质监控平台前端"
}
