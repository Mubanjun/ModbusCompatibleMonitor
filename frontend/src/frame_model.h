#pragma once

#include <QAbstractListModel>
#include <QJsonObject>
#include <QVector>

/// 报文调试台模型：原始 TX/RX 帧。
class FrameModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)
public:
    enum Roles {
        TimeRole = Qt::UserRole + 1,
        DirectionRole,
        AddrRole,
        FuncRole,
        BytesRole,
        RttRole,
        QualityRole,
        NoteRole
    };

    explicit FrameModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return m_rows.size(); }

    void append(const QJsonObject &frame);
    void setAll(const QJsonArray &frames);
    Q_INVOKABLE void clear();

signals:
    void countChanged();

private:
    struct Row {
        QString time;
        QString direction;
        int addr = -1;
        int func = -1;
        QString bytes;
        int rtt = -1;
        QString quality;
        QString note;
    };
    static Row fromJson(const QJsonObject &o);
    QVector<Row> m_rows;
};
