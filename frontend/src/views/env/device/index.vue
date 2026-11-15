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
          placeholder="搜索设备名称/型号/位置"
          allow-clear
          style="width: 240px"
          @search="search"
        />
        <a-select v-model="queryForm.portId" placeholder="所属端口" allow-clear style="width: 180px" @change="search">
          <a-option v-for="port in portOptions" :key="port.id" :value="port.id">{{ port.name }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:device:create']" @click="comingSoon">
          <template #icon><icon-copy /></template>
          <template #default>批量快速新增</template>
        </a-button>
        <a-button v-permission="['monitor:device:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #portName="{ record }">
        {{ portNameOf(record.portId) }}
      </template>
      <template #groupName="{ record }">{{ groupNameOf(record.groupId) }}</template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">启用</a-tag>
        <a-tag v-else color="gray">停用</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:device:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:timeSync:sync']" @click="onSyncTime(record)">校时</a-link>
          <a-link v-permission="['monitor:device:delete']" status="danger" @click="onDelete(record)">删除</a-link>
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
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="120px">
        <a-form-item label="所属端口" field="portId">
          <a-select v-model="form.portId" placeholder="请选择端口" allow-clear>
            <a-option v-for="port in portOptions" :key="port.id" :value="port.id">
              {{ port.name }}（上限 {{ port.deviceLimit }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="设备名称" field="name">
          <a-input v-model="form.name" placeholder="如：1 号水质传感器" allow-clear />
        </a-form-item>
        <a-form-item label="从站地址" field="slaveAddr">
          <a-input-number v-model="form.slaveAddr" :min="1" :max="255" placeholder="1~255，同端口内唯一" />
        </a-form-item>
        <a-form-item label="协议" field="protocol">
          <a-select v-model="form.protocol">
            <a-option value="RS_MODBUS">仁科 RS-Modbus</a-option>
            <a-option value="MODBUS_RTU">标准 Modbus RTU</a-option>
            <a-option value="MODBUS_TCP">Modbus TCP</a-option>
            <a-option value="MODBUS_ASCII">Modbus ASCII（可选）</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="设备型号" field="model">
          <a-input v-model="form.model" placeholder="如：RS-EC-N01-2" allow-clear />
        </a-form-item>
        <a-form-item label="所属分组" field="groupId">
          <a-tree-select v-model="form.groupId" :data="groupOptions" placeholder="选择分组" allow-clear />
        </a-form-item>
        <a-form-item label="安装位置" field="location">
          <a-input v-model="form.location" placeholder="选填" allow-clear />
        </a-form-item>
        <a-form-item label="采集间隔(ms)" field="intervalMs">
          <a-input-number v-model="form.intervalMs" :min="1000" placeholder="留空则继承端口/全局" />
        </a-form-item>
        <a-form-item label="单帧超时(ms)" field="timeoutMs">
          <a-input-number v-model="form.timeoutMs" :min="100" :max="60000" placeholder="默认 1000" />
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
import { Message, Modal } from '@arco-design/web-vue'
import {
  type DeviceGroupNode,
  type DeviceQuery,
  type DeviceReq,
  type DeviceResp,
  type PortResp,
  addDevice,
  deleteDevice,
  listPort,
  pageDevice,
  syncDeviceTime,
  treeDeviceGroup,
  updateDevice,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import { comingSoon } from '@/utils/coming-soon'
import has from '@/utils/has'

defineOptions({ name: 'EnvDevice' })

const queryForm = reactive<DeviceQuery>({
  sort: ['portId,asc', 'sortNo,asc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageDevice({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '所属端口', dataIndex: 'portName', slotName: 'portName', width: 150, ellipsis: true, tooltip: true },
  { title: '设备名称', dataIndex: 'name', minWidth: 150, ellipsis: true, tooltip: true },
  { title: '从站地址', dataIndex: 'slaveAddr', width: 100, align: 'center' },
  { title: '协议', dataIndex: 'protocol', width: 130, align: 'center' },
  { title: '型号', dataIndex: 'model', minWidth: 140, ellipsis: true, tooltip: true },
  { title: '分组', dataIndex: 'groupName', slotName: 'groupName', width: 130, ellipsis: true, tooltip: true },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 170,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:device:update', 'monitor:device:delete', 'monitor:timeSync:sync']),
  },
]

const portOptions = ref<PortResp[]>([])
const groups = ref<DeviceGroupNode[]>([])
const flattenGroups = (nodes: DeviceGroupNode[]): DeviceGroupNode[] =>
  nodes.flatMap((node) => [node, ...flattenGroups(node.children ?? [])])
const toGroupTreeData = (nodes: DeviceGroupNode[]): any[] =>
  nodes.map((node) => ({ key: node.id, title: node.name, children: toGroupTreeData(node.children ?? []) }))
const groupOptions = computed(() => toGroupTreeData(groups.value))
const groupNameOf = (id?: string) =>
  (id ? flattenGroups(groups.value).find((item) => item.id === id)?.name ?? id : '未分组')
// 加载端口下拉项
const loadPorts = async () => {
  const [portRes, groupRes] = await Promise.all([listPort({ page: 1, size: 200 }), treeDeviceGroup()])
  portOptions.value = Array.isArray(portRes.data) ? portRes.data : (portRes.data.list ?? [])
  groups.value = groupRes.data ?? []
}
const portNameOf = (portId: string) => portOptions.value.find((port) => port.id === portId)?.name ?? portId

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.portId = undefined
  search()
}

// 删除
const onDelete = (record: DeviceResp) => {
  return handleDelete(() => deleteDevice(record.id), {
    content: `是否确定删除设备「${record.name}」？其名下通道配置会一并删除。`,
    showModal: true,
  })
}

// 一键校时：把平台本机时间同步到该设备（NFR-REL-06）
const onSyncTime = (record: DeviceResp) => {
  Modal.confirm({
    title: '一键校时确认',
    content: `确定把平台本机时间同步到设备「${record.name}」吗？该校时操作不可撤销。`,
    async onOk() {
      const res = await syncDeviceTime(record.id)
      const data = res.data
      if (data?.resultStatus === 'SUCCESS') {
        Message.success(data.message ?? '校时成功')
      } else {
        Message.error(data?.message ?? '校时失败')
      }
    },
  })
}

const defaultForm = (): DeviceReq => ({
  portId: '',
  name: '',
  slaveAddr: 1,
  protocol: 'RS_MODBUS',
  model: '',
  groupId: '1',
  groupName: '',
  location: '',
  longitude: undefined,
  latitude: undefined,
  elevation: undefined,
  address: '',
  timeoutMs: 1000,
  enabled: true,
  description: '',
})

const visible = ref(false)
const title = ref('新增设备')
const formRef = ref()
const form = reactive<DeviceReq>(defaultForm())
const currentId = ref('')

const rules = {
  portId: [{ required: true, message: '请选择所属端口' }],
  name: [{ required: true, message: '请输入设备名称' }],
  slaveAddr: [{ required: true, message: '请填写从站地址' }],
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
  title.value = '新增设备'
  visible.value = true
}

// 修改
const onUpdate = (record: DeviceResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改设备'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateDevice(form, currentId.value)
  } else {
    await addDevice(form)
  }
  Message.success('保存成功')
  search()
  return true
}

onMounted(loadPorts)
</script>
