<template>
  <a-modal
    v-model:visible="visible" title="修改密码" :mask-closable="false" :esc-to-close="false"
    :width="width >= 500 ? 500 : '100%'" draggable @before-ok="save" @ok="saveAfter" @close="reset"
  >
    <GiForm ref="formRef" v-model="form" :columns="columns" />
  </a-modal>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import { Message } from '@arco-design/web-vue'
import NProgress from 'nprogress'
import { updateUserPassword } from '@/apis'
import { encryptByRsa } from '@/utils/encrypt'
import { useUserStore } from '@/stores'
import { type ColumnItem, GiForm } from '@/components/GiForm'
import { useResetReactive } from '@/hooks'
import modalErrorWrapper from '@/utils/modal-error-wrapper'
import router from '@/router'

const { width } = useWindowSize()
const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)

const formRef = ref<InstanceType<typeof GiForm>>()

const [form, resetForm] = useResetReactive({
  oldPassword: '',
  newPassword: '',
  rePassword: '',
})

const columns: ColumnItem[] = reactive([
  {
    label: '当前密码',
    field: 'oldPassword',
    type: 'input-password',
    span: 24,
    rules: [{ required: true, message: '请输入当前密码' }],
    hide: () => {
      return !userInfo.value.pwdResetTime
    },
  },
  {
    label: '新密码',
    field: 'newPassword',
    type: 'input-password',
    span: 24,
    rules: [
      { required: true, message: '请输入新密码' },
      {
        validator: (value, callback) => {
          if (value === form.oldPassword) {
            callback('新密码不能与当前密码相同')
          } else {
            callback()
          }
        },
      },
    ],
  },
  {
    label: '确认新密码',
    field: 'rePassword',
    type: 'input-password',
    span: 24,
    props: {
      placeholder: '请再次输入新密码',
    },
    rules: [
      { required: true, message: '请再次输入新密码' },
      {
        validator: (value, callback) => {
          if (value !== form.newPassword) {
            callback('两次输入的密码不一致')
          } else {
            callback()
          }
        },
      },
    ],
  },
])

// 重置
const reset = () => {
  formRef.value?.formRef?.resetFields()
  resetForm()
}

// 保存
const save = async () => {
  const isInvalid = await formRef.value?.formRef?.validate()
  if (isInvalid) return false
  if (form.newPassword !== form.rePassword) {
    Message.error('两次新密码不一致')
    return false
  }
  if (form.newPassword === form.oldPassword) {
    Message.error('新密码与旧密码不能相同')
    return false
  }
  try {
    await updateUserPassword({
      oldPassword: encryptByRsa(form.oldPassword) || '',
      newPassword: encryptByRsa(form.newPassword) || '',
    })
    return true
  } catch (error) {
    return false
  }
}

const saveAfter = async () => {
  modalErrorWrapper({
    title: '提示',
    content: '密码修改成功! 请保存好新密码，并使用新密码重新登录',
    maskClosable: false,
    escToClose: false,
    okText: '重新登录',
    async onOk() {
      NProgress.done()
      await userStore.logoutCallBack()
      await router.replace('/login')
    },
  })
}

const visible = ref(false)
// 打开弹框
const open = () => {
  visible.value = true
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.captcha-btn {
  margin-left: 12px;
  min-width: 98px;
  border-radius: 4px;
}
</style>
