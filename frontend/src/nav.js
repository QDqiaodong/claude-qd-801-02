import { reactive } from 'vue'

/**
 * 轻量跨视图导航：菜单只有 5 步，App.vue 用自定义步骤切换而不是 vue-router，
 * 配合比页「查看检测链」与质检详情页「查看任务单」通过这里把视图和载荷带过去。
 */
export const nav = reactive({
  current: 'task',
  /** 跳转到质检页时可携带任务单 id（从任务单查看完整检测链） */
  qualityOrderId: null,
  /** 跳转到配合比页时可携带任务单 id（定位任务单） */
  mixOrderId: null
})

export function goStep(step) {
  nav.current = step
}

export function goQualityForOrder(orderId) {
  nav.current = 'quality'
  nav.qualityOrderId = orderId
}

export function goMixForOrder(orderId) {
  nav.current = 'mix'
  nav.mixOrderId = orderId
}
