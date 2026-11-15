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
          placeholder="搜索模板名称/型号/描述"
          allow-clear
          style="width: 240px"
          @search="search"
        />
        <a-select v-model="queryForm.elementType" placeholder="要素类型" allow-clear style="width: 180px" @change="search">
          <a-option v-for="item in elementOptions" :key="item.code" :value="item.code">{{ item.name }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:template:create']" type="primary" @click="onAdd">
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
          <a-link v-permission="['monitor:template:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:template:delete']" status="danger" @click="onDelete(record)">删除</a-link>
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
        <a-form-item label="模板名称" field="name">
          <a-input v-model="form.name" placeholder="如：电导率 RS-EC-N01-2 10~20000" allow-clear />
        </a-form-item>
        <a-form-item label="设备型号" field="model">
          <a-input v-model="form.model" placeholder="如：RS-EC-N01-2" allow-clear />
        </a-form-item>
        <a-form-item label="要素类型" field="elementType">
          <a-select v-model="form.elementType" placeholder="选择后自动带出单位" allow-clear @change="onElementChange">
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
  type DeviceTemplateQuery,
  type DeviceTemplateReq,
  type DeviceTemplateResp,
  type ElementTypeItem,
  addTemplate,
  deleteTemplate,
  listElementCatalog,
  pageTemplate,
  updateTemplate,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvTemplate' })

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

const queryForm = reactive<DeviceTemplateQuery>({
  sort: ['sort,asc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageTemplate({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '模板名称', dataIndex: 'name', minWidth: 200, ellipsis: true, tooltip: true },
  { title: '型号', dataIndex: 'model', width: 160, ellipsis: true, tooltip: true },
  { title: '要素', dataIndex: 'elementType', width: 150, ellipsis: true, tooltip: true },
  { title: '单位', dataIndex: 'unit', width: 90, align: 'center' },
  { title: '编码方式', dataIndex: 'encoding', minWidth: 160, ellipsis: true, tooltip: true },
  { title: '系数 A', dataIndex: 'coefficient', width: 100, align: 'center' },
  { title: '量程', dataIndex: 'maxValue', width: 140, align: 'center', render: ({ record }) => `${record.minValue ?? '-'} ~ ${record.maxValue ?? '-'}` },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:template:update', 'monitor:template:delete']),
  },
]

const elementOptions = ref<ElementTypeItem[]>([])

const defaultForm = (): DeviceTemplateReq => ({
  name: '',
  model: '',
  elementType: undefined,
  rangeLabel: undefined,
  dataType: 'ANALOG2_ONLY',
  encoding: 'UINT16_SCALE_1',
  enabled: true,
  description: '',
})

const form = reactive<DeviceTemplateReq>(defaultForm())
const currentRanges = computed(() => elementOptions.value.find((item) => item.code === form.elementType)?.ranges ?? [])

// 加载要素字典
const loadElements = async () => {
  const res = await listElementCatalog()
  elementOptions.value = res.data ?? []
}

// 切换要素时清空档位
const onElementChange = () => {
  form.rangeLabel = undefined
}

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.elementType = undefined
  search()
}

// 删除
const onDelete = (record: DeviceTemplateResp) => {
  return handleDelete(() => deleteTemplate(record.id), {
    content: `是否确定删除模板「${record.name}」？`,
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增模板')
const formRef = ref()
const currentId = ref('')

const rules = {
  name: [{ required: true, message: '请输入模板名称' }],
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
  title.value = '新增模板'
  visible.value = true
}

// 修改
const onUpdate = (record: DeviceTemplateResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改模板'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateTemplate(form, currentId.value)
  } else {
    await addTemplate(form)
  }
  Message.success('保存成功')
  search()
  return true
}

onMounted(loadElements)
</script>
