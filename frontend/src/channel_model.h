#pragma once

#include <QAbstractListModel>
#include <QJsonArray>
#include <QJsonObject>
#include <QVector>

/// 20 路通道的实时值模型（WS sample 事件驱动）。
class ChannelModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)
public:
    enum Roles {
        NoRole = Qt::UserRole + 1,
        NameRole,
        UnitRole,
        ModelRole,
        ValueRole,
        RawRole,
        QualityRole,
        RttRole,
        UpdatedRole,
        EnabledRole,
        HasValueRole
    };

    explicit ChannelModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return m_rows.size(); }

    Q_INVOKABLE QVariantMap get(int row) const;

    void ensureChannels(int count);
    void applyConfig(const QJsonArray &channels);
    void applySample(const QJsonObject &sample);
    void clearValues();

signals:
    void countChanged();
    void updated();

private:
    struct Row {
        int no = 0;
        QString name;
        QString unit;
        QString model;
        double value = 0.0;
        qint64 raw = 0;
        QString quality = QStringLiteral("no_data");
        int rtt = 0;
        QString updated;
        bool enabled = true;
        bool hasValue = false;
    };

    int indexOf(int no) const;
    QVector<Row> m_rows;
};
