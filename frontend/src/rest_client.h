#pragma once

#include <QObject>
#include <QNetworkAccessManager>
#include <QJsonDocument>
#include <QJsonObject>
#include <QString>

/// 轻量 REST 客户端：统一封装 GET/POST/PUT，异步回调通过 finished 信号返回。
class RestClient : public QObject
{
    Q_OBJECT
public:
    explicit RestClient(QObject *parent = nullptr);

    QString baseUrl() const { return m_base; }
    void setBaseUrl(const QString &url);

    void get(const QString &path);
    void post(const QString &path, const QJsonObject &body = QJsonObject());
    void put(const QString &path, const QJsonObject &body = QJsonObject());

signals:
    void finished(const QString &path, const QJsonDocument &doc, bool ok, const QString &error);

private:
    void send(const QByteArray &method, const QString &path, const QJsonObject &body);

    QNetworkAccessManager m_nam;
    QString m_base;
};
