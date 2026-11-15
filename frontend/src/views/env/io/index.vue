<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { BackupFileResp, ImportPreviewResp, ImportReportResp } from '@/apis'
import {
  createBackup,
  downloadBackup,
  executeImport,
  exportDataCsv,
  exportTableSql,
  getBackupStatus,
  listBackups,
  listIoTables,
  previewImport,
  purgeBackups,
  restoreBackup,
  verifyBackup,
} from '@/apis'
import { useDownload } from '@/hooks'

defineOptions({ name: 'EnvIo' })

const importTypes = [
  { label: '设备清单（DEVICE）', value: 'DEVICE' },
  { label: '历史数据（HISTORY）', value: 'HISTORY' },
  { label: '业务配置（CONFIG）', value: 'CONFIG' },
]

const importType = ref('DEVICE')
const importFile = ref<File>()
const uploadFileList = ref<any[]>([])
const preview = ref<ImportPreviewResp>()
const report = ref<ImportReportResp>()
const importing = ref(false)

const exportQuery = reactive<{ deviceId?: string, nodeId?: number, analogNo?: number, range?: string[] }>({})
const tables = ref<string[]>([])
const exportTable = ref('biz_device')

const backups = ref<BackupFileResp[]>([])
const backupStatus = ref<Record<string, any>>({})
const backupForm = reactive<{ password?: string, includeLogs: boolean }>({ includeLogs: false })
const backupLoading = ref(false)

const errorColumns: TableInstance['columns'] = [
  { title: '行号', dataIndex: 'row', width: 90 },
  { title: '错误原因', dataIndex: 'message', minWidth: 260, ellipsis: true, tooltip: true },
]

const backupColumns: TableInstance['columns'] = [
  { title: '文件名', dataIndex: 'name', minWidth: 260, ellipsis: true, tooltip: true },
  { title: '大小', dataIndex: 'sizeLabel', width: 110, align: 'right' },
  { title: '创建时间', dataIndex: 'createdAt', width: 190 },
  { title: '加密', dataIndex: 'encrypted', slotName: 'encrypted', width: 90, align: 'center' },
  { title: '操作', dataIndex: 'action', slotName: 'action', width: 260, align: 'center' },
]

const loadPreview = async (file: File) => {
  preview.value = await previewImport(importType.value, file).then((res) => res.data)
  if (preview.value?.headersValid) {
    Message.success('表头校验通过')
  } else {
    Message.warning(`缺少必需列：${(preview.value?.missingHeaders ?? []).join(',')}`)
  }
}

const handleUpload = (options: any) => {
  const { fileItem, onSuccess, onError } = options
  importFile.value = fileItem.file as File
  uploadFileList.value = [fileItem]
  loadPreview(importFile.value).then(() => onSuccess()).catch(onError)
}

const onImportTypeChange = async () => {
  report.value = undefined
  if (importFile.value) {
    await loadPreview(importFile.value)
  }
}

const runImport = async (dryRun: boolean) => {
  if (!importFile.value) {
    Message.warning('请先选择导入文件')
    return
  }
  importing.value = true
  try {
    const res = await executeImport(importType.value, importFile.value, dryRun)
    report.value = res.data
    Message.success(`${dryRun ? '校验' : '导入'}完成：成功 ${res.data?.success}，跳过 ${res.data?.skipped}，失败 ${res.data?.failed}`)
  } finally {
    importing.value = false
  }
}

const onExportCsv = async () => {
  const query: Record<string, any> = {
    deviceId: exportQuery.deviceId,
    nodeId: exportQuery.nodeId,
    analogNo: exportQuery.analogNo,
  }
  if (exportQuery.range?.length === 2) {
    query.startTime = `${exportQuery.range[0]} 00:00:00`
    query.endTime = `${exportQuery.range[1]} 23:59:59`
  }
  // 缺陷 65：http.download 只返回 Blob 响应，浏览器不会自动保存；必须交给 useDownload 落盘，
  // 否则「导出已开始」只是个假提示（服务端生成了、传输完了、用户磁盘上没有文件）。
  await useDownload(() => exportDataCsv(query), `历史数据_${Date.now()}.csv`)
  Message.success('导出已开始')
}

