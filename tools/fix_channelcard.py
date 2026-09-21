# -*- coding: utf-8 -*-
import io
cc = r"D:\EXPPROJECT\JDRK-MonitorPlatform\frontend\qml\components\ChannelCard.qml"
s = io.open(cc, encoding="utf-8").read()
s = s.replace("property var data: ({})", "property var info: ({})")
s = s.replace("card.data.", "card.info.")
io.open(cc, "w", encoding="utf-8").write(s)

dp = r"D:\EXPPROJECT\JDRK-MonitorPlatform\frontend\qml\pages\DashboardPage.qml"
s = io.open(dp, encoding="utf-8").read()
s = s.replace("data: ({", "info: ({")
io.open(dp, "w", encoding="utf-8").write(s)
print("renamed data -> info")
