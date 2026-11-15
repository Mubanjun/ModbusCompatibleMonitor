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
          placeholder="搜索规则名称"
          allow-clear
          style="width: 220px"
          @search="search"
        />
        <a-select v-model="queryForm.targetId" placeholder="上报目标" allow-clear style="width: 200px" @change="search">
          <a-option v-for="item in targetOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:reportRule:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #targetName="{ record }">
        {{ targetNameOf(record.targetId) }}
      </template>
      <template #templateName="{ record }">
        {{ record.templateId ? templateNameOf(record.templateId) : '目标默认' }}
      </template>
      <template #scope="{ record }">
        {{ scopeText(record) }}
      </template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">启用</a-tag>
        <a-tag v-else color="gray">停用</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:reportRule:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:reportRule:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="visible"
      :title="title"
      :width="620"
      draggable
      :mask-closable="false"
      @before-ok="onSubmit"
      @close="onReset"
    >
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="130px">
        <a-form-item label="规则名称" field="name">
          <a-input v-model="form.name" placeholder="如：进水口数据上报" allow-clear />
        </a-form-item>
        <a-form-item label="上报目标" field="targetId">
          <a-select v-model="form.targetId" placeholder="请选择上报目标" allow-clear>
            <a-option v-for="item in targetOptions" :key="item.id" :value="item.id">
              {{ item.name }}（{{ item.targetType }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="上报模板" field="templateId">
          <a-select v-model="form.templateId" placeholder="留空则使用目标默认格式" allow-clear>
            <a-option v-for="item in templateOptions" :key="item.id" :value="item.id">
              {{ item.name }}（{{ item.code }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="限定范围" field="portId">
          <a-space>
            <a-select v-model="form.portId" placeholder="全部端口" allow-clear style="width: 180px" @change="onPortChange">
              <a-option v-for="item in portOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
            </a-select>
            <a-select v-model="form.deviceId" placeholder="全部设备" allow-clear style="width: 200px">
              <a-option v-for="item in deviceOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
            </a-select>
          </a-space>
          <template #extra>
            <span style="font-size: 12px">都留空表示全量上报；选择设备时要求设备属于所选端口</span>
          </template>
        </a-form-item>
        <a-form-item label="上报间隔(ms)" field="intervalMs">
          <a-input-number v-model="form.intervalMs" :min="1000" placeholder="默认 60000" />
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
  type DeviceResp,
  type PortResp,
  type ReportRuleQuery,
  type ReportRuleReq,
  type ReportRuleResp,
  type ReportTargetResp,
  type ReportTemplateResp,
  addReportRule,
  deleteReportRule,
  listDevice,
  listPort,
  listReportTarget,
  listReportTemplate,
  pageReportRule,
  updateReportRule,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvReportRule' })

const queryForm = reactive<ReportRuleQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageReportRule({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '规则名称', dataIndex: 'name', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '上报目标', dataIndex: 'targetName', slotName: 'targetName', minWidth: 170, ellipsis: true, tooltip: true },
  { title: '上报模板', dataIndex: 'templateName', slotName: 'templateName', minWidth: 170, ellipsis: true, tooltip: true },
  { title: '限定范围', dataIndex: 'scope', slotName: 'scope', minWidth: 200, ellipsis: true, tooltip: true },
  { title: '间隔(ms)', dataIndex: 'intervalMs', width: 110, align: 'center' },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:reportRule:update', 'monitor:reportRule:delete']),
  },
]

const defaultForm = (): ReportRuleReq => ({
  name: '',
  targetId: undefined,
  templateId: undefined,
  portId: undefined,
  deviceId: undefined,
  intervalMs: 60000,
  enabled: true,
  description: '',
})

const form = reactive<ReportRuleReq>(defaultForm())

const targetOptions = ref<ReportTargetResp[]>([])
const templateOptions = ref<ReportTemplateResp[]>([])
const portOptions = ref<PortResp[]>([])
const deviceOptions = ref<DeviceResp[]>([])
const targetNameOf = (id: string) => targetOptions.value.find((item) => item.id === id)?.name ?? id
const templateNameOf = (id: string) => templateOptions.value.find((item) => item.id === id)?.name ?? id
const portNameOf = (id: string) => portOptions.value.find((item) => item.id === id)?.name ?? id
const deviceNameOf = (id: string) => deviceOptions.value.find((item) => item.id === id)?.name ?? id
const scopeText = (record: ReportRuleResp) => {
  if (!record.portId && !record.deviceId) {
    return '全部端口 / 全部设备'
  }
  const port = record.portId ? portNameOf(record.portId) : '全部端口'
  const device = record.deviceId ? deviceNameOf(record.deviceId) : '全部设备'
  return `${port} / ${device}`
}

// 加载下拉选项
const loadOptions = async () => {
  const [targetRes, templateRes, portRes, deviceRes] = await Promise.all([
    listReportTarget({ page: 1, size: 200 }),
    listReportTemplate({ page: 1, size: 200 }),
    listPort({ page: 1, size: 200 }),
    listDevice({ page: 1, size: 500 }),
  ])
  const pick = (res: any) => (Array.isArray(res.data) ? res.data : (res.data.list ?? []))
  targetOptions.value = pick(targetRes)
  templateOptions.value = pick(templateRes)
  portOptions.value = pick(portRes)
  deviceOptions.value = pick(deviceRes)
}

// 切换端口时清空设备
const onPortChange = () => {
  form.deviceId = undefined
}

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.targetId = undefined
  search()
}

// 删除
const onDelete = (record: ReportRuleResp) => {
  return handleDelete(() => deleteReportRule(record.id), {
    content: `是否确定删除上报规则「${record.name}」？`,
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增上报规则')
const formRef = ref()
const currentId = ref('')

const rules = {
  name: [{ required: true, message: '请输入规则名称' }],
  targetId: [{ required: true, message: '请选择上报目标' }],
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
  title.value = '新增上报规则'
  visible.value = true
}

// 修改
const onUpdate = (record: ReportRuleResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改上报规则'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateReportRule(form, currentId.value)
  } else {
    await addReportRule(form)
  }
  Message.success('保存成功')
  search()
  return true
}

onMounted(loadOptions)
</script>
