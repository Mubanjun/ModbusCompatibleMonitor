<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { MigrationDetectResp, MigrationImportResp, MigrationVerifyResp } from '@/apis'
import {
  detectSource,
  getMigrationStatus,
  getRollbackGuide,
  importDevices,
  importHistory,
  listPort,
  setMigrationMode,
  verifyMigration,
} from '@/apis'
import { comingSoon } from '@/utils/coming-soon'

defineOptions({ name: 'EnvMigration' })

const form = reactive({
  host: '127.0.0.1',
  port: 3306,
  database: 'rkmonitor',
  username: 'root',
  password: '',
  table: '',
  portId: undefined as string | undefined,
  protocol: 'RS_MODBUS',
  nameColumn: 'DeviceName',
  addrColumn: 'DeviceAddr',
  range: [] as string[],
})

const detect = ref<MigrationDetectResp>()
const deviceResult = ref<MigrationImportResp>()
const historyResult = ref<MigrationImportResp>()
const verifyResult = ref<MigrationVerifyResp>()
const status = ref<Record<string, any>>({})
const guide = ref<string[]>([])
const portOptions = ref<{ label: string, value: string }[]>([])
const busy = ref(false)

const tableColumns: TableInstance['columns'] = [
  { title: '表名', dataIndex: 'name', minWidth: 220, ellipsis: true },
  { title: '行数', dataIndex: 'rows', width: 120, align: 'right' },
  { title: 'DR-03', dataIndex: 'dr03', slotName: 'dr03', width: 90, align: 'center' },
  { title: '疑似设备表', dataIndex: 'deviceTable', slotName: 'deviceTable', width: 110, align: 'center' },
]

const sampleColumns: TableInstance['columns'] = [
  { title: '设备', dataIndex: 'deviceName', minWidth: 160 },
  { title: '节点', dataIndex: 'nodeId', width: 80, align: 'center' },
  { title: '模拟量', dataIndex: 'analogNo', width: 90, align: 'center' },
  { title: '记录时间', dataIndex: 'recordTime', width: 190 },
  { title: '源库值', dataIndex: 'sourceValue', width: 110, align: 'right' },
  { title: '本系统值', dataIndex: 'localValue', width: 110, align: 'right' },
  { title: '一致', dataIndex: 'consistent', slotName: 'consistent', width: 90, align: 'center' },
]

const connection = () => ({
  host: form.host,
  port: form.port,
  database: form.database,
  username: form.username,
  password: form.password,
})

const rangeParam = () => {
  if (form.range?.length === 2) {
    return { startTime: `${form.range[0]} 00:00:00`, endTime: `${form.range[1]} 23:59:59` }
  }
  return {}
}

const loadBase = async () => {
  const [statusRes, guideRes, portRes] = await Promise.all([
    getMigrationStatus(),
    getRollbackGuide(),
    listPort({}),
  ])
  status.value = statusRes.data ?? {}
  guide.value = guideRes.data ?? []
  portOptions.value = (portRes.data ?? []).map((item: any) => ({ label: item.name, value: String(item.id) }))
}

const onDetect = async () => {
  busy.value = true
  try {
    const res = await detectSource(connection())
    detect.value = res.data
    if (detect.value?.connected) {
      Message.success(`已连接 ${detect.value.database}，共 ${detect.value.tableCount} 张表`)
      if (detect.value.deviceTables?.length && !form.table) {
        form.table = detect.value.deviceTables[0]
      }
    } else {
      Message.error(detect.value?.errorMsg ?? '连接失败')
    }
  } finally {
    busy.value = false
  }
}

const onImportDevices = async (dryRun: boolean) => {
  const res = await importDevices({ ...connection(), table: form.table, portId: form.portId, protocol: form.protocol, nameColumn: form.nameColumn, addrColumn: form.addrColumn, dryRun })
  deviceResult.value = res.data
  Message.success(`${dryRun ? '预演' : '导入'}完成：写入 ${res.data?.imported}，失败 ${res.data?.failed}`)
}

