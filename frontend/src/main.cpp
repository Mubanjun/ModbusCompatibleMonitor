//! JDRK 水质监控平台 · Qt6 QML 前端入口。

#include <QGuiApplication>
#include <QQuickStyle>
#include <QFile>
#include <QDir>
#include <QDebug>
#include <QSettings>
#include <QStandardPaths>
#include <QCommandLineParser>
#include <QCommandLineOption>
#include <cstdio>

#include "app_controller.h"

// 把 Qt/QML 的日志同时写到 stderr 与文件，便于无控制台时定位启动错误。
// 日志文件路径在 QGuiApplication 构造后确定（用户数据目录，避免安装目录只读）。
static QString g_logPath;

static void jdrkMessageHandler(QtMsgType type, const QMessageLogContext &ctx, const QString &msg)
{
    Q_UNUSED(type);
    const char *cat = ctx.category ? ctx.category : "default";
    const QString line = QStringLiteral("[%1] %2\n").arg(QString::fromLatin1(cat), msg);
    const QByteArray utf8 = line.toUtf8();
    std::fwrite(utf8.constData(), 1, static_cast<size_t>(utf8.size()), stderr);

    QString path = g_logPath;
    if (path.isEmpty())
        path = QDir::currentPath() + QStringLiteral("/jdrk-ui.log");
    QFile f(path);
    if (f.open(QIODevice::Append | QIODevice::Text)) {
        f.write(utf8);
        f.close();
    }
}

#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QFont>

/// 日志目录：优先用户数据目录（Windows: %LOCALAPPDATA%\JDRK\...，Linux: ~/.local/share/...）
static QString resolveLogPath()
{
    QString dir = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);
    if (dir.isEmpty())
        dir = QDir::currentPath();
    QDir().mkpath(dir);
    return dir + QStringLiteral("/jdrk-ui.log");
}

int main(int argc, char *argv[])
{
    qInstallMessageHandler(jdrkMessageHandler);
    // 使用可定制的非原生样式，避免 Windows 原生样式无法自定义控件
    QQuickStyle::setStyle(QStringLiteral("Fusion"));
    QGuiApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("JDRK 水质监控平台"));
    app.setOrganizationName(QStringLiteral("JDRK"));
    app.setApplicationVersion(QStringLiteral(JDRK_UI_VERSION));

    g_logPath = resolveLogPath();

    // 后端地址来源优先级：命令行 --server > 环境变量 JDRK_SERVER > 上次保存 > 内置默认
    QCommandLineParser parser;
    parser.setApplicationDescription(QStringLiteral("JDRK 水质监控平台上位机（REST + WebSocket 客户端）"));
    parser.addHelpOption();
    parser.addVersionOption();
    QCommandLineOption serverOpt(
        QStringList() << QStringLiteral("s") << QStringLiteral("server"),
        QStringLiteral("后端服务地址，例如 http://192.168.1.10:8790"),
        QStringLiteral("url"));
    parser.addOption(serverOpt);
    parser.process(app);

    QString serverUrl = parser.value(serverOpt);
    if (serverUrl.isEmpty())
        serverUrl = QString::fromLocal8Bit(qgetenv("JDRK_SERVER"));
    if (serverUrl.isEmpty()) {
        QSettings settings;
        serverUrl = settings.value(QStringLiteral("server/baseUrl")).toString();
    }
    if (!serverUrl.isEmpty())
        qInfo("[jdrk] 后端地址: %s", qPrintable(serverUrl));

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

    controller.start(serverUrl);
    return app.exec();
}
