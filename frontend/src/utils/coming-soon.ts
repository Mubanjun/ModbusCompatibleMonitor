import { Modal } from '@arco-design/web-vue'

/**
 * 未实现功能的统一占位提示。
 *
 * 约定：所有尚未实现的功能入口，点击后必须调用本方法，
 * 以 PopUp(type=error, message="敬请期待") 的形式明确告知用户，
 * 不得静默无响应、不得跳转到空白页。
 */
export function comingSoon() {
  Modal.error({
    title: '功能提示',
    content: '敬请期待',
  })
}
