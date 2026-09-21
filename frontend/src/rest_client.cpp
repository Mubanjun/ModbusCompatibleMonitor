#include "rest_client.h"

#include <QNetworkRequest>
#include <QNetworkReply>
#include <QUrl>

RestClient::RestClient(QObject *parent)
    : QObject(parent),
      m_base(QStringLiteral("http://127.0.0.1:8790"))
{
}

void RestClient::setBaseUrl(const QString &url)
{
    m_base = url;
    while (m_base.endsWith(QLatin1Char('/')))
        m_base.chop(1);
}

void RestClient::get(const QString &path)
{
    send("GET", path, QJsonObject());
}

void RestClient::post(const QString &path, const QJsonObject &body)
{
    send("POST", path, body);
}

void RestClient::put(const QString &path, const QJsonObject &body)
{
    send("PUT", path, body);
}

void RestClient::send(const QByteArray &method, const QString &path, const QJsonObject &body)
{
    QUrl url(m_base + path);
    QNetworkRequest req(url);
    req.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));

    QNetworkReply *reply = nullptr;
    const QByteArray payload = body.isEmpty() ? QByteArray("{}") : QJsonDocument(body).toJson(QJsonDocument::Compact);
    if (method == "GET")
        reply = m_nam.get(req);
    else if (method == "POST")
        reply = m_nam.post(req, payload);
    else
        reply = m_nam.put(req, payload);

    connect(reply, &QNetworkReply::finished, this, [this, reply, path]() {
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const QByteArray data = reply->readAll();
        const bool netOk = (reply->error() == QNetworkReply::NoError);
        const bool ok = netOk && status >= 200 && status < 300;
        QJsonDocument doc;
        QJsonParseError perr{};
        doc = QJsonDocument::fromJson(data, &perr);
        QString err;
        if (!ok) {
            if (!netOk)
                err = reply->errorString();
            else
                err = QStringLiteral("HTTP %1").arg(status);
            const QJsonObject o = doc.object();
            if (o.contains(QStringLiteral("error")))
                err = o.value(QStringLiteral("error")).toString();
        }
        reply->deleteLater();
        emit finished(path, doc, ok, err);
    });
}
