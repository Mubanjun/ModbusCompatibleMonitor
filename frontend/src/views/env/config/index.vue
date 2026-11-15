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
          placeholder="搜索参数名称/编码/说明"
          allow-clear
          style="width: 230px"
          @search="search"
        />
        <a-select v-model="queryForm.category" placeholder="分类" allow-clear style="width: 150px" @change="search">
          <a-option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:config:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #category="{ record }">
        <a-tag :color="categoryColor(record.category)">{{ categoryLabel(record.category) }}</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:config:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:config:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="visible"
      :title="title"
      :width="600"
      draggable
      :mask-closable="false"
      @before-ok="onSubmit"
      @close="onReset"
    >
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="120px">
        <a-form-item label="分类" field="category">
          <a-radio-group v-model="form.category">
            <a-radio v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="参数名称" field="name">
          <a-input v-model="form.name" placeholder="如：采集间隔" allow-clear />
        </a-form-item>
        <a-form-item label="参数编码" field="code">
          <a-input v-model="form.code" placeholder="如：COLLECT_INTERVAL_MS" allow-clear :disabled="!!currentId" />
          <template #extra>
            <span style="font-size: 12px">{{ currentId ? '参数编码创建后不可修改' : '运行时代码按此编码取值，创建后不可修改' }}</span>
          </template>
        </a-form-item>
        <a-form-item label="参数值" field="value">
          <a-input v-model="form.value" placeholder="选填" allow-clear />
        </a-form-item>
        <a-form-item label="默认值" field="defaultValue">
          <a-input v-model="form.defaultValue" placeholder="选填" allow-clear />
        </a-form-item>
        <a-form-item label="说明" field="description">
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
  type SysConfigQuery,
  type SysConfigReq,
  type SysConfigResp,
  addSysConfig,
  deleteSysConfig,
  pageSysConfig,
  updateSysConfig,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvConfig' })

const categoryOptions = [
  { value: 'COLLECT', label: '采集' },
  { value: 'REPORT', label: '上报' },
  { value: 'STORAGE', label: '存储' },
  { value: 'SYSTEM', label: '系统' },
]
const categoryLabel = (value: string) => categoryOptions.find((item) => item.value === value)?.label ?? value
const categoryColor = (value: string) => {
  switch (value) {
    case 'COLLECT':
      return 'arcoblue'
    case 'REPORT':
      return 'green'
    case 'STORAGE':
      return 'orangered'
    default:
      return 'gray'
  }
}

// 表单声明前置，避免被下方函数引用时报「used before defined」
const defaultForm = (): SysConfigReq => ({
  category: 'COLLECT',
  name: '',
  code: '',
  value: undefined,
  defaultValue: undefined,
  description: '',
})

const form = reactive<SysConfigReq>(defaultForm())
const currentId = ref('')

const queryForm = reactive<SysConfigQuery>({
  sort: ['category,asc', 'code,asc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageSysConfig({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '分类', dataIndex: 'category', slotName: 'category', width: 100, align: 'center' },
  { title: '参数名称', dataIndex: 'name', minWidth: 160, ellipsis: true, tooltip: true },
  { title: '参数编码', dataIndex: 'code', minWidth: 200, ellipsis: true, tooltip: true },
  { title: '参数值', dataIndex: 'value', minWidth: 120, ellipsis: true, tooltip: true },
  { title: '默认值', dataIndex: 'defaultValue', minWidth: 120, ellipsis: true, tooltip: true },
  { title: '说明', dataIndex: 'description', minWidth: 240, ellipsis: true, tooltip: true },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:config:update', 'monitor:config:delete']),
  },
]

// 重置查询
const reset = () => {
  queryForm.description = undefined
  queryForm.category = undefined
  search()
}

// 删除
const onDelete = (record: SysConfigResp) => {
  return handleDelete(() => deleteSysConfig(record.id), {
    content: `是否确定删除配置「${record.name}」？`,
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增配置')
const formRef = ref()

const rules = {
  category: [{ required: true, message: '请选择分类' }],
  name: [{ required: true, message: '请输入参数名称' }],
  code: [{ required: true, message: '请输入参数编码' }],
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
  title.value = '新增配置'
  visible.value = true
}

// 修改
const onUpdate = (record: SysConfigResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改配置'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateSysConfig(form, currentId.value)
  } else {
    await addSysConfig(form)
  }
  Message.success('保存成功')
  search()
  return true
}
</script>
