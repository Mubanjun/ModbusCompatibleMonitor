//! JDRK 水质监控平台 · Qt6 QML 前端入口。

#include <QGuiApplication>
#include <QQuickStyle>
#include <QFile>
#include <QDir>
#include <QDebug>
#include <cstdio>

// 把 Qt/QML 的日志同时写到 stderr 与文件，便于无控制台时定位启动错误
static void jdrkMessageHandler(QtMsgType type, const QMessageLogContext &ctx, const QString &msg)
{
    Q_UNUSED(type);
    const char *cat = ctx.category ? ctx.category : "default";
    const QString line = QStringLiteral("[%1] %2\n").arg(QString::fromLatin1(cat), msg);
    const QByteArray utf8 = line.toUtf8();
    std::fwrite(utf8.constData(), 1, static_cast<size_t>(utf8.size()), stderr);
    QFile f(QDir::currentPath() + QStringLiteral("/jdrk-ui.log"));
    if (f.open(QIODevice::Append | QIODevice::Text)) {
        f.write(utf8);
        f.close();
    }
}
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QFont>

#include "app_controller.h"

int main(int argc, char *argv[])
{
    qInstallMessageHandler(jdrkMessageHandler);
    // 使用可定制的非原生样式，避免 Windows 原生样式无法自定义控件
    QQuickStyle::setStyle(QStringLiteral("Fusion"));
    QGuiApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("JDRK 水质监控平台"));
    app.setOrganizationName(QStringLiteral("JDRK"));

    // 中文界面默认使用微软雅黑 / 系统字体
    QFont f = app.font();
    f.setPointSizeF(f.pointSizeF());
    app.setFont(f);

    AppController controller;

    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty(QStringLiteral("app"), &controller);
    engine.rootContext()->setContextProperty(QStringLiteral("channelModel"), controller.channelModel());
    engine.rootContext()->setContextProperty(QStringLiteral("alarmModel"), controller.alarmModel());
    engine.rootContext()->setContextProperty(QStringLiteral("frameModel"), controller.frameModel());

    const QUrl url(QStringLiteral("qrc:/qml/Main.qml"));
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreated, &app,
                     [url](QObject *obj, const QUrl &objUrl) {
                         if (!obj && url == objUrl)
                             QCoreApplication::exit(-1);
                     }, Qt::QueuedConnection);
    engine.load(url);

    if (engine.rootObjects().isEmpty())
        return -1;

    controller.start();
    return app.exec();
}
