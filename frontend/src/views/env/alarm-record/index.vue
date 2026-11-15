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
          placeholder="搜索设备名称/报警信息/要素"
          allow-clear
          style="width: 230px"
          @search="search"
        />
        <a-select v-model="queryForm.alarmType" placeholder="报警类型" allow-clear style="width: 180px" @change="search">
          <a-option v-for="(item, key) in alarmTypeMap" :key="key" :value="key">{{ item[0] }}</a-option>
        </a-select>
        <a-select v-model="queryForm.handlingFlag" placeholder="处理状态" allow-clear style="width: 160px" @change="search">
          <a-option :value="0">未处理</a-option>
          <a-option :value="1">已处理</a-option>
          <a-option :value="2">已忽略</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #alarmType="{ record }">
        <a-tag :color="alarmTypeMap[String(record.alarmType)]?.[1] ?? 'gray'">
          {{ alarmTypeMap[String(record.alarmType)]?.[0] ?? record.alarmType }}
        </a-tag>
      </template>
      <template #handlingFlag="{ record }">
        <a-tag :color="handlingFlagMap[String(record.handlingFlag)]?.[1] ?? 'gray'">
          {{ handlingFlagMap[String(record.handlingFlag)]?.[0] ?? record.handlingFlag }}
        </a-tag>
      </template>
      <template #handlingUser="{ record }">
        <div>{{ record.handlingUser ?? '-' }}</div>
        <div v-if="record.handlingMethod" style="color: var(--color-text-3); font-size: 12px">
          {{ record.handlingMethod }}
        </div>
      </template>
      <template #operation="{ record }">
        <a-link
          v-if="record.handlingFlag === 0"
          v-permission="['monitor:alarmRecord:update']"
          title="登记处理结果"
          @click="onHandle(record)"
        >
          处理
        </a-link>
        <span v-else style="color: var(--color-text-3)">-</span>
      </template>
    </GiTable>

    <a-modal v-model:visible="handleVisible" title="登记报警处理结果" width="520" @ok="submitHandle">
      <a-form :model="handleForm" layout="vertical">
        <a-form-item label="处理结果">
          <a-radio-group v-model="handleForm.handlingFlag">
            <a-radio :value="1">已处理</a-radio>
            <a-radio :value="2">已忽略</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="处理方式">
          <a-textarea
            v-model="handleForm.handlingMethod"
            placeholder="例如：已现场核查，清洗探头"
            :auto-size="{ minRows: 3, maxRows: 5 }"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import { type AlarmRecordQuery, type AlarmRecordResp, handleAlarmRecord, pageAlarmRecord } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvAlarmRecord' })

// 键与 biz_alarm_record.alarm_type 落库值一致（当前为中文类型名）
const alarmTypeMap: Record<string, [string, string]> = {
  上限报警: ['上限报警', 'red'],
  下限报警: ['下限报警', 'orangered'],
  偏差报警: ['偏差报警', 'orangered'],
  变化率报警: ['变化率报警', 'arcoblue'],
  开关量闭合报警: ['开关量闭合报警', 'purple'],
  开关量断开报警: ['开关量断开报警', 'purple'],
  报警恢复: ['报警恢复', 'green'],
}

const handleVisible = ref(false)
const handleForm = reactive<{ id?: string, handlingFlag: number, handlingMethod?: string }>({
  handlingFlag: 1,
})

const onHandle = (record: AlarmRecordResp) => {
  handleForm.id = record.id
  handleForm.handlingFlag = 1
  handleForm.handlingMethod = undefined
  handleVisible.value = true
}

const handlingFlagMap: Record<string, [string, string]> = {
  0: ['未处理', 'red'],
  1: ['已处理', 'green'],
  2: ['已忽略', 'gray'],
}

const queryForm = reactive<AlarmRecordQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageAlarmRecord({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '设备', dataIndex: 'deviceName', minWidth: 150, ellipsis: true, tooltip: true },
  { title: '地址', dataIndex: 'deviceAddr', width: 80, align: 'center' },
  { title: '通道', dataIndex: 'nodeId', width: 80, align: 'center' },
  { title: '要素', dataIndex: 'elementName', width: 110 },
  { title: '类型', dataIndex: 'alarmType', slotName: 'alarmType', width: 100, align: 'center' },
  { title: '报警信息', dataIndex: 'alarmMessage', minWidth: 220, ellipsis: true, tooltip: true },
  { title: '数值', dataIndex: 'dataValue', width: 90, align: 'center' },
  { title: '单位', dataIndex: 'unit', width: 80, align: 'center' },
  { title: '报警范围', dataIndex: 'alarmRange', width: 130, align: 'center' },
  { title: '记录时间', dataIndex: 'recordTime', width: 180 },
  { title: '处理', dataIndex: 'handlingFlag', slotName: 'handlingFlag', width: 90, align: 'center' },
  { title: '处理人', dataIndex: 'handlingUser', slotName: 'handlingUser', width: 200 },
  { title: '操作', dataIndex: 'operation', slotName: 'operation', width: 100, align: 'center', fixed: 'right' },
]

// 提交处理结果（须放在 useTable 之后，避免引用未初始化的 search）
const submitHandle = async () => {
  await handleAlarmRecord(
    { handlingFlag: handleForm.handlingFlag, handlingMethod: handleForm.handlingMethod },
    String(handleForm.id),
  )
  Message.success('处理结果已登记')
  handleVisible.value = false
  search()
}

// 重置查询
const reset = () => {
  queryForm.description = undefined
  queryForm.alarmType = undefined
  queryForm.handlingFlag = undefined
  search()
}
</script>
