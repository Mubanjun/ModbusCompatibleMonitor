# -*- coding: utf-8 -*-
"""用 Windows Restart Manager 找出占用 COM3 的进程"""
import ctypes
from ctypes import wintypes as w

rm = ctypes.WinDLL('rstrtmgr')
CCH_RM_SESSION_KEY = 32
CCH_RM_MAX_APP_NAME = 255
CCH_RM_MAX_SVC_NAME = 63

class RM_UNIQUE_PROCESS(ctypes.Structure):
    _fields_ = [("dwProcessId", w.DWORD), ("ProcessStartTime", w.FILETIME)]

class RM_PROCESS_INFO(ctypes.Structure):
    _fields_ = [
        ("Process", RM_UNIQUE_PROCESS),
        ("strAppName", w.WCHAR * (CCH_RM_MAX_APP_NAME + 1)),
        ("strServiceShortName", w.WCHAR * (CCH_RM_MAX_SVC_NAME + 1)),
        ("ApplicationType", ctypes.c_uint),
        ("AppStatus", w.ULONG),
        ("TSSessionId", w.DWORD),
        ("bRestartable", w.BOOL),
    ]

sess = w.DWORD(0)
key = ctypes.create_unicode_buffer(CCH_RM_SESSION_KEY + 1)
rc = rm.RmStartSession(ctypes.byref(sess), 0, key)
print("RmStartSession rc=", rc, "sess=", sess.value)

for res in [r'\\.\COM3', r'\Device\Serial4', 'COM3']:
    arr1 = (w.LPCWSTR * 1)(res)
    rc = rm.RmRegisterResources(sess, 1, arr1, 0, None, 0, None)
    print("register", res, "rc=", rc)

need = w.UINT(0); count = w.UINT(0); reason = w.DWORD(0)
rc = rm.RmGetList(sess, ctypes.byref(need), ctypes.byref(count), None, ctypes.byref(reason))
print("RmGetList probe rc=", rc, "need=", need.value)
if need.value:
    arr = (RM_PROCESS_INFO * need.value)()
    count = w.UINT(need.value)
    rc = rm.RmGetList(sess, ctypes.byref(need), ctypes.byref(count), arr, ctypes.byref(reason))
    print("RmGetList rc=", rc, "count=", count.value)
    for i in range(count.value):
        print("  pid=%d app=%s svc=%s" % (arr[i].Process.dwProcessId, arr[i].strAppName, arr[i].strServiceShortName))
else:
    print("no process reported")
rm.RmEndSession(sess)