const onExportTable = async () => {
  await useDownload(() => exportTableSql(exportTable.value), `${exportTable.value}_${Date.now()}.sql`)
  Message.success('导出已开始')
}

const loadTables = async () => {
  const res = await listIoTables()
  tables.value = res.data ?? []
  if (tables.value.length && !tables.value.includes(exportTable.value)) {
    exportTable.value = tables.value[0]
  }
}

const loadBackups = async () => {
  const [list, status] = await Promise.all([listBackups(), getBackupStatus()])
  backups.value = (list.data ?? []).map((item: BackupFileResp) => ({
    ...item,
    sizeLabel: item.size == null ? '-' : `${(item.size / 1024).toFixed(1)} KB`,
  })) as any
  backupStatus.value = status.data ?? {}
}

const onCreateBackup = async () => {
  backupLoading.value = true
  try {
    const res = await createBackup(backupForm)
    Message.success(`备份完成：${res.data?.name}`)
    await loadBackups()
  } finally {
    backupLoading.value = false
  }
}

const onVerify = async (row: BackupFileResp) => {
  const res = await verifyBackup({ name: row.name, password: backupForm.password })
  const data = res.data
  if (data?.valid) {
    Message.success(`校验通过：${data.checked} 个条目`)
  } else {
    Message.error(`校验失败：${(data?.mismatched ?? []).join(', ')}`)
  }
}

const onDownloadBackup = async (name: string) => {
  // 缺陷 65：模板原先直接调 API 函数，Blob 被丢掉，用户磁盘上不会有文件
  await useDownload(() => downloadBackup(name), name)
}

const onRestore = (row: BackupFileResp) => {
  Modal.confirm({
    title: '从备份恢复',
    content: `确定从 ${row.name} 恢复业务数据吗？恢复前会自动备份当前状态。`,
    async onOk() {
      const res = await restoreBackup({ name: row.name, password: backupForm.password, override: true })
      Message.success(`恢复完成：${res.data?.tables?.length ?? 0} 张表 / ${res.data?.statements ?? 0} 条语句`)
      await loadBackups()
    },
  })
}

const onPurge = async () => {
  const res = await purgeBackups()
  Message.success(`已清理 ${res.data ?? 0} 份备份`)
  await loadBackups()
}

