<template>
  <div>
    <CubeDetail
      v-if="view === 'detail'"
      :key="`detail-${groupId}`"
      :group-id="groupId"
      @back="showLists"
      @input-result="openResult"
      @recheck="openRecheck"
      @view-order="viewOrder"
    />
    <QualityLists
      v-else
      ref="listsRef"
      :order-id="nav.qualityOrderId"
      @open-group="openGroup"
      @input-result="openResult"
      @register="openRegister"
    />
    <CubeRegisterDialog ref="registerRef" @saved="onSaved" />
    <ResultInputDialog ref="resultRef" @submitted="onChanged" />
    <RecheckDialog ref="recheckRef" @submitted="onChanged" />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import CubeRegisterDialog from './quality/CubeRegisterDialog.vue'
import CubeDetail from './quality/CubeDetail.vue'
import QualityLists from './quality/QualityLists.vue'
import RecheckDialog from './quality/RecheckDialog.vue'
import ResultInputDialog from './quality/ResultInputDialog.vue'
import { goMixForOrder, nav } from '../nav'

const view = ref('lists')
const groupId = ref(null)
const listsRef = ref(null)
const registerRef = ref(null)
const resultRef = ref(null)
const recheckRef = ref(null)

function openGroup(id) {
  groupId.value = id
  view.value = 'detail'
}

function showLists() {
  view.value = 'lists'
}

function openRegister() {
  registerRef.value.open()
}

function openResult(item) {
  resultRef.value.open(item)
}

function openRecheck(recheck) {
  recheckRef.value.open(recheck)
}

function onSaved(detail) {
  listsRef.value?.reload()
  if (detail?.id) openGroup(detail.id)
}

function onChanged(item) {
  listsRef.value?.reload()
  if (item?.groupId) openGroup(item.groupId)
}

function viewOrder(orderId) {
  goMixForOrder(orderId)
}

// 从任务单进入质检：自动打开该任务单最新一组试块（若有）
async function handleOrderId(orderId) {
  if (orderId == null) return
  view.value = 'lists'
  await tick()
  const res = await listsRef.value?.loadForOrder(orderId)
  if (res?.length) {
    openGroup(res[0].id)
  }
  nav.qualityOrderId = null
}

watch(() => nav.qualityOrderId, handleOrderId)

onMounted(() => {
  if (nav.qualityOrderId != null) handleOrderId(nav.qualityOrderId)
})

// 等列表组件完成挂载
function tick() {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()))
}
</script>
