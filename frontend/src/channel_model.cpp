#include "channel_model.h"

#include <QDateTime>

ChannelModel::ChannelModel(QObject *parent)
    : QAbstractListModel(parent)
{
}

int ChannelModel::rowCount(const QModelIndex &parent) const
{
    if (parent.isValid())
        return 0;
    return m_rows.size();
}

QVariant ChannelModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_rows.size())
        return {};
    const Row &r = m_rows.at(index.row());
    switch (role) {
    case NoRole: return r.no;
    case NameRole: return r.name;
    case UnitRole: return r.unit;
    case ModelRole: return r.model;
    case ValueRole: return r.value;
    case RawRole: return r.raw;
    case QualityRole: return r.quality;
    case RttRole: return r.rtt;
    case UpdatedRole: return r.updated;
    case EnabledRole: return r.enabled;
    case HasValueRole: return r.hasValue;
    default: return {};
    }
}

QHash<int, QByteArray> ChannelModel::roleNames() const
{
    return {
        { NoRole, "channelNo" },
        { NameRole, "name" },
        { UnitRole, "unit" },
        { ModelRole, "model" },
        { ValueRole, "value" },
        { RawRole, "raw" },
        { QualityRole, "quality" },
        { RttRole, "rtt" },
        { UpdatedRole, "updated" },
        { EnabledRole, "enabled" },
        { HasValueRole, "hasValue" },
    };
}

QVariantMap ChannelModel::get(int row) const
{
    if (row < 0 || row >= m_rows.size())
        return {};
    const Row &r = m_rows.at(row);
    QVariantMap m;
    m["channelNo"] = r.no;
    m["name"] = r.name;
    m["unit"] = r.unit;
    m["model"] = r.model;
    m["value"] = r.value;
    m["raw"] = r.raw;
    m["quality"] = r.quality;
    m["rtt"] = r.rtt;
    m["updated"] = r.updated;
    m["enabled"] = r.enabled;
    m["hasValue"] = r.hasValue;
    return m;
}

int ChannelModel::indexOf(int no) const
{
    for (int i = 0; i < m_rows.size(); ++i) {
        if (m_rows.at(i).no == no)
            return i;
    }
    return -1;
}

void ChannelModel::ensureChannels(int count)
{
    if (m_rows.size() == count)
        return;
    beginResetModel();
    m_rows.clear();
    for (int i = 1; i <= count; ++i) {
        Row r;
        r.no = i;
        r.name = QStringLiteral("通道%1").arg(i);
        m_rows.append(r);
    }
    endResetModel();
    emit countChanged();
}

void ChannelModel::applyConfig(const QJsonArray &channels)
{
    for (const QJsonValue &v : channels) {
        const QJsonObject o = v.toObject();
        const int no = o.value("no").toInt();
        int idx = indexOf(no);
        if (idx < 0) {
            beginInsertRows(QModelIndex(), m_rows.size(), m_rows.size());
            Row r;
            r.no = no;
            m_rows.append(r);
            endInsertRows();
            idx = m_rows.size() - 1;
            emit countChanged();
        }
        Row &r = m_rows[idx];
        r.name = o.value("name").toString(r.name);
        r.unit = o.value("unit").toString();
        r.model = o.value("sensor_model").toString();
        r.enabled = o.value("enabled").toBool(true);
    }
    emit dataChanged(index(0, 0), index(m_rows.size() - 1, 0));
}

void ChannelModel::applySample(const QJsonObject &sample)
{
    const int no = sample.value("channel_no").toInt();
    int idx = indexOf(no);
    if (idx < 0) {
        beginInsertRows(QModelIndex(), m_rows.size(), m_rows.size());
        Row r;
        r.no = no;
        r.name = sample.value("channel_name").toString();
        m_rows.append(r);
        endInsertRows();
        idx = m_rows.size() - 1;
        emit countChanged();
    }
    Row &r = m_rows[idx];
    if (sample.contains("channel_name") && !sample.value("channel_name").toString().isEmpty())
        r.name = sample.value("channel_name").toString();
    r.unit = sample.value("unit").toString(r.unit);
    const QString q = sample.value("quality").toString(QStringLiteral("no_data"));
    r.quality = q;
    r.hasValue = sample.value("value").isDouble();
    if (r.hasValue)
        r.value = sample.value("value").toDouble();
    if (sample.value("raw_value").isDouble())
        r.raw = static_cast<qint64>(sample.value("raw_value").toDouble());
    r.rtt = sample.value("rtt_ms").toInt();
    const QString t = sample.value("record_time").toString();
    const QDateTime dt = QDateTime::fromString(t, Qt::ISODateWithMs);
    r.updated = dt.isValid() ? dt.toLocalTime().toString(QStringLiteral("HH:mm:ss"))
                             : QTime::currentTime().toString(QStringLiteral("HH:mm:ss"));

    emit dataChanged(index(idx, 0), index(idx, 0));
    emit updated();
}

void ChannelModel::clearValues()
{
    if (m_rows.isEmpty())
        return;
    for (Row &r : m_rows) {
        r.hasValue = false;
        r.quality = QStringLiteral("no_data");
    }
    emit dataChanged(index(0, 0), index(m_rows.size() - 1, 0));
    emit updated();
}