onMounted(async () => {
  await Promise.all([loadTables(), loadBackups()])
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" title="数据导入（Excel / CSV，导入前校验 + 错误定位）" style="margin-bottom: 12px">
      <a-space wrap style="margin-bottom: 12px">
        <span>数据类型：</span>
        <a-select v-model="importType" :options="importTypes" style="width: 220px" @change="onImportTypeChange" />
        <a-button v-permission="['monitor:io:import']" :loading="importing" @click="runImport(true)">
          仅校验
        </a-button>
        <a-button v-permission="['monitor:io:import']" type="primary" :loading="importing" @click="runImport(false)">
          执行导入
        </a-button>
      </a-space>
      <a-upload
        draggable
        :custom-request="handleUpload"
        :limit="1"
        :show-retry-button="false"
        :show-cancel-button="false"
        :file-list="uploadFileList"
        accept=".csv, .xlsx, .xls"
        tip="支持 CSV / Excel；历史数据最大 20 万行"
      />
      <a-descriptions v-if="preview" :column="4" size="small" style="margin-top: 12px" bordered>
        <a-descriptions-item label="类型">
          {{ preview.type }}
        </a-descriptions-item>
        <a-descriptions-item label="行数">
          {{ preview.total }}
        </a-descriptions-item>
        <a-descriptions-item label="表头校验">
          <a-tag :color="preview.headersValid ? 'green' : 'red'">
            {{ preview.headersValid ? '通过' : `缺少 ${preview.missingHeaders.join(',')}` }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="表头">
          {{ preview.headers.join(', ') }}
        </a-descriptions-item>
      </a-descriptions>
      <a-descriptions v-if="report" :column="4" size="small" style="margin-top: 12px" bordered>
        <a-descriptions-item label="总数">
          {{ report.total }}
        </a-descriptions-item>
        <a-descriptions-item label="成功">
          {{ report.success }}
        </a-descriptions-item>
        <a-descriptions-item label="跳过">
          {{ report.skipped }}
        </a-descriptions-item>
        <a-descriptions-item label="失败">
          {{ report.failed }}
        </a-descriptions-item>
      </a-descriptions>
      <a-table
        v-if="report?.errors?.length"
        row-key="row"
        :data="report.errors"
        :columns="errorColumns"
        :pagination="false"
        size="small"
        style="margin-top: 12px"
      />
    </a-card>

    <a-card :bordered="false" title="数据导出（流式，内存占用与行数无关）" style="margin-bottom: 12px">
      <a-space wrap>
        <a-input v-model="exportQuery.deviceId" placeholder="设备 ID" style="width: 200px" allow-clear />
        <a-input-number v-model="exportQuery.nodeId" placeholder="节点号" style="width: 130px" />
        <a-input-number v-model="exportQuery.analogNo" placeholder="模拟量" style="width: 130px" />
        <a-range-picker v-model="exportQuery.range" style="width: 260px" />
        <a-button v-permission="['monitor:io:export']" type="primary" @click="onExportCsv">
          导出历史 CSV
        </a-button>
        <a-divider direction="vertical" />
        <a-select v-model="exportTable" :options="tables.map((item: string) => ({ label: item, value: item }))" style="width: 220px" />
        <a-button v-permission="['monitor:io:export']" @click="onExportTable">
          导出整表 SQL
        </a-button>
      </a-space>
    </a-card>

    <a-card :bordered="false" title="备份与恢复" style="margin-bottom: 12px">
      <a-space wrap style="margin-bottom: 12px">
        <a-input-password v-model="backupForm.password" placeholder="加密口令（留空为明文包）" style="width: 240px" allow-clear />
        <a-checkbox v-model="backupForm.includeLogs">
          包含日志
        </a-checkbox>
        <a-button v-permission="['monitor:io:backup']" type="primary" :loading="backupLoading" @click="onCreateBackup">
          一键打包备份
        </a-button>
        <a-button v-permission="['monitor:io:backup']" @click="onPurge">
          清理过期备份
        </a-button>
        <a-button @click="loadBackups">
          刷新
        </a-button>
        <span style="color: var(--color-text-3)">
          目录 {{ backupStatus.dir }}｜保留 {{ backupStatus.keep }} 份｜定时备份 {{ backupStatus.autoEnabled ? `每 ${backupStatus.intervalHours} 小时` : '未启用' }}
        </span>
      </a-space>
      <a-table
        row-key="name"
        :data="backups"
        :columns="backupColumns"
        :pagination="{ pageSize: 10 }"
        size="small"
        :scroll="{ x: '100%', minWidth: 1100 }"
      >
        <template #encrypted="{ record }">
          <a-tag :color="record.encrypted ? 'orange' : 'gray'">
            {{ record.encrypted ? '加密' : '明文' }}
          </a-tag>
        </template>
        <template #action="{ record }">
          <a-space>
            <a-link @click="onDownloadBackup(record.name)">
              下载
            </a-link>
            <a-link @click="onVerify(record)">
              校验
            </a-link>
            <a-link v-permission="['monitor:io:restore']" status="danger" @click="onRestore(record)">
              恢复
            </a-link>
          </a-space>
        </template>
      </a-table>
    </a-card>
  </GiPageLayout>
</template>
