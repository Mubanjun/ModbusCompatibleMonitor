<template>
  <a-card title="安全设置" bordered class="gradient-card">
    <div v-for="item in modeList" :key="item.title">
      <div class="item">
        <div class="icon-wrapper"><GiSvgIcon :name="item.icon" :size="26" /></div>
        <div class="info">
          <div class="info-top">
            <span class="label">{{ item.title }}</span>
            <span class="bind">
              <icon-check-circle-fill v-if="item.status" :size="14" class="success" />
              <icon-exclamation-circle-fill v-else :size="14" class="warning" />
              <span style="font-size: 12px" :class="item.status ? 'success' : 'warning'">{{ item.statusString }}</span>
            </span>
          </div>
          <div class="info-desc">
            <span class="value">{{ item.value }}</span>
            {{ item.subtitle }}
          </div>
        </div>
        <div class="btn-wrapper">
          <a-button class="btn" :type="item.status ? 'secondary' : 'primary'" @click="onUpdate()">
            {{ item.status ? '修改' : '设置' }}
          </a-button>
        </div>
      </div>
    </div>
  </a-card>

  <VerifyModel ref="verifyModelRef" />
</template>

<script setup lang="ts">
import type { ModeItem } from '../type'
import VerifyModel from '../components/VerifyModel.vue'
import { useUserStore } from '@/stores'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)

// 本平台仅开放「账号 + 密码」登录，安全手机与安全邮箱不支持自助修改，故只保留登录密码
const modeList = ref<ModeItem[]>([])
modeList.value = [
  {
    title: '登录密码',
    icon: 'password-color',
    subtitle: '请设置密码，可通过账号+密码登录',
    type: 'password',
    jumpMode: 'modal',
    status: !!userInfo.value.pwdResetTime,
    statusString: userInfo.value.pwdResetTime ? '已设置' : '未设置',
  },
]

const verifyModelRef = ref<InstanceType<typeof VerifyModel>>()
// 修改
const onUpdate = () => {
  verifyModelRef.value?.open()
}
</script>

<style scoped lang="scss"></style>
