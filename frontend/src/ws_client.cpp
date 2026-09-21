#include "ws_client.h"

#include <QCryptographicHash>
#include <QRandomGenerator>
#include <QTimer>
#include <QHostAddress>

WsClient::WsClient(QObject *parent)
    : QObject(parent),
      m_reconnectTimer(new QTimer(this))
{
    m_reconnectTimer->setSingleShot(true);
    m_reconnectTimer->setInterval(2000);
    connect(m_reconnectTimer, &QTimer::timeout, this, [this]() {
        if (m_wantOpen && !m_open)
            open(m_url);
    });

    connect(&m_socket, &QTcpSocket::connected, this, &WsClient::onConnected);
    connect(&m_socket, &QTcpSocket::readyRead, this, &WsClient::onReadyRead);
    connect(&m_socket, &QTcpSocket::disconnected, this, &WsClient::onDisconnected);
    connect(&m_socket, &QTcpSocket::errorOccurred, this, [this](QAbstractSocket::SocketError) {
        fail(m_socket.errorString());
    });
}

void WsClient::open(const QUrl &url)
{
    m_url = url;
    m_wantOpen = true;
    m_buffer.clear();
    m_handshakeDone = false;
    if (m_socket.state() != QAbstractSocket::UnconnectedState)
        m_socket.abort();

    QByteArray seed(16, 0);
    for (int i = 0; i < seed.size(); ++i)
        seed[i] = static_cast<char>(QRandomGenerator::global()->bounded(256));
    m_key = seed.toBase64();

    const QString host = url.host();
    const int port = url.port(80);
    m_socket.connectToHost(host, static_cast<quint16>(port));
}

void WsClient::close()
{
    m_wantOpen = false;
    m_reconnectTimer->stop();
    if (m_open) {
        sendFrame(0x8, QByteArray());
    }
    m_socket.disconnectFromHost();
    if (m_socket.state() != QAbstractSocket::UnconnectedState)
        m_socket.abort();
    if (m_open) {
        m_open = false;
        emit closed();
    }
}

void WsClient::onConnected()
{
    const QString host = m_url.host();
    const int port = m_url.port(80);
    QString path = m_url.path();
    if (path.isEmpty())
        path = QStringLiteral("/");
    if (m_url.hasQuery())
        path += QLatin1Char('?') + m_url.query();

    QByteArray req;
    req += "GET " + path.toUtf8() + " HTTP/1.1\r\n";
    req += "Host: " + host.toUtf8() + ":" + QByteArray::number(port) + "\r\n";
    req += "Upgrade: websocket\r\n";
    req += "Connection: Upgrade\r\n";
    req += "Sec-WebSocket-Key: " + m_key + "\r\n";
    req += "Sec-WebSocket-Version: 13\r\n";
    req += "\r\n";
    m_socket.write(req);
}

void WsClient::onReadyRead()
{
    m_buffer += m_socket.readAll();

    if (!m_handshakeDone) {
        const int end = m_buffer.indexOf("\r\n\r\n");
        if (end < 0)
            return;
        const QByteArray head = m_buffer.left(end);
        m_buffer.remove(0, end + 4);
        if (!head.startsWith("HTTP/1.1 101") && !head.startsWith("HTTP/1.1 1")) {
            fail(QStringLiteral("WebSocket 握手失败: ") + QString::fromUtf8(head.left(80)));
            m_socket.abort();
            return;
        }
        m_handshakeDone = true;
        m_open = true;
        emit opened();
    }
    processBuffer();
}

void WsClient::processBuffer()
{
    for (;;) {
        if (m_buffer.size() < 2)
            return;
        const quint8 b0 = static_cast<quint8>(m_buffer.at(0));
        const quint8 b1 = static_cast<quint8>(m_buffer.at(1));
        const quint8 opcode = b0 & 0x0F;
        const bool masked = (b1 & 0x80) != 0;
        quint64 len = b1 & 0x7F;
        int off = 2;
        if (len == 126) {
            if (m_buffer.size() < 4)
                return;
            len = (static_cast<quint8>(m_buffer.at(2)) << 8) | static_cast<quint8>(m_buffer.at(3));
            off = 4;
        } else if (len == 127) {
            if (m_buffer.size() < 10)
                return;
            len = 0;
            for (int i = 0; i < 8; ++i)
                len = (len << 8) | static_cast<quint8>(m_buffer.at(2 + i));
            off = 10;
        }
        QByteArray mask;
        if (masked) {
            if (m_buffer.size() < off + 4)
                return;
            mask = m_buffer.mid(off, 4);
            off += 4;
        }
        if (static_cast<quint64>(m_buffer.size()) < static_cast<quint64>(off) + len)
            return;

        QByteArray payload = m_buffer.mid(off, static_cast<int>(len));
        m_buffer.remove(0, off + static_cast<int>(len));
        if (masked) {
            for (int i = 0; i < payload.size(); ++i)
                payload[i] = payload.at(i) ^ mask.at(i % 4);
        }

        if (opcode == 0x1) {
            emit textMessage(QString::fromUtf8(payload));
        } else if (opcode == 0x8) {
            close();
            return;
        } else if (opcode == 0x9) {
            sendFrame(0xA, payload);
        }
    }
}

void WsClient::sendFrame(quint8 opcode, const QByteArray &payload)
{
    if (m_socket.state() != QAbstractSocket::ConnectedState)
        return;
    QByteArray frame;
    frame.append(static_cast<char>(0x80 | opcode));
    const int n = payload.size();
    if (n < 126) {
        frame.append(static_cast<char>(0x80 | n));
    } else if (n < 65536) {
        frame.append(static_cast<char>(0x80 | 126));
        frame.append(static_cast<char>((n >> 8) & 0xFF));
        frame.append(static_cast<char>(n & 0xFF));
    } else {
        frame.append(static_cast<char>(0x80 | 127));
        for (int i = 7; i >= 0; --i)
            frame.append(static_cast<char>((static_cast<quint64>(n) >> (8 * i)) & 0xFF));
    }
    QByteArray mask(4, 0);
    for (int i = 0; i < 4; ++i)
        mask[i] = static_cast<char>(QRandomGenerator::global()->bounded(256));
    frame.append(mask);
    QByteArray masked = payload;
    for (int i = 0; i < masked.size(); ++i)
        masked[i] = masked.at(i) ^ mask.at(i % 4);
    frame.append(masked);
    m_socket.write(frame);
    m_socket.flush();
}

void WsClient::sendText(const QString &text)
{
    sendFrame(0x1, text.toUtf8());
}

void WsClient::onDisconnected()
{
    if (m_open) {
        m_open = false;
        emit closed();
    }
    scheduleReconnect();
}

void WsClient::fail(const QString &error)
{
    if (m_open) {
        m_open = false;
        emit closed();
    }
    emit failed(error);
    scheduleReconnect();
}

void WsClient::scheduleReconnect()
{
    if (m_wantOpen)
        m_reconnectTimer->start();
}
