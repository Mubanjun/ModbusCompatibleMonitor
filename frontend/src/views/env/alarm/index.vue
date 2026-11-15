<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1200 }"
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
        <a-select v-model="queryForm.ruleType" placeholder="规则类型" allow-clear style="width: 150px" @change="search">
          <a-option v-for="item in ruleTypes" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-select v-model="queryForm.deviceId" placeholder="所属设备" allow-clear style="width: 180px" @change="search">
          <a-option v-for="device in deviceOptions" :key="device.id" :value="device.id">{{ device.name }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:alarm:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #ruleType="{ record }">
        {{ ruleTypeLabel(record.ruleType) }}
      </template>
      <template #threshold="{ record }">
        {{ thresholdText(record) }}
      </template>
      <template #deviceName="{ record }">
        {{ deviceNameOf(record.deviceId) }}
      </template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">启用</a-tag>
        <a-tag v-else color="gray">停用</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:alarm:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:alarm:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="visible"
      :title="title"
      :width="640"
      draggable
      :mask-closable="false"
      @before-ok="onSubmit"
      @close="onReset"
    >
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="130px">
        <a-form-item label="规则名称" field="name">
          <a-input v-model="form.name" placeholder="如：溶解氧低于 5mg/L" allow-clear />
        </a-form-item>
        <a-form-item label="规则类型" field="ruleType">
          <a-radio-group v-model="form.ruleType">
            <a-radio v-for="item in ruleTypes" :key="item.value" :value="item.value">{{ item.label }}</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="所属设备" field="deviceId">
          <a-select v-model="form.deviceId" placeholder="留空表示按端口或全局" allow-clear>
            <a-option v-for="device in deviceOptions" :key="device.id" :value="device.id">
              {{ device.name }}（从站 {{ device.slaveAddr }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="通道 / 模拟量" field="channelNo">
          <a-space>
            <a-input-number v-model="form.channelNo" :min="1" :max="32" placeholder="1~32" />
            <a-select v-model="form.analogNo" placeholder="模拟量" allow-clear style="width: 120px">
              <a-option :value="1">模拟量1</a-option>
              <a-option :value="2">模拟量2</a-option>
            </a-select>
          </a-space>
        </a-form-item>
        <a-form-item label="要素类型" field="elementType">
          <a-select v-model="form.elementType" placeholder="选填" allow-clear>
            <a-option v-for="item in elementOptions" :key="item.code" :value="item.code">
              {{ item.name }}（{{ item.unit }}）
            </a-option>
          </a-select>
        </a-form-item>

        <template v-if="form.ruleType === 'RANGE'">
          <a-form-item label="上限 / 下限" field="upperLimit">
            <a-space>
              <a-input-number v-model="form.upperLimit" placeholder="上限" />
              <a-input-number v-model="form.lowerLimit" placeholder="下限" />
            </a-space>
            <template #extra>
              <span style="font-size: 12px">至少填写一个；只填上限即上限报警，只填下限即下限报警</span>
            </template>
          </a-form-item>
        </template>
        <a-form-item v-if="form.ruleType === 'DEVIATION'" label="允许偏差" field="deviation">
          <a-input-number v-model="form.deviation" placeholder="允许偏差" />
        </a-form-item>
        <a-form-item v-if="form.ruleType === 'RATE'" label="变化率上限" field="rateLimit">
          <a-input-number v-model="form.rateLimit" placeholder="单位/分钟" />
        </a-form-item>
        <a-form-item v-if="form.ruleType === 'SWITCH'" label="目标状态" field="switchState">
          <a-radio-group v-model="form.switchState">
            <a-radio :value="1">闭合报警</a-radio>
            <a-radio :value="0">断开报警</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="防抖 / 抑制(s)" field="durationS">
          <a-space>
            <a-input-number v-model="form.durationS" :min="0" placeholder="持续多久才报警" />
            <a-input-number v-model="form.suppressWindowS" :min="0" placeholder="抑制窗口" />
          </a-space>
        </a-form-item>
        <a-form-item label="通知方式" field="notifyMethods">
          <a-checkbox-group v-model="notifyList">
            <a-checkbox value="MQTT">MQTT</a-checkbox>
            <a-checkbox value="EMAIL">邮件</a-checkbox>
            <a-checkbox value="SMS">短信</a-checkbox>
          </a-checkbox-group>
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
  type AlarmRuleQuery,
  type AlarmRuleReq,
  type AlarmRuleResp,
  type DeviceResp,
  type ElementTypeItem,
  addAlarmRule,
  deleteAlarmRule,
  listDevice,
  listElementCatalog,
  pageAlarmRule,
  updateAlarmRule,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvAlarm' })

const ruleTypes = [
  { value: 'RANGE', label: '阈值' },
  { value: 'DEVIATION', label: '偏差' },
  { value: 'RATE', label: '变化率' },
  { value: 'SWITCH', label: '开关量' },
]
const ruleTypeLabel = (value: string) => ruleTypes.find((item) => item.value === value)?.label ?? value

const queryForm = reactive<AlarmRuleQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageAlarmRule({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '规则名称', dataIndex: 'name', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '类型', dataIndex: 'ruleType', slotName: 'ruleType', width: 100, align: 'center' },
  { title: '所属设备', dataIndex: 'deviceName', slotName: 'deviceName', width: 150, ellipsis: true, tooltip: true },
  { title: '通道/模拟量', dataIndex: 'channelNo', width: 120, align: 'center', render: ({ record }) => record.channelNo ? `${record.channelNo} / ${record.analogNo ?? '-'}` : '-' },
  { title: '阈值条件', dataIndex: 'threshold', slotName: 'threshold', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '要素', dataIndex: 'elementType', width: 140, ellipsis: true, tooltip: true },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:alarm:update', 'monitor:alarm:delete']),
  },
]

const thresholdText = (record: AlarmRuleResp) => {
  switch (record.ruleType) {
    case 'RANGE':
      return `${record.lowerLimit ?? '-∞'} ~ ${record.upperLimit ?? '+∞'}`
    case 'DEVIATION':
      return `偏差 ±${record.deviation ?? '-'}`
    case 'RATE':
      return `变化率 ≤ ${record.rateLimit ?? '-'}/分钟`
    case 'SWITCH':
      return record.switchState === 1 ? '闭合报警' : '断开报警'
    default:
      return '-'
  }
}

const deviceOptions = ref<DeviceResp[]>([])
const elementOptions = ref<ElementTypeItem[]>([])
const deviceNameOf = (deviceId?: string) =>
  deviceId ? (deviceOptions.value.find((device) => device.id === deviceId)?.name ?? deviceId) : '全部设备'

const notifyList = ref<string[]>([])

const defaultForm = (): AlarmRuleReq => ({
  name: '',
  ruleType: 'RANGE',
  deviceId: undefined,
  channelNo: undefined,
  analogNo: undefined,
  elementType: undefined,
  upperLimit: undefined,
  lowerLimit: undefined,
  deviation: undefined,
  rateLimit: undefined,
  switchState: undefined,
  durationS: 0,
  suppressWindowS: 0,
  notifyMethods: undefined,
  enabled: true,
  description: '',
})

const form = reactive<AlarmRuleReq>(defaultForm())

// 加载设备与要素字典
const loadOptions = async () => {
  const [deviceRes, elementRes] = await Promise.all([listDevice({ page: 1, size: 500 }), listElementCatalog()])
  deviceOptions.value = Array.isArray(deviceRes.data) ? deviceRes.data : (deviceRes.data.list ?? [])
  elementOptions.value = elementRes.data ?? []
}

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.ruleType = undefined
  queryForm.deviceId = undefined
  search()
}

