<template>
  <GiPageLayout>
    <a-row :gutter="16">
      <a-col :xs="24" :md="9" :lg="8">
        <a-card title="分组树" :bordered="false">
          <template #extra>
            <a-space>
              <a-button size="small" @click="comingSoon">组级批量操作</a-button>
              <a-button size="small" type="primary" @click="openAdd('1')">新增一级分组</a-button>
              <a-button size="small" @click="load">刷新</a-button>
            </a-space>
          </template>
          <a-input-search v-model="keyword" placeholder="搜索分组" allow-clear style="margin-bottom: 12px" />
          <a-tree :data="treeData" block-node default-expand-all :selected-keys="selectedKeys" @select="onSelect" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="15" :lg="16">
        <a-card :title="selected ? selected.name : '分组详情'" :bordered="false">
          <template #extra>
            <a-space v-if="selected">
              <a-button v-permission="['device:group:create']" @click="openAdd(selected.id)">新增子分组</a-button>
              <a-button v-permission="['device:group:update']" @click="openEdit">修改/移动</a-button>
              <a-button v-permission="['device:group:delete']" status="danger" @click="onDelete">删除</a-button>
            </a-space>
          </template>
          <a-descriptions v-if="selected" :column="2" bordered>
            <a-descriptions-item label="分组 ID">{{ selected.id }}</a-descriptions-item>
            <a-descriptions-item label="层级">{{ selected.depth }}</a-descriptions-item>
            <a-descriptions-item label="本组设备">{{ selected.directDeviceCount }}</a-descriptions-item>
            <a-descriptions-item label="含子分组设备">{{ selected.subtreeDeviceCount }}</a-descriptions-item>
            <a-descriptions-item label="路径">{{ selected.path }}</a-descriptions-item>
            <a-descriptions-item label="内置">{{ selected.builtin ? '是' : '否' }}</a-descriptions-item>
            <a-descriptions-item label="备注" :span="2">{{ selected.remark || '暂无' }}</a-descriptions-item>
          </a-descriptions>
          <a-empty v-else description="请选择左侧分组" />
        </a-card>
      </a-col>
    </a-row>

    <a-modal v-model:visible="visible" :title="editing ? '修改分组' : '新增分组'" :width="520" @before-ok="onSubmit">
      <a-form ref="formRef" :model="form" :rules="rules" label-col-flex="90px">
        <a-form-item label="名称" field="name">
          <a-input v-model="form.name" placeholder="分组名称" allow-clear />
        </a-form-item>
        <a-form-item label="父分组" field="parentId">
          <a-tree-select v-model="form.parentId" :data="treeSelectData" placeholder="选择父分组" allow-clear />
        </a-form-item>
        <a-form-item label="排序" field="sortNo">
          <a-input-number v-model="form.sortNo" :min="0" :max="9999" />
        </a-form-item>
        <a-form-item label="备注" field="remark">
          <a-textarea v-model="form.remark" :max-length="200" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>
  </GiPageLayout>
</template>

<script setup lang="ts">
import { Message, Modal } from '@arco-design/web-vue'
import {
  type DeviceGroupNode,
  type DeviceGroupReq,
  addDeviceGroup,
  deleteDeviceGroup,
  treeDeviceGroup,
  updateDeviceGroup,
} from '@/apis'
import { comingSoon } from '@/utils/coming-soon'

defineOptions({ name: 'EnvDeviceGroup' })

const loading = ref(false)
const keyword = ref('')
const groups = ref<DeviceGroupNode[]>([])
const selected = ref<DeviceGroupNode | null>(null)
const visible = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive<DeviceGroupReq>({ name: '', parentId: '1', sortNo: 999, remark: '' })

const flatten = (nodes: DeviceGroupNode[]): DeviceGroupNode[] =>
  nodes.flatMap((node) => [node, ...flatten(node.children ?? [])])

const toTreeData = (nodes: DeviceGroupNode[]): any[] =>
  nodes.map((node) => ({
    key: node.id,
    title: `${node.name}（${node.subtreeDeviceCount}）`,
    children: toTreeData(node.children ?? []),
  }))

const filterTree = (nodes: DeviceGroupNode[], text: string): DeviceGroupNode[] => {
  if (!text) return nodes
  return nodes
    .map((node) => ({ ...node, children: filterTree(node.children ?? [], text) }))
    .filter((node) => node.name.includes(text) || (node.children?.length ?? 0) > 0)
}

const treeData = computed(() => toTreeData(filterTree(groups.value, keyword.value.trim())))
const treeSelectData = computed(() => toTreeData(groups.value))
const selectedKeys = computed(() => (selected.value ? [selected.value.id] : []))

const load = async () => {
  loading.value = true
  try {
    const res = await treeDeviceGroup()
    groups.value = res.data ?? []
    if (selected.value) {
      selected.value = flatten(groups.value).find((item) => item.id === selected.value?.id) ?? null
    }
  } finally {
    loading.value = false
  }
}

const onSelect = (keys: (string | number)[]) => {
  const id = keys[0] ? String(keys[0]) : ''
  selected.value = flatten(groups.value).find((item) => item.id === id) ?? null
}

const openAdd = (parentId: string) => {
  editing.value = false
  Object.assign(form, { name: '', parentId, sortNo: 999, remark: '' })
  visible.value = true
}

const openEdit = () => {
  if (!selected.value) return
  editing.value = true
  Object.assign(form, {
    name: selected.value.name,
    parentId: selected.value.parentId,
    sortNo: selected.value.sortNo,
    remark: selected.value.remark,
  })
  visible.value = true
}

const onSubmit = async () => {
  const invalid = await formRef.value?.validate()
  if (invalid) return false
  if (editing.value && selected.value) {
    await updateDeviceGroup(form, selected.value.id)
  } else {
    await addDeviceGroup(form)
  }
  Message.success('保存成功')
  visible.value = false
  await load()
  return true
}

const onDelete = async () => {
  if (!selected.value) return
  Modal.confirm({
    title: '删除分组',
    content: `删除「${selected.value.name}」后，其直接设备与子分组将移到父分组。`,
    async onOk() {
      await deleteDeviceGroup(selected.value!.id)
      selected.value = null
      await load()
      Message.success('已删除')
    },
  })
}

const rules = { name: [{ required: true, message: '请输入分组名称' }] }

onMounted(load)
</script>
