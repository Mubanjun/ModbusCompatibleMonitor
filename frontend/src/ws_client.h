#pragma once

#include <QObject>
#include <QTcpSocket>
#include <QByteArray>
#include <QUrl>

class QTimer;

/// 自研 RFC6455 WebSocket 客户端（基于 QTcpSocket）。
/// 不依赖 QtWebSockets 模块，便于跨平台与裁剪。
class WsClient : public QObject
{
    Q_OBJECT
public:
    explicit WsClient(QObject *parent = nullptr);

    void open(const QUrl &url);
    void close();
    void sendText(const QString &text);
    bool isOpen() const { return m_open; }

signals:
    void opened();
    void closed();
    void textMessage(const QString &text);
    void failed(const QString &error);

private slots:
    void onConnected();
    void onReadyRead();
    void onDisconnected();

private:
    void fail(const QString &error);
    void sendFrame(quint8 opcode, const QByteArray &payload);
    void processBuffer();
    void scheduleReconnect();

    QTcpSocket m_socket;
    QTimer *m_reconnectTimer;
    QByteArray m_buffer;
    QByteArray m_key;
    QUrl m_url;
    bool m_handshakeDone = false;
    bool m_open = false;
    bool m_wantOpen = false;
};
