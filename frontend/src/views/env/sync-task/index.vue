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
          placeholder="搜索任务名称"
          allow-clear
          style="width: 200px"
          @search="search"
        />
        <a-select v-model="queryForm.targetId" placeholder="目标库" allow-clear style="width: 190px" @change="search">
          <a-option v-for="item in targetOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
        </a-select>
        <a-select v-model="queryForm.taskStatus" placeholder="任务状态" allow-clear style="width: 140px" @change="search">
          <a-option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['monitor:syncTask:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #targetName="{ record }">
        {{ targetNameOf(record.targetId) }}
      </template>
      <template #syncMode="{ record }">
        {{ record.syncMode === 'FULL' ? '全量' : '增量' }}
      </template>
      <template #conflictPolicy="{ record }">
        {{ policyLabel(record.conflictPolicy) }}
      </template>
      <template #taskStatus="{ record }">
        <a-tag :color="statusColor(record.taskStatus)">{{ statusLabel(record.taskStatus) }}</a-tag>
      </template>
      <template #progress="{ record }">
        {{ record.totalCount ?? 0 }} / 成功 {{ record.successCount ?? 0 }} / 失败 {{ record.failCount ?? 0 }}
      </template>
      <template #enabled="{ record }">
        <a-tag v-if="record.enabled" color="green">启用</a-tag>
        <a-tag v-else color="gray">停用</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['monitor:syncTask:start']" @click="onStart(record)">执行</a-link>
          <a-link
            v-if="record.taskStatus === 'RUNNING'"
            v-permission="['monitor:syncTask:pause']"
            @click="onPause(record)"
          >
            暂停
          </a-link>
          <a-link
            v-if="record.taskStatus === 'PAUSED'"
            v-permission="['monitor:syncTask:resume']"
            @click="onResume(record)"
          >
            续传
          </a-link>
          <a-link v-permission="['monitor:syncTask:update']" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['monitor:syncTask:delete']" status="danger" @click="onDelete(record)">删除</a-link>
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
        <a-form-item label="任务名称" field="name">
          <a-input v-model="form.name" placeholder="如：历史数据同步至仁科库" allow-clear />
        </a-form-item>
        <a-form-item label="目标库" field="targetId">
          <a-select v-model="form.targetId" placeholder="请选择上报目标" allow-clear>
            <a-option v-for="item in targetOptions" :key="item.id" :value="item.id">
              {{ item.name }}（{{ item.targetType }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="同步模式" field="syncMode">
          <a-radio-group v-model="form.syncMode">
            <a-radio value="INCREMENT">增量</a-radio>
            <a-radio value="FULL">全量</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="冲突策略" field="conflictPolicy">
          <a-select v-model="form.conflictPolicy">
            <a-option v-for="item in policyOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
          </a-select>
          <template #extra>
            <span style="font-size: 12px">以记录时间判定新旧；冲突发生时按此策略处理</span>
          </template>
        </a-form-item>
        <a-form-item label="同步表" field="tableNames">
          <a-checkbox-group v-model="tableList">
            <a-checkbox value="tbalarm">报警表 tbalarm</a-checkbox>
            <a-checkbox value="tbhistory">历史数据 tbhistory</a-checkbox>
            <a-checkbox value="tbnoticelog">通知日志 tbnoticelog</a-checkbox>
            <a-checkbox value="tbsyslog">系统日志 tbsyslog</a-checkbox>
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
  type ReportTargetResp,
  type SyncTaskQuery,
  type SyncTaskReq,
  type SyncTaskResp,
  addSyncTask,
  deleteSyncTask,
  listReportTarget,
  pageSyncTask,
  pauseSyncTask,
  resumeSyncTask,
  startSyncTask,
  updateSyncTask,
} from '@/apis'
import { useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'EnvSyncTask' })

const statusOptions = [
  { value: 'IDLE', label: '待执行' },
  { value: 'RUNNING', label: '进行中' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'DONE', label: '已完成' },
  { value: 'FAILED', label: '失败' },
]
const policyOptions = [
  { value: 'NEWER_WINS', label: '新数据优先' },
  { value: 'OVERWRITE', label: '覆盖' },
  { value: 'SKIP', label: '跳过' },
  { value: 'MANUAL', label: '人工确认' },
]
const statusLabel = (value: string) => statusOptions.find((item) => item.value === value)?.label ?? value
const statusColor = (value: string) => {
  switch (value) {
    case 'RUNNING':
      return 'arcoblue'
    case 'DONE':
      return 'green'
    case 'FAILED':
      return 'red'
    case 'PAUSED':
      return 'orangered'
    default:
      return 'gray'
  }
}
const policyLabel = (value: string) => policyOptions.find((item) => item.value === value)?.label ?? value

