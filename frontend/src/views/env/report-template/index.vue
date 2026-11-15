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
          placeholder="搜索模板名称/编码"
          allow-clear
          style="width: 220px"
          @search="search"
        />
        <a-select v-model="queryForm.formatType" placeholder="报文格式" allow-clear style="width: 150px" @change="search">
          <a-option value="JSON">JSON</a-option>
          <a-option value="KV">键值对</a-option>
          <a-option value="CUSTOM">自定义模板</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:reportTemplate:create']" type="primary" @click="onAdd">
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
          <a-link v-permission="['monitor:reportTemplate:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:reportTemplate:delete']" status="danger" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="visible"
      :title="title"
      :width="680"
      draggable
      :mask-closable="false"
      @before-ok="onSubmit"
      @close="onReset"
    >
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="130px">
        <a-form-item label="模板名称" field="name">
          <a-input v-model="form.name" placeholder="如：标准水质 JSON 报文" allow-clear />
        </a-form-item>
        <a-form-item label="模板编码" field="code">
          <a-input v-model="form.code" placeholder="如：water_quality_json" allow-clear />
          <template #extra>
            <span style="font-size: 12px">字母开头，支持字母、数字与下划线；供上报规则引用</span>
          </template>
        </a-form-item>
        <a-form-item label="报文格式" field="formatType">
          <a-radio-group v-model="form.formatType">
            <a-radio value="JSON">JSON</a-radio>
            <a-radio value="KV">键值对</a-radio>
            <a-radio value="CUSTOM">自定义模板</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="模板内容" field="content">
          <a-textarea v-model="form.content" :auto-size="{ minRows: 6 }" placeholder="支持变量占位，如 &#36;{deviceName}、&#36;{recordTime}、&#36;{engValue}" />
          <template #extra>
            <span style="font-size: 12px">可用占位：&#36;{'{'}deviceName{'}'}、&#36;{'{'}recordTime{'}'}、&#36;{'{'}engValue{'}'}、&#36;{'{'}rawValue{'}'}、&#36;{'{'}elementName{'}'}、&#36;{'{'}unit{'}'}</span>
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
import {
  type ReportTemplateQuery,
  type ReportTemplateReq,
  type ReportTemplateResp,
  addReportTemplate,
  deleteReportTemplate,
  pageReportTemplate,
  updateReportTemplate,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvReportTemplate' })

const queryForm = reactive<ReportTemplateQuery>({
  sort: ['code,asc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageReportTemplate({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '模板名称', dataIndex: 'name', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '编码', dataIndex: 'code', minWidth: 170, ellipsis: true, tooltip: true },
  { title: '报文格式', dataIndex: 'formatType', width: 110, align: 'center' },
  { title: '模板内容', dataIndex: 'content', minWidth: 260, ellipsis: true, tooltip: true },
  { title: '状态', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:reportTemplate:update', 'monitor:reportTemplate:delete']),
  },
]

const defaultForm = (): ReportTemplateReq => ({
  name: '',
  code: '',
  formatType: 'JSON',
  content: '',
  enabled: true,
  description: '',
})

const form = reactive<ReportTemplateReq>(defaultForm())

// 重置
const reset = () => {
  queryForm.description = undefined
  queryForm.formatType = undefined
  search()
}

// 删除
const onDelete = (record: ReportTemplateResp) => {
  return handleDelete(() => deleteReportTemplate(record.id), {
    content: `是否确定删除上报模板「${record.name}」？`,
    showModal: true,
  })
}

const visible = ref(false)
const title = ref('新增上报模板')
const formRef = ref()
const currentId = ref('')

const rules = {
  name: [{ required: true, message: '请输入模板名称' }],
  code: [{ required: true, message: '请输入模板编码' }],
  formatType: [{ required: true, message: '请选择报文格式' }],
  content: [{ required: true, message: '请输入模板内容' }],
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
  title.value = '新增上报模板'
  visible.value = true
}

// 修改
const onUpdate = (record: ReportTemplateResp) => {
  onReset()
  Object.assign(form, record)
  currentId.value = record.id
  title.value = '修改上报模板'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  if (currentId.value) {
    await updateReportTemplate(form, currentId.value)
  } else {
    await addReportTemplate(form)
  }
  Message.success('保存成功')
  search()
  return true
}
</script>
