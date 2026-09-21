#include "app_controller.h"

#include <QDateTime>
#include <QJsonObject>
#include <QSettings>
#include <QTimer>
#include <QUrl>

AppController::AppController(QObject *parent)
    : QObject(parent)
{
    m_channels.ensureChannels(20);
}

void AppController::start(const QString &serverUrl)
{
    if (!serverUrl.isEmpty())
        m_rest.setBaseUrl(serverUrl);

    connect(&m_rest, &RestClient::finished, this, &AppController::onRest);
    connect(&m_ws, &WsClient::textMessage, this, &AppController::onWsText);
    connect(&m_ws, &WsClient::opened, this, [this]() {
        m_connected = true;
        emit connectedChanged();
        setMessage(QStringLiteral("WebSocket 已连接"));
        m_ws.sendText(QStringLiteral("{\"action\":\"status\"}"));
    });
    connect(&m_ws, &WsClient::closed, this, [this]() {
        m_connected = false;
        emit connectedChanged();
        setMessage(QStringLiteral("WebSocket 已断开，正在重连…"));
    });
    connect(&m_ws, &WsClient::failed, this, [this](const QString &e) {
        m_connected = false;
        emit connectedChanged();
        setMessage(e);
    });

    refreshAll();

    auto *healthTimer = new QTimer(this);
    healthTimer->setInterval(5000);
    connect(healthTimer, &QTimer::timeout, this, [this]() { refreshHealth(); });
    healthTimer->start();

    QString wsUrl = m_rest.baseUrl();
    wsUrl.replace(QStringLiteral("http://"), QStringLiteral("ws://"));
    wsUrl.replace(QStringLiteral("https://"), QStringLiteral("wss://"));
    wsUrl += QStringLiteral("/api/ws");
    m_ws.open(QUrl(wsUrl));
}

void AppController::setBaseUrl(const QString &url)
{
    if (m_rest.baseUrl() == url)
        return;
    m_rest.setBaseUrl(url);
    // 记住所选后端地址，下次启动直接使用（Windows 注册表 / Linux ~/.config）
    QSettings settings;
    settings.setValue(QStringLiteral("server/baseUrl"), url);
    emit baseUrlChanged();
    m_ws.close();
    QString wsUrl = url;
    wsUrl.replace(QStringLiteral("http://"), QStringLiteral("ws://"));
    wsUrl.replace(QStringLiteral("https://"), QStringLiteral("wss://"));
    wsUrl += QStringLiteral("/api/ws");
    m_ws.open(QUrl(wsUrl));
    refreshAll();
}

void AppController::refreshAll()
{
    refreshHealth();
    refreshChannels();
    refreshLink();
    refreshAlarms();
    refreshFrames();
    m_rest.get(QStringLiteral("/api/config"));
    m_rest.get(QStringLiteral("/api/links"));
    m_rest.get(QStringLiteral("/api/forward/status"));
    m_rest.get(QStringLiteral("/api/samples/latest?per_channel=1"));
}

void AppController::refreshHealth()   { m_rest.get(QStringLiteral("/api/health")); }
void AppController::refreshChannels() { m_rest.get(QStringLiteral("/api/channels")); }
void AppController::refreshLink()     { m_rest.get(QStringLiteral("/api/link/status")); }
void AppController::refreshAlarms()   { m_rest.get(QStringLiteral("/api/alarms?limit=200")); }
void AppController::refreshFrames()   { m_rest.get(QStringLiteral("/api/debug/frames?limit=500")); }

void AppController::setMessage(const QString &msg)
{
    m_message = msg;
    emit messageChanged();
}

void AppController::setLinkStatus(const QVariantMap &m)
{
    m_linkStatus = m;
    emit linkStatusChanged();
}

void AppController::applyConfig(const QJsonObject &cfg)
{
    const QJsonObject poll = cfg.value(QStringLiteral("poll")).toObject();
    m_pollEnabled = poll.value(QStringLiteral("enabled")).toBool(m_pollEnabled);
    const int interval = poll.value(QStringLiteral("interval_ms")).toInt(m_pollIntervalMs);
    if (interval > 0)
        m_pollIntervalMs = interval;
    emit pollChanged();
}

