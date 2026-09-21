#pragma once

#include <QObject>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QVariantList>
#include <QVariantMap>

#include "alarm_model.h"
#include "channel_model.h"
#include "frame_model.h"
#include "rest_client.h"
#include "ws_client.h"

/// 前端核心控制器：对接 Rust 后端的 REST + WebSocket，
/// 维护实时通道模型、告警、报文与各页面所需的状态属性。
class AppController : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool connected READ connected NOTIFY connectedChanged)
    Q_PROPERTY(QString baseUrl READ baseUrl WRITE setBaseUrl NOTIFY baseUrlChanged)
    Q_PROPERTY(QVariantMap linkStatus READ linkStatus NOTIFY linkStatusChanged)
    Q_PROPERTY(QVariantMap health READ health NOTIFY healthChanged)
    Q_PROPERTY(QVariantMap lastRound READ lastRound NOTIFY lastRoundChanged)
    Q_PROPERTY(QVariantMap lastDebug READ lastDebug NOTIFY lastDebugChanged)
    Q_PROPERTY(QVariantMap forwardStatus READ forwardStatus NOTIFY forwardStatusChanged)
    Q_PROPERTY(QVariantList channelsConfig READ channelsConfig NOTIFY channelsConfigChanged)
    Q_PROPERTY(QVariantList linkProfiles READ linkProfiles NOTIFY linkProfilesChanged)
    Q_PROPERTY(QVariantList historyPoints READ historyPoints NOTIFY historyChanged)
    Q_PROPERTY(QVariantList scanResults READ scanResults NOTIFY scanResultsChanged)
    Q_PROPERTY(QString message READ message NOTIFY messageChanged)
    Q_PROPERTY(bool pollEnabled READ pollEnabled NOTIFY pollChanged)
    Q_PROPERTY(int pollIntervalMs READ pollIntervalMs NOTIFY pollChanged)
    Q_PROPERTY(QString activeLink READ activeLink NOTIFY linkStatusChanged)
    Q_PROPERTY(QString serverVersion READ serverVersion NOTIFY healthChanged)
    Q_PROPERTY(ChannelModel *channelModel READ channelModel CONSTANT)
    Q_PROPERTY(AlarmModel *alarmModel READ alarmModel CONSTANT)
    Q_PROPERTY(FrameModel *frameModel READ frameModel CONSTANT)

public:
    explicit AppController(QObject *parent = nullptr);

    /// serverUrl 为空时使用内置默认地址；非空（来自 --server / JDRK_SERVER / QSettings）则覆盖。
    void start(const QString &serverUrl = QString());

    bool connected() const { return m_connected; }
    QString baseUrl() const { return m_rest.baseUrl(); }
    void setBaseUrl(const QString &url);

    QVariantMap linkStatus() const { return m_linkStatus; }
    QVariantMap health() const { return m_health; }
    QVariantMap lastRound() const { return m_lastRound; }
    QVariantMap lastDebug() const { return m_lastDebug; }
    QVariantMap forwardStatus() const { return m_forwardStatus; }
    QVariantList channelsConfig() const { return m_channelsConfig; }
    QVariantList linkProfiles() const { return m_linkProfiles; }
    QVariantList historyPoints() const { return m_historyPoints; }
    QVariantList scanResults() const { return m_scanResults; }
    QString message() const { return m_message; }
    bool pollEnabled() const { return m_pollEnabled; }
    int pollIntervalMs() const { return m_pollIntervalMs; }
    QString activeLink() const { return m_linkStatus.value("profile").toString(); }
    QString serverVersion() const { return m_health.value("version").toString(); }

    ChannelModel *channelModel() { return &m_channels; }
    AlarmModel *alarmModel() { return &m_alarms; }
    FrameModel *frameModel() { return &m_frames; }

    Q_INVOKABLE void refreshAll();
    Q_INVOKABLE void refreshHealth();
    Q_INVOKABLE void refreshChannels();
    Q_INVOKABLE void refreshLink();
    Q_INVOKABLE void refreshAlarms();
    Q_INVOKABLE void refreshFrames();

    Q_INVOKABLE void saveChannel(int no, const QVariantMap &patch);
    Q_INVOKABLE void activateLink(const QString &name);
    Q_INVOKABLE void reloadLink();
    Q_INVOKABLE void pollNow();
    Q_INVOKABLE void setPollEnabled(bool on);
    Q_INVOKABLE void setPollInterval(int ms);

    Q_INVOKABLE void sendRaw(const QString &hex, int addr, int expectLen);
    Q_INVOKABLE void scan(int from, int to, int start, int count);
    Q_INVOKABLE void readRegs(int addr, int start, int count);
    Q_INVOKABLE void writeReg(int addr, int reg, int value);

    Q_INVOKABLE void loadHistory(int channel, const QString &from, const QString &to, int limit);
    Q_INVOKABLE void clearAlarm(int channelNo);
    Q_INVOKABLE void testForward(const QString &target);
    Q_INVOKABLE void clearFrames();

signals:
    void connectedChanged();
    void baseUrlChanged();
    void linkStatusChanged();
    void healthChanged();
    void lastRoundChanged();
    void lastDebugChanged();
    void forwardStatusChanged();
    void channelsConfigChanged();
    void linkProfilesChanged();
    void historyChanged();
    void scanResultsChanged();
    void messageChanged();
    void pollChanged();

private:
    void onRest(const QString &path, const QJsonDocument &doc, bool ok, const QString &error);
    void onWsText(const QString &text);
    void setMessage(const QString &msg);
    void setLinkStatus(const QVariantMap &m);
    void applyConfig(const QJsonObject &cfg);

    RestClient m_rest;
    WsClient m_ws;
    ChannelModel m_channels;
    AlarmModel m_alarms;
    FrameModel m_frames;

    QVariantMap m_linkStatus;
    QVariantMap m_health;
    QVariantMap m_lastRound;
    QVariantMap m_lastDebug;
    QVariantMap m_forwardStatus;
    QVariantList m_channelsConfig;
    QVariantList m_linkProfiles;
    QVariantList m_historyPoints;
    QVariantList m_scanResults;
    QString m_message;
    bool m_connected = false;
    bool m_pollEnabled = false;
    int m_pollIntervalMs = 10000;
};
