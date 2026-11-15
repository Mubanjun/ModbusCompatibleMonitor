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
          placeholder="搜索要素名称/单位/描述"
          allow-clear
          style="width: 240px"
          @search="search"
        />
        <a-select v-model="queryForm.deviceId" placeholder="所属设备" allow-clear style="width: 200px" @change="search">
          <a-option v-for="device in deviceOptions" :key="device.id" :value="device.id">{{ device.name }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:channel:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
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
          <a-link v-permission="['monitor:channel:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:channel:delete']" status="danger" @click="onDelete(record)">删除</a-link>
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
        <a-form-item label="所属设备" field="deviceId">
          <a-select v-model="form.deviceId" placeholder="请选择设备" allow-clear>
            <a-option v-for="device in deviceOptions" :key="device.id" :value="device.id">
              {{ device.name }}（从站 {{ device.slaveAddr }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="通道号 / 模拟量" field="channelNo">
          <a-space>
            <a-input-number v-model="form.channelNo" :min="1" :max="32" placeholder="1~32" />
            <a-select v-model="form.analogNo" style="width: 120px">
              <a-option :value="1">模拟量1</a-option>
              <a-option :value="2">模拟量2</a-option>
            </a-select>
          </a-space>
        </a-form-item>
        <a-form-item label="要素类型" field="elementType">
          <a-select v-model="form.elementType" placeholder="选择后自动带出名称与单位" allow-clear @change="onElementChange">
            <a-option v-for="item in elementOptions" :key="item.code" :value="item.code">
              {{ item.name }}（{{ item.unit }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="量程档位" field="rangeLabel">
          <a-select
            v-model="form.rangeLabel"
            placeholder="选择后自动带出编码方式、系数 A 与量程"
            allow-clear
            :disabled="!form.elementType"
          >
            <a-option v-for="range in currentRanges" :key="range.label" :value="range.label">
              {{ range.label }}
            </a-option>
          </a-select>
          <template #extra>
            <span style="font-size: 12px">档位决定寄存器编码与系数，由后端按要素字典自动填充</span>
          </template>
        </a-form-item>
        <a-form-item label="数据属性" field="dataType">
          <a-select v-model="form.dataType">
            <a-option value="ANALOG2_ONLY">模拟量2启用 / 模拟量1禁用</a-option>
            <a-option value="ANALOG1_ONLY">模拟量1启用 / 模拟量2禁用</a-option>
            <a-option value="BOTH">两个模拟量都启用</a-option>
            <a-option value="FLOAT32">浮点型数据</a-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="!form.rangeLabel" label="编码方式" field="encoding">
          <a-select v-model="form.encoding" placeholder="不套用档位时需手工选择">
            <a-option v-for="item in encodingOptions" :key="item" :value="item">{{ item }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="寄存器地址" field="regAddr">
          <a-input-number v-model="form.regAddr" :min="0" :max="65535" placeholder="0 = 模拟量2，1 = 模拟量1" />
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
  type ChannelQuery,
  type ChannelReq,
  type ChannelResp,
  type DeviceResp,
  type ElementTypeItem,
  addChannel,
  deleteChannel,
  listDevice,
  listElementCatalog,
  pageChannel,
  updateChannel,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvChannel' })

const encodingOptions = [
  'UINT16_SCALE_1',
  'UINT16_SCALE_10',
  'UINT16_SCALE_100',
  'UINT16_SCALE_1000',
  'INT16_SCALE_1',
  'INT16_SCALE_10',
  'INT16_SCALE_100',
  'UINT32_SCALE_1',
  'UINT32_SCALE_10',
  'UINT32_SCALE_100',
  'FLOAT32_BIG_ENDIAN',
]

const queryForm = reactive<ChannelQuery>({
  sort: ['deviceId,asc', 'channelNo,asc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageChannel({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '所属设备', dataIndex: 'deviceName', slotName: 'deviceName', width: 160, ellipsis: true, tooltip: true },
  { title: '通道', dataIndex: 'channelNo', width: 80, align: 'center' },
  { title: '模拟量', dataIndex: 'analogNo', width: 90, align: 'center' },
  { title: '要素', dataIndex: 'elementName', width: 120, ellipsis: true, tooltip: true },
  { title: '单位', dataIndex: 'unit', width: 90, align: 'center' },
  { title: '编码方式', dataIndex: 'encoding', minWidth: 160, ellipsis: true, tooltip: true },
  { title: '系数 A', dataIndex: 'coefficient', width: 100, align: 'center' },
  { title: '量程', dataIndex: 'maxValue', width: 130, align: 'center', render: ({ record }) => `${record.minValue ?? '-'} ~ ${record.maxValue ?? '-'}` },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:channel:update', 'monitor:channel:delete']),
  },
]

const deviceOptions = ref<DeviceResp[]>([])
const elementOptions = ref<ElementTypeItem[]>([])

const defaultForm = (): ChannelReq => ({
  deviceId: '',
  channelNo: 1,
  analogNo: 2,
  elementType: undefined,
  rangeLabel: undefined,
  dataType: 'ANALOG2_ONLY',
  encoding: 'UINT16_SCALE_1',
  regAddr: 0,
  enabled: true,
  description: '',
})

const form = reactive<ChannelReq>(defaultForm())
const deviceNameOf = (deviceId: string) => deviceOptions.value.find((device) => device.id === deviceId)?.name ?? deviceId
const currentRanges = computed(() => elementOptions.value.find((item) => item.code === form.elementType)?.ranges ?? [])

// 加载设备与要素字典
const loadOptions = async () => {
  const [deviceRes, elementRes] = await Promise.all([listDevice({ page: 1, size: 500 }), listElementCatalog()])
  deviceOptions.value = Array.isArray(deviceRes.data) ? deviceRes.data : (deviceRes.data.list ?? [])
  elementOptions.value = elementRes.data ?? []
}

// 切换要素时清空档位
const onElementChange = () => {
  form.rangeLabel = undefined
}

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.deviceId = undefined
  search()
}

// 删除
const onDelete = (record: ChannelResp) => {
  return handleDelete(() => deleteChannel(record.id), {
    content: `是否确定删除该通道配置？`,
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增通道')
const formRef = ref()
const currentId = ref('')

const rules = {
  deviceId: [{ required: true, message: '请选择所属设备' }],
  channelNo: [{ required: true, message: '请填写通道号' }],
  analogNo: [{ required: true, message: '请选择模拟量' }],
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
  title.value = '新增通道'
  visible.value = true
}

// 修改
const onUpdate = (record: ChannelResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改通道'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateChannel(form, currentId.value)
  } else {
    await addChannel(form)
  }
  Message.success('保存成功')
  search()
  return true
}

onMounted(loadOptions)
</script>