void AppController::onRest(const QString &path, const QJsonDocument &doc, bool ok, const QString &error)
{
    const QString bare = path.section(QLatin1Char('?'), 0, 0);
    if (!ok) {
        setMessage(QStringLiteral("请求失败 %1：%2").arg(bare, error));
        m_lastDebug = QVariantMap{ { QStringLiteral("ok"), false },
                                   { QStringLiteral("path"), bare },
                                   { QStringLiteral("error"), error } };
        emit lastDebugChanged();
        return;
    }

    if (bare == QLatin1String("/api/config")) {
        applyConfig(doc.object());
    } else if (bare == QLatin1String("/api/health")) {
        m_health = doc.object().toVariantMap();
        emit healthChanged();
    } else if (bare == QLatin1String("/api/link/status")) {
        setLinkStatus(doc.object().toVariantMap());
    } else if (bare == QLatin1String("/api/channels")) {
        const QJsonArray arr = doc.array();
        m_channelsConfig = arr.toVariantList();
        m_channels.applyConfig(arr);
        emit channelsConfigChanged();
    } else if (bare == QLatin1String("/api/links")) {
        m_linkProfiles = doc.array().toVariantList();
        emit linkProfilesChanged();
    } else if (bare == QLatin1String("/api/samples/latest")) {
        for (const QJsonValue &v : doc.array())
            m_channels.applySample(v.toObject());
    } else if (bare == QLatin1String("/api/samples")) {
        QVariantList points;
        const QJsonArray arr = doc.array();
        for (const QJsonValue &v : arr) {
            const QJsonObject o = v.toObject();
            const QDateTime dt = QDateTime::fromString(o.value(QStringLiteral("record_time")).toString(), Qt::ISODateWithMs);
            QVariantMap p;
            p[QStringLiteral("ts")] = static_cast<qint64>(dt.toMSecsSinceEpoch());
            p[QStringLiteral("time")] = dt.toLocalTime().toString(QStringLiteral("MM-dd HH:mm:ss"));
            p[QStringLiteral("v")] = o.value(QStringLiteral("value")).toDouble();
            p[QStringLiteral("hasValue")] = o.value(QStringLiteral("value")).isDouble();
            p[QStringLiteral("quality")] = o.value(QStringLiteral("quality")).toString();
            p[QStringLiteral("channel")] = o.value(QStringLiteral("channel_no")).toInt();
            points.prepend(p);
        }
        m_historyPoints = points;
        emit historyChanged();
    } else if (bare == QLatin1String("/api/alarms")) {
        m_alarms.setAll(doc.array());
    } else if (bare == QLatin1String("/api/forward/status")) {
        m_forwardStatus = doc.object().toVariantMap();
        emit forwardStatusChanged();
    } else if (bare == QLatin1String("/api/debug/frames")) {
        m_frames.setAll(doc.array());
    } else if (bare == QLatin1String("/api/debug/scan")) {
        const QJsonObject o = doc.object();
        m_scanResults = o.value(QStringLiteral("results")).toArray().toVariantList();
        m_lastDebug = o.toVariantMap();
        emit scanResultsChanged();
        emit lastDebugChanged();
    } else if (bare == QLatin1String("/api/debug/raw")
               || bare == QLatin1String("/api/debug/read")
               || bare == QLatin1String("/api/debug/write")
               || bare == QLatin1String("/api/forward/test")
               || bare == QLatin1String("/api/poll/now")
               || bare == QLatin1String("/api/links/reload")) {
        m_lastDebug = doc.object().toVariantMap();
        emit lastDebugChanged();
    } else if (bare.startsWith(QLatin1String("/api/links/"))) {
        setMessage(QStringLiteral("链路已切换"));
        refreshLink();
    } else if (bare.startsWith(QLatin1String("/api/channels/"))) {
        setMessage(QStringLiteral("通道配置已保存"));
        refreshChannels();
    }
}

