#pragma once

#include <QAbstractListModel>
#include <QJsonArray>
#include <QJsonObject>
#include <QVector>

/// 告警记录模型（WS alarm 事件 + REST /api/alarms）。
class AlarmModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)
public:
    enum Roles {
        IdRole = Qt::UserRole + 1,
        ChannelRole,
        NameRole,
        TypeRole,
        ValueRole,
        ThresholdRole,
        RaisedRole,
        ClearedRole,
        ActiveRole
    };

    explicit AlarmModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return m_rows.size(); }

    void append(const QJsonObject &alarm);
    void setAll(const QJsonArray &alarms);

signals:
    void countChanged();

private:
    struct Row {
        qint64 id = 0;
        int channel = 0;
        QString name;
        QString type;
        double value = 0.0;
        double threshold = 0.0;
        QString raised;
        QString cleared;
        bool active = true;
    };
    static Row fromJson(const QJsonObject &o);
    QVector<Row> m_rows;
};
