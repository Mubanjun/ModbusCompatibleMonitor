<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1000 }"
      @refresh="search"
    >
      <template #toolbar-left>
        <a-input-search
          v-model="queryForm.description"
          placeholder="搜索端口名称/地址/描述"
          allow-clear
          style="width: 240px"
          @search="search"
        />
        <a-select v-model="queryForm.portType" placeholder="端口类型" allow-clear style="width: 170px" @change="search">
          <a-option value="SERIAL">串口</a-option>
          <a-option value="TCP">Modbus TCP / 串口服务器</a-option>
          <a-option value="RFC2217">RFC2217 串口服务器</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:port:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">启用</a-tag>
        <a-tag v-else color="gray">停用</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:port:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:port:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="visible"
      :title="title"
      :width="580"
      draggable
      :mask-closable="false"
      @before-ok="onSubmit"
      @close="onReset"
    >
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="120px">
        <a-form-item label="端口名称" field="name">
          <a-input v-model="form.name" placeholder="如：1 号 485 总线" allow-clear />
        </a-form-item>
        <a-form-item label="端口类型" field="portType">
          <a-radio-group v-model="form.portType">
            <a-radio value="SERIAL">串口</a-radio>
            <a-radio value="TCP">Modbus TCP</a-radio>
            <a-radio value="RFC2217">RFC2217</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="端口地址" field="portName">
          <a-input v-model="form.portName" placeholder="串口号如 COM1，或 IP:端口" allow-clear />
        </a-form-item>
        <a-form-item v-if="form.portType !== 'TCP'" label="波特率" field="baudRate">
          <a-select v-model="form.baudRate" allow-clear>
            <a-option v-for="rate in baudRates" :key="rate" :value="rate">{{ rate }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="数据位 / 停止位" field="dataBits">
          <a-space>
            <a-select v-model="form.dataBits" style="width: 110px">
              <a-option v-for="bit in [5, 6, 7, 8]" :key="bit" :value="bit">{{ bit }}</a-option>
            </a-select>
            <a-select v-model="form.stopBits" style="width: 110px">
              <a-option :value="1">1</a-option>
              <a-option :value="2">2</a-option>
            </a-select>
          </a-space>
        </a-form-item>
        <a-form-item label="校验位 / 流控" field="parity">
          <a-space>
            <a-select v-model="form.parity" style="width: 110px">
              <a-option :value="0">无校验</a-option>
              <a-option :value="1">奇校验</a-option>
              <a-option :value="2">偶校验</a-option>
            </a-select>
            <a-select v-model="form.flowControl" style="width: 130px">
              <a-option :value="0">无流控</a-option>
              <a-option :value="1">RTS/CTS</a-option>
              <a-option :value="2">XON/XOFF</a-option>
            </a-select>
          </a-space>
        </a-form-item>
        <a-form-item label="485 接入上限" field="deviceLimit">
          <a-input-number v-model="form.deviceLimit" :min="1" :max="256" placeholder="默认 32" />
          <template #extra>
            <span style="font-size: 12px">同一 485 端口可接入的设备数量上限</span>
          </template>
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
import { type PortQuery, type PortReq, type PortResp, addPort, deletePort, pagePort, updatePort } from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvPort' })

const baudRates = [1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200]

const queryForm = reactive<PortQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pagePort({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '端口名称', dataIndex: 'name', minWidth: 140, ellipsis: true, tooltip: true },
  { title: '类型', dataIndex: 'portType', width: 130, align: 'center' },
  { title: '端口地址', dataIndex: 'portName', minWidth: 140, ellipsis: true, tooltip: true },
  { title: '波特率', dataIndex: 'baudRate', width: 100, align: 'center' },
  { title: '接入上限', dataIndex: 'deviceLimit', width: 100, align: 'center' },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  { title: '描述', dataIndex: 'description', minWidth: 160, ellipsis: true, tooltip: true },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:port:update', 'monitor:port:delete']),
  },
]

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.portType = undefined
  search()
}

// 删除
const onDelete = (record: PortResp) => {
  return handleDelete(() => deletePort(record.id), {
    content: `是否确定删除端口「${record.name}」？`,
    showModal: true,
  })
}

const defaultForm = (): PortReq => ({
  name: '',
  portType: 'SERIAL',
  portName: '',
  baudRate: 4800,
  dataBits: 8,
  stopBits: 1,
  parity: 0,
  flowControl: 0,
  deviceLimit: 32,
  enabled: true,
  description: '',
  sort: 999,
})

const visible = ref(false)
const title = ref('新增端口')
const formRef = ref()
const form = reactive<PortReq>(defaultForm())
const currentId = ref('')

const rules = {
  name: [{ required: true, message: '请输入端口名称' }],
  portType: [{ required: true, message: '请选择端口类型' }],
  portName: [{ required: true, message: '请输入端口地址' }],
  deviceLimit: [{ required: true, message: '请填写设备数量上限' }],
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
  title.value = '新增端口'
  visible.value = true
}

// 修改
const onUpdate = (record: PortResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改端口'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updatePort(form, currentId.value)
  } else {
    await addPort(form)
  }
  Message.success('保存成功')
  search()
  return true
}
</script>
