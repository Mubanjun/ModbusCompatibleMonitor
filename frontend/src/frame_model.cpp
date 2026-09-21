#include "frame_model.h"

#include <QDateTime>
#include <QJsonArray>

FrameModel::FrameModel(QObject *parent)
    : QAbstractListModel(parent)
{
}

int FrameModel::rowCount(const QModelIndex &parent) const
{
    if (parent.isValid())
        return 0;
    return m_rows.size();
}

QVariant FrameModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_rows.size())
        return {};
    const Row &r = m_rows.at(index.row());
    switch (role) {
    case TimeRole: return r.time;
    case DirectionRole: return r.direction;
    case AddrRole: return r.addr;
    case FuncRole: return r.func;
    case BytesRole: return r.bytes;
    case RttRole: return r.rtt;
    case QualityRole: return r.quality;
    case NoteRole: return r.note;
    default: return {};
    }
}

QHash<int, QByteArray> FrameModel::roleNames() const
{
    return {
        { TimeRole, "time" },
        { DirectionRole, "direction" },
        { AddrRole, "addr" },
        { FuncRole, "func" },
        { BytesRole, "bytes" },
        { RttRole, "rtt" },
        { QualityRole, "quality" },
        { NoteRole, "note" },
    };
}

FrameModel::Row FrameModel::fromJson(const QJsonObject &o)
{
    Row r;
    const QDateTime t = QDateTime::fromString(o.value("at").toString(), Qt::ISODateWithMs);
    r.time = t.isValid() ? t.toLocalTime().toString(QStringLiteral("HH:mm:ss.zzz"))
                         : QTime::currentTime().toString(QStringLiteral("HH:mm:ss.zzz"));
    r.direction = o.value("direction").toString();
    if (o.contains("addr") && !o.value("addr").isNull())
        r.addr = o.value("addr").toInt();
    if (o.contains("func") && !o.value("func").isNull())
        r.func = o.value("func").toInt();
    r.bytes = o.value("bytes").toString();
    if (o.contains("rtt_ms") && !o.value("rtt_ms").isNull())
        r.rtt = o.value("rtt_ms").toInt();
    r.quality = o.value("quality").toString();
    r.note = o.value("note").toString();
    return r;
}

void FrameModel::append(const QJsonObject &frame)
{
    beginInsertRows(QModelIndex(), m_rows.size(), m_rows.size());
    m_rows.append(fromJson(frame));
    while (m_rows.size() > 2000) {
        beginRemoveRows(QModelIndex(), 0, 0);
        m_rows.removeFirst();
        endRemoveRows();
    }
    endInsertRows();
    emit countChanged();
}

void FrameModel::setAll(const QJsonArray &frames)
{
    beginResetModel();
    m_rows.clear();
    for (const QJsonValue &v : frames)
        m_rows.append(fromJson(v.toObject()));
    endResetModel();
    emit countChanged();
}

void FrameModel::clear()
{
    beginResetModel();
    m_rows.clear();
    endResetModel();
    emit countChanged();
}
