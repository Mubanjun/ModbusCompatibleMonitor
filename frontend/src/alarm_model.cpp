#include "alarm_model.h"

#include <QDateTime>

AlarmModel::AlarmModel(QObject *parent)
    : QAbstractListModel(parent)
{
}

int AlarmModel::rowCount(const QModelIndex &parent) const
{
    if (parent.isValid())
        return 0;
    return m_rows.size();
}

QVariant AlarmModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_rows.size())
        return {};
    const Row &r = m_rows.at(index.row());
    switch (role) {
    case IdRole: return r.id;
    case ChannelRole: return r.channel;
    case NameRole: return r.name;
    case TypeRole: return r.type;
    case ValueRole: return r.value;
    case ThresholdRole: return r.threshold;
    case RaisedRole: return r.raised;
    case ClearedRole: return r.cleared;
    case ActiveRole: return r.active;
    default: return {};
    }
}

QHash<int, QByteArray> AlarmModel::roleNames() const
{
    return {
        { IdRole, "alarmId" },
        { ChannelRole, "channelNo" },
        { NameRole, "name" },
        { TypeRole, "alarmType" },
        { ValueRole, "value" },
        { ThresholdRole, "threshold" },
        { RaisedRole, "raisedAt" },
        { ClearedRole, "clearedAt" },
        { ActiveRole, "active" },
    };
}

AlarmModel::Row AlarmModel::fromJson(const QJsonObject &o)
{
    Row r;
    r.id = static_cast<qint64>(o.value("id").toDouble());
    r.channel = o.value("channel_no").toInt();
    r.name = o.value("channel_name").toString();
    r.type = o.value("alarm_type").toString();
    r.value = o.value("value").toDouble();
    r.threshold = o.value("threshold").toDouble();
    const QDateTime raised = QDateTime::fromString(o.value("raised_at").toString(), Qt::ISODateWithMs);
    r.raised = raised.isValid() ? raised.toLocalTime().toString(QStringLiteral("MM-dd HH:mm:ss")) : QString();
    const QString clearedStr = o.value("cleared_at").toString();
    if (!clearedStr.isEmpty()) {
        const QDateTime cleared = QDateTime::fromString(clearedStr, Qt::ISODateWithMs);
        r.cleared = cleared.isValid() ? cleared.toLocalTime().toString(QStringLiteral("MM-dd HH:mm:ss")) : clearedStr;
        r.active = false;
    } else {
        r.active = true;
    }
    return r;
}

void AlarmModel::append(const QJsonObject &alarm)
{
    beginInsertRows(QModelIndex(), 0, 0);
    m_rows.prepend(fromJson(alarm));
    while (m_rows.size() > 500) {
        beginRemoveRows(QModelIndex(), m_rows.size() - 1, m_rows.size() - 1);
        m_rows.removeLast();
        endRemoveRows();
    }
    endInsertRows();
    emit countChanged();
}

void AlarmModel::setAll(const QJsonArray &alarms)
{
    beginResetModel();
    m_rows.clear();
    for (const QJsonValue &v : alarms)
        m_rows.append(fromJson(v.toObject()));
    endResetModel();
    emit countChanged();
}
