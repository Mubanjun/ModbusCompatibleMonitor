<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import type { LogFileResp, LogLevelResp } from '@/apis'
import { downloadLogFile, exportDiagnosticPackage, listLogFile, listLogLevel, setLogLevel } from '@/apis'
import { useDownload } from '@/hooks'

defineOptions({ name: 'EnvLog' })

const levels = ref<LogLevelResp[]>([])
const files = ref<LogFileResp[]>([])
const loading = ref(false)
const exporting = ref(false)
const levelOptions = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF']

const levelColumns: TableInstance['columns'] = [
  { title: '日志器', dataIndex: 'logger', minWidth: 320, ellipsis: true, tooltip: true },
  { title: '当前级别', dataIndex: 'level', slotName: 'level', width: 200, align: 'center' },
  { title: '受平台管理', dataIndex: 'managed', slotName: 'managed', width: 120, align: 'center' },
]

const fileColumns: TableInstance['columns'] = [
  { title: '文件名', dataIndex: 'name', minWidth: 220, ellipsis: true, tooltip: true },
  { title: '模块', dataIndex: 'module', width: 140 },
  { title: '相对路径', dataIndex: 'path', minWidth: 240, ellipsis: true, tooltip: true },
  { title: '大小', dataIndex: 'sizeLabel', width: 120, align: 'right' },
  { title: '最后修改', dataIndex: 'lastModified', width: 200 },
  { title: '操作', dataIndex: 'action', slotName: 'action', width: 100, align: 'center' },
]

const loadLevels = async () => {
  const res = await listLogLevel()
  levels.value = res.data ?? []
}

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await listLogFile()
    files.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

const onChangeLevel = async (logger: string, level: string | number | boolean) => {
  const res = await setLogLevel({ logger, level: String(level) })
  Message.success(`已切换 ${logger} → ${res.data?.level ?? level}`)
  await loadLevels()
}

const onDownload = async (file: LogFileResp) => {
  // 缺陷 65：http.download 只返回 Blob 响应，浏览器不会自动保存，必须交给 useDownload 落盘
  await useDownload(() => downloadLogFile(file.path), file.name || 'jdrk.log')
}

const onExport = async () => {
  exporting.value = true
  try {
    await useDownload(() => exportDiagnosticPackage(), `诊断包_${Date.now()}.zip`)
    Message.success('诊断包已开始下载')
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await loadLevels()
  await loadFiles()
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" title="日志级别（即时生效，无需重启）" style="margin-bottom: 12px">
      <a-table
        row-key="logger"
        :data="levels"
        :columns="levelColumns"
        :pagination="false"
        :bordered="false"
        size="small"
      >
        <template #level="{ record }">
          <a-select
            v-permission="['monitor:log:level']"
            :model-value="record.level"
            :options="levelOptions.map((item: string) => ({ label: item, value: item }))"
            style="width: 140px"
            @change="(value: any) => onChangeLevel(record.logger, value)"
          />
        </template>
        <template #managed="{ record }">
          <a-tag :color="record.managed ? 'green' : 'gray'">{{ record.managed ? '是' : '否' }}</a-tag>
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false" title="日志文件" style="margin-bottom: 12px">
      <template #extra>
        <a-space>
          <a-button v-permission="['monitor:diagnostic:export']" type="primary" :loading="exporting" @click="onExport">
            导出诊断包
          </a-button>
          <a-button @click="loadFiles">
            刷新
          </a-button>
        </a-space>
      </template>
      <a-table
        row-key="path"
        :data="files"
        :columns="fileColumns"
        :loading="loading"
        :pagination="{ pageSize: 20 }"
        :bordered="false"
        size="small"
        :scroll="{ x: '100%', minWidth: 1100 }"
      >
        <template #action="{ record }">
          <a-link @click="onDownload(record)">
            下载
          </a-link>
        </template>
      </a-table>
    </a-card>
  </GiPageLayout>
</template>
