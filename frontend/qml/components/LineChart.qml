import QtQuick

// 轻量折线图（Canvas 手绘，不依赖 QtCharts）
Item {
    id: chart
    property var theme
    property var points: []
    property color lineColor: theme ? theme.accent : "#38bdf8"
    property string emptyText: "暂无数据"
    property string unit: ""

    onPointsChanged: canvas.requestPaint()
    onWidthChanged: canvas.requestPaint()
    onHeightChanged: canvas.requestPaint()

    Rectangle {
        anchors.fill: parent
        radius: theme ? theme.radius : 10
        color: theme ? theme.surface : "#151d2e"
        border.width: 1
        border.color: theme ? theme.border : "#2a3548"
    }

    Text {
        anchors.centerIn: parent
        visible: !chart.points || chart.points.length === 0
        text: chart.emptyText
        color: theme ? theme.textDim : "#8fa3bf"
        font.pixelSize: 13
    }

    Canvas {
        id: canvas
        anchors.fill: parent
        anchors.margins: 10

        onPaint: {
            var ctx = getContext("2d");
            ctx.reset();
            var t = chart.theme;
            if (!chart.points || chart.points.length === 0)
                return;

            var w = width, h = height;
            var padL = 44, padB = 22, padT = 8, padR = 8;
            var plotW = w - padL - padR;
            var plotH = h - padT - padB;

            var vals = [];
            var ts = [];
            for (var i = 0; i < chart.points.length; ++i) {
                var p = chart.points[i];
                if (p.hasValue) {
                    vals.push(Number(p.v));
                    ts.push(Number(p.ts));
                }
            }
            if (vals.length === 0)
                return;

            var minV = Math.min.apply(null, vals);
            var maxV = Math.max.apply(null, vals);
            if (maxV - minV < 1e-9) { maxV = minV + 1; minV = minV - 1; }
            var span = maxV - minV;
            minV -= span * 0.08;
            maxV += span * 0.08;
            span = maxV - minV;

            var minT = Math.min.apply(null, ts);
            var maxT = Math.max.apply(null, ts);
            if (maxT === minT) maxT = minT + 1;

            // 网格 + Y 轴刻度
            ctx.strokeStyle = Qt.rgba(0.55, 0.64, 0.75, 0.18);
            ctx.fillStyle = t ? t.textDim : "#8fa3bf";
            ctx.font = "10px sans-serif";
            ctx.lineWidth = 1;
            var rows = 4;
            for (var r = 0; r <= rows; ++r) {
                var yy = padT + plotH * r / rows;
                ctx.beginPath();
                ctx.moveTo(padL, yy);
                ctx.lineTo(padL + plotW, yy);
                ctx.stroke();
                var vv = maxV - span * r / rows;
                ctx.fillText(vv.toFixed(2), 4, yy + 3);
            }

            // 折线
            ctx.strokeStyle = chart.lineColor;
            ctx.lineWidth = 2;
            ctx.beginPath();
            var started = false;
            for (var k = 0; k < chart.points.length; ++k) {
                var q = chart.points[k];
                if (!q.hasValue) continue;
                var x = padL + plotW * (Number(q.ts) - minT) / (maxT - minT);
                var y = padT + plotH * (1 - (Number(q.v) - minV) / span);
                if (!started) { ctx.moveTo(x, y); started = true; }
                else ctx.lineTo(x, y);
            }
            ctx.stroke();

            // X 轴首尾时间
            ctx.fillStyle = t ? t.textDim : "#8fa3bf";
            var first = chart.points[0];
            var last = chart.points[chart.points.length - 1];
            if (first && first.time) ctx.fillText(first.time, padL, h - 6);
            if (last && last.time) {
                var lw = ctx.measureText(last.time).width;
                ctx.fillText(last.time, padL + plotW - lw, h - 6);
            }
        }
    }
}