const onImportHistory = async (dryRun: boolean) => {
  busy.value = true
  try {
    const res = await importHistory({ ...connection(), ...rangeParam(), dryRun })
    historyResult.value = res.data
    Message.success(`历史迁移${dryRun ? '预演' : ''}：写入 ${res.data?.imported}，跳过 ${res.data?.skipped}，失败 ${res.data?.failed}`)
  } finally {
    busy.value = false
  }
}

const onVerify = async () => {
  busy.value = true
  try {
    const res = await verifyMigration({ ...connection(), ...rangeParam() })
    verifyResult.value = res.data
    Message[res.data?.consistent ? 'success' : 'warning'](res.data?.conclusion ?? '')
  } finally {
    busy.value = false
  }
}

const onMode = (mode: string) => {
  Modal.confirm({
    title: '切换运行模式',
    content: `确认切换为 ${mode}？COEXIST 会在错峰窗口内暂停本系统采集。`,
    async onOk() {
      const res = await setMigrationMode(mode)
      Message.success(`当前模式：${res.data}`)
      await loadBase()
    },
  })
}

onMounted(async () => {
  await loadBase()
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" title="① 连接参数（源库 = 仁科原库）" style="margin-bottom: 12px">
      <a-space wrap>
        <a-input v-model="form.host" placeholder="主机" style="width: 170px" />
        <a-input-number v-model="form.port" placeholder="端口" style="width: 120px" />
        <a-input v-model="form.database" placeholder="库名" style="width: 160px" />
        <a-input v-model="form.username" placeholder="用户名" style="width: 130px" />
        <a-input-password v-model="form.password" placeholder="口令" style="width: 150px" />
        <a-button v-permission="['monitor:migration:detect']" type="primary" :loading="busy" @click="onDetect">
          检测原库
        </a-button>
      </a-space>
      <a-alert v-if="detect && !detect.connected" type="error" style="margin-top: 12px">
        {{ detect.errorMsg }}
      </a-alert>
      <a-table
        v-if="detect?.tables?.length"
        row-key="name"
        :data="detect.tables"
        :columns="tableColumns"
        :pagination="{ pageSize: 8 }"
        size="small"
        style="margin-top: 12px"
      >
        <template #dr03="{ record }">
          <a-tag v-if="record.dr03" color="green">
            DR-03
          </a-tag>
        </template>
        <template #deviceTable="{ record }">
          <a-tag v-if="record.deviceTable && !record.dr03" color="orange">
            设备表
          </a-tag>
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false" title="② 映射设备（配置迁移）" style="margin-bottom: 12px">
      <a-space wrap>
        <a-input v-model="form.table" placeholder="源设备表名" style="width: 200px" />
        <a-select v-model="form.portId" :options="portOptions" placeholder="目标端口" style="width: 200px" />
        <a-select
          v-model="form.protocol"
          :options="[{ label: 'RS_MODBUS', value: 'RS_MODBUS' }, { label: 'MODBUS_RTU', value: 'MODBUS_RTU' }, { label: 'MODBUS_TCP', value: 'MODBUS_TCP' }, { label: 'MODBUS_ASCII', value: 'MODBUS_ASCII' }]"
          style="width: 160px"
        />
        <a-input v-model="form.nameColumn" placeholder="名称列" style="width: 150px" />
        <a-input v-model="form.addrColumn" placeholder="地址列" style="width: 150px" />
        <a-button v-permission="['monitor:migration:import']" :disabled="!form.table || !form.portId" @click="onImportDevices(true)">
          预演
        </a-button>
        <a-button v-permission="['monitor:migration:import']" type="primary" :disabled="!form.table || !form.portId" @click="onImportDevices(false)">
          导入设备
        </a-button>
      </a-space>
      <a-alert v-if="deviceResult" style="margin-top: 12px" type="info">
        扫描 {{ deviceResult.scanned }} 行，写入 {{ deviceResult.imported }}，失败 {{ deviceResult.failed }}
      </a-alert>
    </a-card>

    <a-card :bordered="false" title="③ 导入历史数据" style="margin-bottom: 12px">
      <a-space wrap>
        <a-range-picker v-model="form.range" style="width: 260px" />
        <a-button v-permission="['monitor:migration:import']" :loading="busy" @click="onImportHistory(true)">
          预演
        </a-button>
        <a-button v-permission="['monitor:migration:import']" type="primary" :loading="busy" @click="onImportHistory(false)">
          导入历史
        </a-button>
        <a-button v-permission="['monitor:migration:verify']" :loading="busy" @click="onVerify">
          一致性校验
        </a-button>
      </a-space>
      <a-alert v-if="historyResult" style="margin-top: 12px" type="info">
        扫描 {{ historyResult.scanned }} 行，写入 {{ historyResult.imported }}，跳过 {{ historyResult.skipped }}，失败 {{ historyResult.failed }}
      </a-alert>
      <a-alert v-if="verifyResult" style="margin-top: 12px" :type="verifyResult.consistent ? 'success' : 'warning'">
        {{ verifyResult.conclusion }}
      </a-alert>
      <a-table
        v-if="verifyResult?.samples?.length"
        row-key="recordTime"
        :data="verifyResult.samples"
        :columns="sampleColumns"
        :pagination="{ pageSize: 10 }"
        size="small"
        style="margin-top: 12px"
      >
        <template #consistent="{ record }">
          <a-tag :color="record.consistent ? 'green' : 'red'">
            {{ record.consistent ? '一致' : '不一致' }}
          </a-tag>
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false" title="④ 一键转换（对象模型 → 仁科库）" style="margin-bottom: 12px">
      <a-alert type="info" style="margin-bottom: 12px">
        将本平台对象化存储的传感器与读数，按仁科 `rkmonitor` 库结构生成全量/增量脚本；
        转换规则需业主与第三方取数方签字确认后启用。
      </a-alert>
      <a-space wrap>
        <a-button v-permission="['monitor:migration:switch']" type="primary" @click="comingSoon">
          全量转换并导出 .sql
        </a-button>
        <a-button v-permission="['monitor:migration:switch']" @click="comingSoon">
          增量转换
        </a-button>
        <a-button v-permission="['monitor:migration:verify']" @click="comingSoon">
          转换结果校验报告
        </a-button>
      </a-space>
    </a-card>

    <a-card :bordered="false" title="⑤ 并存与切换 / 回退">
      <a-space wrap style="margin-bottom: 12px">
        <span>当前模式：</span>
        <a-tag color="arcoblue">
          {{ status.mode ?? '-' }}
        </a-tag>
        <a-button v-permission="['monitor:migration:switch']" @click="onMode('COEXIST')">
          并存（错峰运行）
        </a-button>
        <a-button v-permission="['monitor:migration:switch']" type="primary" @click="onMode('SWITCHED')">
          切换到本系统
        </a-button>
        <a-button v-permission="['monitor:migration:switch']" @click="onMode('OFF')">
          退回仅本系统
        </a-button>
        <span style="color: var(--color-text-3)">
          错峰窗口 {{ status.quietWindowStart }}~{{ status.quietWindowEnd }}（{{ status.quietWindowEnabled ? '生效中' : '未启用' }}）
        </span>
      </a-space>
      <a-typography-title :heading="6">
        自包含回退与设备交还（FR-MIG-05）
      </a-typography-title>
      <div style="color: var(--color-text-3)">
        只回退本平台自身的运行模式与数据，并把设备交还现场；<b>不启动、不调用、不依赖任何第三方原软件</b>。
      </div>
      <ol>
        <li v-for="(item, index) in guide" :key="index">
          {{ item }}
        </li>
      </ol>
    </a-card>
  </GiPageLayout>
</template>