void AppController::onWsText(const QString &text)
{
    const QJsonObject o = QJsonDocument::fromJson(text.toUtf8()).object();
    const QString type = o.value(QStringLiteral("type")).toString();

    if (type == QLatin1String("sample")) {
        m_channels.applySample(o);
    } else if (type == QLatin1String("round")) {
        m_lastRound = o.toVariantMap();
        emit lastRoundChanged();
    } else if (type == QLatin1String("link_status")) {
        QVariantMap m = o.toVariantMap();
        m.remove(QStringLiteral("type"));
        setLinkStatus(m);
    } else if (type == QLatin1String("frame")) {
        m_frames.append(o);
    } else if (type == QLatin1String("alarm")) {
        m_alarms.append(o);
        setMessage(QStringLiteral("告警：通道 %1 %2")
                       .arg(o.value(QStringLiteral("channel_no")).toInt())
                       .arg(o.value(QStringLiteral("alarm_type")).toString()));
    } else if (type == QLatin1String("forward")) {
        QVariantMap m = m_forwardStatus;
        const QString target = o.value(QStringLiteral("target")).toString();
        QVariantMap sub = o.toVariantMap();
        sub.remove(QStringLiteral("type"));
        m[target] = sub;
        m_forwardStatus = m;
        emit forwardStatusChanged();
    } else if (type == QLatin1String("hello")) {
        m_health[QStringLiteral("version")] = o.value(QStringLiteral("version"));
        emit healthChanged();
    } else if (type == QLatin1String("notice")) {
        setMessage(o.value(QStringLiteral("message")).toString());
    } else if (type == QLatin1String("status")) {
        if (o.contains(QStringLiteral("link")))
            setLinkStatus(o.value(QStringLiteral("link")).toObject().toVariantMap());
    } else if (type == QLatin1String("snapshot")) {
        for (const QJsonValue &v : o.value(QStringLiteral("samples")).toArray())
            m_channels.applySample(v.toObject());
    }
}

void AppController::saveChannel(int no, const QVariantMap &patch)
{
    m_rest.put(QStringLiteral("/api/channels/%1").arg(no), QJsonObject::fromVariantMap(patch));
}

void AppController::activateLink(const QString &name)
{
    m_rest.post(QStringLiteral("/api/links/%1/activate").arg(name));
}

void AppController::reloadLink()
{
    m_rest.post(QStringLiteral("/api/links/reload"));
}

void AppController::pollNow()
{
    m_rest.post(QStringLiteral("/api/poll/now"));
}

void AppController::setPollEnabled(bool on)
{
    QJsonObject o;
    o[QStringLiteral("enabled")] = on;
    m_pollEnabled = on;
    emit pollChanged();
    m_rest.put(QStringLiteral("/api/poll"), o);
}

void AppController::setPollInterval(int ms)
{
    QJsonObject o;
    o[QStringLiteral("interval_ms")] = ms;
    m_pollIntervalMs = ms;
    emit pollChanged();
    m_rest.put(QStringLiteral("/api/poll"), o);
}

void AppController::sendRaw(const QString &hex, int addr, int expectLen)
{
    QJsonObject o;
    o[QStringLiteral("hex")] = hex;
    if (addr >= 0)
        o[QStringLiteral("addr")] = addr;
    if (expectLen > 0)
        o[QStringLiteral("expect_len")] = expectLen;
    m_rest.post(QStringLiteral("/api/debug/raw"), o);
}

void AppController::scan(int from, int to, int start, int count)
{
    QJsonObject o;
    o[QStringLiteral("from")] = from;
    o[QStringLiteral("to")] = to;
    o[QStringLiteral("start")] = start;
    o[QStringLiteral("count")] = count;
    m_rest.post(QStringLiteral("/api/debug/scan"), o);
}

void AppController::readRegs(int addr, int start, int count)
{
    QJsonObject o;
    o[QStringLiteral("addr")] = addr;
    o[QStringLiteral("start")] = start;
    o[QStringLiteral("count")] = count;
    m_rest.post(QStringLiteral("/api/debug/read"), o);
}

void AppController::writeReg(int addr, int reg, int value)
{
    QJsonObject o;
    o[QStringLiteral("addr")] = addr;
    o[QStringLiteral("reg")] = reg;
    o[QStringLiteral("value")] = value;
    m_rest.post(QStringLiteral("/api/debug/write"), o);
}

void AppController::loadHistory(int channel, const QString &from, const QString &to, int limit)
{
    QString path = QStringLiteral("/api/samples?limit=%1").arg(limit);
    if (channel > 0)
        path += QStringLiteral("&channel=%1").arg(channel);
    if (!from.isEmpty())
        path += QStringLiteral("&from=%1").arg(from);
    if (!to.isEmpty())
        path += QStringLiteral("&to=%1").arg(to);
    m_rest.get(path);
}

void AppController::clearAlarm(int channelNo)
{
    QJsonObject o;
    o[QStringLiteral("channel_no")] = channelNo;
    m_rest.post(QStringLiteral("/api/alarms/clear"), o);
    setMessage(QStringLiteral("已复归通道 %1 的告警").arg(channelNo));
}

void AppController::testForward(const QString &target)
{
    QJsonObject o;
    o[QStringLiteral("target")] = target;
    m_rest.post(QStringLiteral("/api/forward/test"), o);
}

void AppController::clearFrames()
{
    m_frames.clear();
}
