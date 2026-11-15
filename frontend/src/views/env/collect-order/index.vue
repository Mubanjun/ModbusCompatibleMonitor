<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 900 }"
      @refresh="search"
    >
      <template #toolbar-left>
        <a-select v-model="queryForm.portId" placeholder="所属端口" allow-clear style="width: 200px" @change="search">
          <a-option v-for="item in portOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:collectOrder:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #portName="{ record }">
        {{ portNameOf(record.portId) }}
      </template>
      <template #deviceName="{ record }">
        {{ deviceNameOf(record.deviceId) }}
      </template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">参与</a-tag>
        <a-tag v-else color="gray">跳过</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:collectOrder:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:collectOrder:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="visible"
      :title="title"
      :width="560"
      draggable
      :mask-closable="false"
      @before-ok="onSubmit"
      @close="onReset"
    >
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="130px">
        <a-form-item label="所属端口" field="portId">
          <a-select v-model="form.portId" placeholder="请选择端口" allow-clear @change="onPortChange">
            <a-option v-for="item in portOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="设备" field="deviceId">
          <a-select v-model="form.deviceId" placeholder="先选择端口" allow-clear :disabled="!form.portId">
            <a-option v-for="item in filteredDevices" :key="item.id" :value="item.id">
              {{ item.name }}（从站 {{ item.slaveAddr }}）
            </a-option>
          </a-select>
          <template #extra>
            <span style="font-size: 12px">只能选择该端口下的设备</span>
          </template>
        </a-form-item>
        <a-form-item label="采集顺序" field="sortNo">
          <a-input-number v-model="form.sortNo" :min="0" :max="9999" placeholder="数字越小越先轮询" />
        </a-form-item>
        <a-form-item label="是否参与" field="enabled">
          <a-switch v-model="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import {
  type CollectOrderQuery,
  type CollectOrderReq,
  type CollectOrderResp,
  type DeviceResp,
  type PortResp,
  addCollectOrder,
  deleteCollectOrder,
  listDevice,
  listPort,
  pageCollectOrder,
  updateCollectOrder,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvCollectOrder' })

const queryForm = reactive<CollectOrderQuery>({
  sort: ['portId,asc', 'sortNo,asc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageCollectOrder({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '所属端口', dataIndex: 'portName', slotName: 'portName', minWidth: 160, ellipsis: true, tooltip: true },
  { title: '设备', dataIndex: 'deviceName', slotName: 'deviceName', minWidth: 200, ellipsis: true, tooltip: true },
  { title: '采集顺序', dataIndex: 'sortNo', width: 110, align: 'center' },
  { title: '是否参与', dataIndex: 'enabled', slotName: 'enabled', width: 100, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:collectOrder:update', 'monitor:collectOrder:delete']),
  },
]

const defaultForm = (): CollectOrderReq => ({
  portId: undefined,
  deviceId: undefined,
  sortNo: 999,
  enabled: true,
})

const form = reactive<CollectOrderReq>(defaultForm())

const portOptions = ref<PortResp[]>([])
const deviceOptions = ref<DeviceResp[]>([])
const portNameOf = (id: string) => portOptions.value.find((item) => item.id === id)?.name ?? id
const deviceNameOf = (id: string) => deviceOptions.value.find((item) => item.id === id)?.name ?? id
const filteredDevices = computed(() =>
  form.portId ? deviceOptions.value.filter((item) => item.portId === form.portId) : [],
)

// 加载端口与设备
const loadOptions = async () => {
  const [portRes, deviceRes] = await Promise.all([listPort({ page: 1, size: 200 }), listDevice({ page: 1, size: 500 })])
  const pick = (res: any) => (Array.isArray(res.data) ? res.data : (res.data.list ?? []))
  portOptions.value = pick(portRes)
  deviceOptions.value = pick(deviceRes)
}

// 切换端口时清空设备
const onPortChange = () => {
  form.deviceId = undefined
}

// 重置
const reset = () => {
  queryForm.portId = undefined
  search()
}

// 删除
const onDelete = (record: CollectOrderResp) => {
  return handleDelete(() => deleteCollectOrder(record.id), {
    content: '是否确定删除该采集顺序配置？',
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增采集顺序')
const formRef = ref()
const currentId = ref('')

const rules = {
  portId: [{ required: true, message: '请选择端口' }],
  deviceId: [{ required: true, message: '请选择设备' }],
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
  title.value = '新增采集顺序'
  visible.value = true
}

// 修改
const onUpdate = (record: CollectOrderResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改采集顺序'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateCollectOrder(form, currentId.value)
  } else {
    await addCollectOrder(form)
  }
  Message.success('保存成功')
  search()
  return true
}

onMounted(loadOptions)
</script>