// 删除
const onDelete = (record: AlarmRuleResp) => {
  return handleDelete(() => deleteAlarmRule(record.id), {
    content: `是否确定删除报警规则「${record.name}」？`,
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增报警规则')
const formRef = ref()
const currentId = ref('')

const rules = {
  name: [{ required: true, message: '请输入规则名称' }],
  ruleType: [{ required: true, message: '请选择规则类型' }],
}

// 重置表单
const onReset = () => {
  Object.assign(form, defaultForm())
  notifyList.value = []
  currentId.value = ''
  formRef.value?.clearValidate?.()
}

// 新增
const onAdd = () => {
  onReset()
  title.value = '新增报警规则'
  visible.value = true
}

// 修改
const onUpdate = (record: AlarmRuleResp) => {
  onReset()
  Object.assign(form, record)
  notifyList.value = record.notifyMethods ? record.notifyMethods.split(',').filter(Boolean) : []
  currentId.value = record.id
  title.value = '修改报警规则'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  form.notifyMethods = notifyList.value.length ? notifyList.value.join(',') : undefined
  if (currentId.value) {
    await updateAlarmRule(form, currentId.value)
  } else {
    await addAlarmRule(form)
  }
  Message.success('保存成功')
  search()
  return true
}

onMounted(loadOptions)
</script>
