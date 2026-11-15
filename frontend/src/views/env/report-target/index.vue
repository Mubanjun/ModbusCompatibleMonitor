<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1100 }"
      @refresh="search"
    >
      <template #toolbar-left>
        <a-input-search
          v-model="queryForm.description"
          placeholder="搜索目标名称"
          allow-clear
          style="width: 220px"
          @search="search"
        />
        <a-select v-model="queryForm.targetType" placeholder="目标类型" allow-clear style="width: 150px" @change="search">
          <a-option v-for="item in targetTypes" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:reportTarget:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #targetType="{ record }">
        {{ targetTypeLabel(record.targetType) }}
      </template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">启用</a-tag>
        <a-tag v-else color="gray">停用</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:reportTarget:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:reportTarget:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="visible"
      :title="title"
      :width="660"
      draggable
      :mask-closable="false"
      @before-ok="onSubmit"
      @close="onReset"
    >
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="130px">
        <a-form-item label="目标名称" field="name">
          <a-input v-model="form.name" placeholder="如：厂区 MQTT Broker" allow-clear />
        </a-form-item>
        <a-form-item label="目标类型" field="targetType">
          <a-radio-group v-model="form.targetType">
            <a-radio v-for="item in targetTypes" :key="item.value" :value="item.value">{{ item.label }}</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="form.targetType === 'DB'" label="数据库方言" field="dbType">
          <a-select v-model="form.dbType" placeholder="请选择数据库方言" allow-clear>
            <a-option v-for="item in dbTypes" :key="item" :value="item">{{ item }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="连接参数" field="configJson">
          <a-textarea v-model="form.configJson" :auto-size="{ minRows: 4 }" :placeholder="configPlaceholder" />
          <template #extra>
            <span style="font-size: 12px">JSON 格式；MQTT 填 host/port/topic，数据库填 url/database/username/password</span>
          </template>
        </a-form-item>
        <a-form-item label="报文格式" field="formatType">
          <a-select v-model="form.formatType">
            <a-option value="JSON">JSON</a-option>
            <a-option value="KV">键值对</a-option>
            <a-option value="CUSTOM">自定义模板</a-option>
          </a-select>
        </a-form-item>
        <template v-if="form.targetType === 'MQTT'">
          <a-form-item label="QoS / 保留" field="mqttQos">
            <a-space>
              <a-select v-model="form.mqttQos" placeholder="QoS" allow-clear style="width: 120px">
                <a-option :value="0">0</a-option>
                <a-option :value="1">1</a-option>
                <a-option :value="2">2</a-option>
              </a-select>
              <a-switch v-model="form.mqttRetain" />
              <span style="font-size: 12px">保留标志</span>
            </a-space>
          </a-form-item>
        </template>
        <a-form-item label="重试次数" field="retryCount">
          <a-input-number v-model="form.retryCount" :min="0" :max="10" placeholder="默认 3" />
        </a-form-item>
        <a-form-item label="重试间隔(ms)" field="retryIntervalMs">
          <a-input-number v-model="form.retryIntervalMs" :min="100" placeholder="默认 5000" />
        </a-form-item>
        <a-form-item label="超时(ms)" field="timeoutMs">
          <a-input-number v-model="form.timeoutMs" :min="100" placeholder="默认 10000" />
        </a-form-item>
        <a-form-item label="是否启用" field="enabled">
          <a-switch v-model="form.enabled" />
        </a-form-item>
        <a-form-item label="描述" field="description">
          <a-textarea v-model="form.description" placeholder="选填" :max-length="200" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import {
  type ReportTargetQuery,
  type ReportTargetReq,
  type ReportTargetResp,
  addReportTarget,
  deleteReportTarget,
  pageReportTarget,
  updateReportTarget,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvReportTarget' })

const targetTypes = [
  { value: 'MQTT', label: 'MQTT' },
  { value: 'DB', label: '数据库' },
  { value: 'HTTP', label: 'HTTP' },
]
const dbTypes = ['MYSQL', 'MARIADB', 'POSTGRESQL', 'SQLSERVER', 'KINGBASE', 'ORACLE', 'DM']
const targetTypeLabel = (value: string) => targetTypes.find((item) => item.value === value)?.label ?? value

const queryForm = reactive<ReportTargetQuery>({
  sort: ['sort,asc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageReportTarget({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '目标名称', dataIndex: 'name', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '类型', dataIndex: 'targetType', slotName: 'targetType', width: 110, align: 'center' },
  { title: '数据库方言', dataIndex: 'dbType', width: 130, align: 'center', render: ({ record }) => record.dbType || '-' },
  { title: '报文格式', dataIndex: 'formatType', width: 110, align: 'center' },
  { title: '重试', dataIndex: 'retryCount', width: 80, align: 'center' },
  { title: '超时(ms)', dataIndex: 'timeoutMs', width: 100, align: 'center' },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:reportTarget:update', 'monitor:reportTarget:delete']),
  },
]

const defaultForm = (): ReportTargetReq => ({
  name: '',
  targetType: 'MQTT',
  dbType: undefined,
  configJson: '',
  formatType: 'JSON',
  mqttQos: undefined,
  mqttRetain: false,
  retryCount: 3,
  retryIntervalMs: 5000,
  timeoutMs: 10000,
  enabled: true,
  description: '',
  sort: 999,
})

const form = reactive<ReportTargetReq>(defaultForm())

// 连接参数占位提示（含双引号，放在 JS 里避免模板属性引号冲突）
const configPlaceholder = '如 {"host":"192.168.1.10","port":1883,"topic":"jdrk/data"}'

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.targetType = undefined
  search()
}

// 删除
const onDelete = (record: ReportTargetResp) => {
  return handleDelete(() => deleteReportTarget(record.id), {
    content: `是否确定删除上报目标「${record.name}」？`,
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增上报目标')
const formRef = ref()
const currentId = ref('')

const rules = {
  name: [{ required: true, message: '请输入目标名称' }],
  targetType: [{ required: true, message: '请选择目标类型' }],
  configJson: [{ required: true, message: '请输入连接参数' }],
}

// 重置表单
const onReset = () => {
  Object.assign(form, defaultForm())
  currentId.value = ''
  formRef.value?.clearValidate?.()
}

// 新增
const onAdd = () => {
  onReset()
  title.value = '新增上报目标'
  visible.value = true
}

// 修改
const onUpdate = (record: ReportTargetResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改上报目标'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateReportTarget(form, currentId.value)
  } else {
    await addReportTarget(form)
  }
  Message.success('保存成功')
  search()
  return true
}
</script>
