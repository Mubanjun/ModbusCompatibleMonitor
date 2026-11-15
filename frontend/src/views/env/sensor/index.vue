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
          placeholder="搜索传感器/要素"
          allow-clear
          style="width: 240px"
          @search="search"
        />
        <a-tree-select
          v-model="queryForm.groupId"
          :data="groupOptions"
          placeholder="所属分组"
          allow-clear
          style="width: 200px"
          @change="search"
        />
        <a-button @click="reset">重置</a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:sensor:create']" type="primary" @click="onAdd">新增传感器</a-button>
      </template>
      <template #groupName="{ record }">{{ groupNameOf(record.groupId) }}</template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">启用</a-tag>
        <a-tag v-else color="gray">停用</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:sensor:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:sensor:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>
    <a-modal v-model:visible="visible" :title="title" :width="620" draggable :mask-closable="false" @before-ok="onSubmit" @close="onReset">
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="110px">
        <a-form-item label="传感器名称" field="name">
          <a-input v-model="form.name" placeholder="如：进水口 pH" allow-clear />
        </a-form-item>
        <a-form-item label="所属分组" field="groupId">
          <a-tree-select v-model="form.groupId" :data="groupOptions" placeholder="选择分组" allow-clear />
        </a-form-item>
        <a-form-item label="所属端口" field="portId">
          <a-select v-model="form.portId" placeholder="选择端口">
            <a-option v-for="port in portOptions" :key="port.id" :value="port.id">{{ port.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="所属设备" field="deviceId">
          <a-select v-model="form.deviceId" placeholder="直连传感器可留空" allow-clear>
            <a-option v-for="device in deviceOptions" :key="device.id" :value="device.id">{{ device.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="通道/模拟量" field="channelNo">
          <a-space>
            <a-input-number v-model="form.channelNo" :min="1" :max="32" placeholder="通道" />
            <a-input-number v-model="form.analogNo" :min="1" :max="2" placeholder="模拟量" />
          </a-space>
        </a-form-item>
        <a-form-item label="要素编码" field="elementCode">
          <a-input v-model="form.elementCode" placeholder="如 pH / TEMP" allow-clear />
        </a-form-item>
        <a-form-item label="单位" field="unit">
          <a-input v-model="form.unit" placeholder="如 pH / ℃" allow-clear />
        </a-form-item>
        <a-form-item label="系数/偏移" field="coefficient">
          <a-space>
            <a-input-number v-model="form.coefficient" placeholder="系数" />
            <a-input-number v-model="form.offsetValue" placeholder="偏移" />
          </a-space>
        </a-form-item>
        <a-form-item label="量程/精度" field="rangeMin">
          <a-space>
            <a-input-number v-model="form.rangeMin" placeholder="下限" />
            <a-input-number v-model="form.rangeMax" placeholder="上限" />
            <a-input-number v-model="form.precision" :min="0" :max="9" placeholder="精度" />
          </a-space>
        </a-form-item>
        <a-form-item label="是否启用" field="enabled"><a-switch v-model="form.enabled" /></a-form-item>
      </a-form>
    </a-modal>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import {
  type DeviceGroupNode,
  type DeviceResp,
  type PortResp,
  type SensorQuery,
  type SensorReq,
  type SensorResp,
  addSensor,
  deleteSensor,
  listDevice,
  listPort,
  pageSensor,
  treeDeviceGroup,
  updateSensor,
} from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvSensor' })

const queryForm = reactive<SensorQuery>({})
const { tableData: dataList, loading, pagination, search, handleDelete } = useTable((page) =>
  pageSensor({ ...queryForm, ...page }),
)

const columns: TableInstance['columns'] = [
  { title: '传感器名称', dataIndex: 'name', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '分组', dataIndex: 'groupName', slotName: 'groupName', width: 140 },
  { title: '要素编码', dataIndex: 'elementCode', width: 120 },
  { title: '单位', dataIndex: 'unit', width: 80 },
  { title: '系数', dataIndex: 'coefficient', width: 90 },
  { title: '偏移', dataIndex: 'offsetValue', width: 90 },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', dataIndex: 'action', slotName: 'action', width: 130, align: 'center' },
]

const groups = ref<DeviceGroupNode[]>([])
const portOptions = ref<PortResp[]>([])
const deviceOptions = ref<DeviceResp[]>([])
const visible = ref(false)
const title = ref('新增传感器')
const formRef = ref()
const currentId = ref('')
const form = reactive<SensorReq>({
  name: '',
  groupId: '1',
  portId: '',
  elementCode: '',
  coefficient: 1,
  offsetValue: 0,
  enabled: true,
})

const flattenGroups = (nodes: DeviceGroupNode[]): DeviceGroupNode[] =>
  nodes.flatMap((node) => [node, ...flattenGroups(node.children ?? [])])
const toTreeData = (nodes: DeviceGroupNode[]): any[] =>
  nodes.map((node) => ({ key: node.id, title: node.name, children: toTreeData(node.children ?? []) }))
const groupOptions = computed(() => toTreeData(groups.value))
const groupNameOf = (id: string) => flattenGroups(groups.value).find((item) => item.id === id)?.name ?? id

const loadOptions = async () => {
  const [groupRes, portRes, deviceRes] = await Promise.all([
    treeDeviceGroup(),
    listPort({ page: 1, size: 500 }),
    listDevice(),
  ])
  groups.value = groupRes.data ?? []
  portOptions.value = Array.isArray(portRes.data) ? portRes.data : (portRes.data?.list ?? [])
  deviceOptions.value = deviceRes.data ?? []
}

const reset = () => {
  queryForm.description = undefined
  queryForm.groupId = undefined
  search()
}

const onReset = () => {
  Object.assign(form, {
    name: '',
    groupId: '1',
    portId: '',
    deviceId: undefined,
    channelNo: undefined,
    analogNo: undefined,
    elementCode: '',
    unit: '',
    rangeMin: undefined,
    rangeMax: undefined,
    coefficient: 1,
    offsetValue: 0,
    precision: undefined,
    enabled: true,
  })
  currentId.value = ''
  formRef.value?.clearValidate?.()
}

const onAdd = () => {
  onReset()
  title.value = '新增传感器'
  visible.value = true
}

const onUpdate = (record: SensorResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改传感器'
  visible.value = true
}

const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) return false
  if (currentId.value) {
    await updateSensor(form, currentId.value)
  } else {
    await addSensor(form)
  }
  Message.success('保存成功')
  visible.value = false
  search()
  return true
}

const onDelete = (record: SensorResp) =>
  handleDelete(() => deleteSensor(record.id), {
    content: `是否删除传感器「${record.name}」？`,
    showModal: true,
  })

const rules = {
  name: [{ required: true, message: '请输入传感器名称' }],
  portId: [{ required: true, message: '请选择端口' }],
  elementCode: [{ required: true, message: '请输入要素编码' }],
}

onMounted(loadOptions)
</script>