// 表单声明前置，避免被下方的函数引用时报「used before defined」
const defaultForm = (): SyncTaskReq => ({
  name: '',
  targetId: undefined,
  syncMode: 'INCREMENT',
  conflictPolicy: 'NEWER_WINS',
  tableNames: undefined,
  enabled: true,
  description: '',
})

const form = reactive<SyncTaskReq>(defaultForm())
const tableList = ref<string[]>([])
const targetOptions = ref<ReportTargetResp[]>([])
const targetNameOf = (id: string) => targetOptions.value.find((item) => item.id === id)?.name ?? id

const queryForm = reactive<SyncTaskQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageSyncTask({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '任务名称', dataIndex: 'name', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '目标库', dataIndex: 'targetName', slotName: 'targetName', minWidth: 160, ellipsis: true, tooltip: true },
  { title: '模式', dataIndex: 'syncMode', slotName: 'syncMode', width: 90, align: 'center' },
  { title: '冲突策略', dataIndex: 'conflictPolicy', slotName: 'conflictPolicy', width: 120, align: 'center' },
  { title: '同步表', dataIndex: 'tableNames', minWidth: 200, ellipsis: true, tooltip: true },
  { title: '状态', dataIndex: 'taskStatus', slotName: 'taskStatus', width: 100, align: 'center' },
  { title: '进度', dataIndex: 'progress', slotName: 'progress', width: 220, align: 'center' },
  { title: '上次同步', dataIndex: 'lastSyncTime', width: 180 },
  { title: '启用', dataIndex: 'enabled', slotName: 'enabled', width: 90, align: 'center' },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 130,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['monitor:syncTask:update', 'monitor:syncTask:delete']),
  },
]

// 加载目标库下拉项
const loadTargets = async () => {
  const res = await listReportTarget({ page: 1, size: 200 })
  targetOptions.value = Array.isArray(res.data) ? res.data : (res.data.list ?? [])
}

// 重置查询
const reset = () => {
  queryForm.description = undefined
  queryForm.targetId = undefined
  queryForm.taskStatus = undefined
  search()
}

// 删除
const onDelete = (record: SyncTaskResp) => {
  return handleDelete(() => deleteSyncTask(record.id), {
    content: `是否确定删除同步任务「${record.name}」？`,
    showModal: true,
  })
}

// 一键同步
const onStart = async (record: SyncTaskResp) => {
  const res = await startSyncTask(record.id)
  Message.success(res.data ?? '已提交同步')
  search()
}

// 暂停同步
const onPause = async (record: SyncTaskResp) => {
  const res = await pauseSyncTask(record.id)
  Message.info(res.data ? '已请求暂停，将在当前批次结束后停止' : '任务未在运行中')
  search()
}

// 续传同步
const onResume = async (record: SyncTaskResp) => {
  const res = await resumeSyncTask(record.id)
  Message.success(res.data ?? '已提交续传')
  search()
}

const visible = ref(false)
const title = ref('新增同步任务')
const formRef = ref()
const currentId = ref('')

const rules = {
  name: [{ required: true, message: '请输入任务名称' }],
  targetId: [{ required: true, message: '请选择目标库' }],
  syncMode: [{ required: true, message: '请选择同步模式' }],
  conflictPolicy: [{ required: true, message: '请选择冲突策略' }],
}

// 重置表单
const onReset = () => {
  Object.assign(form, defaultForm())
  tableList.value = []
  currentId.value = ''
  formRef.value?.clearValidate?.()
}

// 新增
const onAdd = () => {
  onReset()
  title.value = '新增同步任务'
  visible.value = true
}

// 修改
const onUpdate = (record: SyncTaskResp) => {
  onReset()
  Object.assign(form, record)
  tableList.value = record.tableNames ? record.tableNames.split(',').filter(Boolean) : []
  currentId.value = record.id
  title.value = '修改同步任务'
  visible.value = true
}

// 提交
const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) {
    return false
  }
  form.tableNames = tableList.value.length ? tableList.value.join(',') : undefined
  if (currentId.value) {
    await updateSyncTask(form, currentId.value)
  } else {
    await addSyncTask(form)
  }
  Message.success('保存成功')
  search()
  return true
}

onMounted(loadTargets)
</script>
