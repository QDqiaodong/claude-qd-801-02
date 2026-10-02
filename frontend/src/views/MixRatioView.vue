<template>
  <div>
    <div class="cp-panel">
      <div class="cp-panel-head">
        <div class="cp-panel-title">任务单配合比卡</div>
        <div class="cp-panel-sub">就地修改每方用量，自动重算水胶比；超上限会被拦下</div>
        <div style="margin-left: auto; display: flex; gap: 8px">
          <el-select v-model="filter.status" size="small" clearable placeholder="状态" style="width: 120px" @change="load">
            <el-option label="待排产" value="PENDING" />
            <el-option label="生产中" value="PRODUCING" />
            <el-option label="已完成" value="DONE" />
          </el-select>
          <el-select v-model="filter.grade" size="small" clearable placeholder="强度等级" style="width: 110px" @change="load">
            <el-option v-for="g in grades" :key="g" :label="g" :value="g" />
          </el-select>
        </div>
      </div>
      <div class="cp-panel-body">
        <div v-if="!orders.length" class="cp-empty">暂无任务单</div>
        <div v-else class="mr-grid">
          <div v-for="o in orders" :key="o.id" class="mr-card" :class="{ 'is-over': localOver(o) }" :data-order-id="o.id">
            <div class="mr-head">
              <b>{{ o.orderNo }}</b>
              <span class="cp-badge" :style="{ background: gradeColor(o.strengthGrade) }">{{ o.strengthGrade }}</span>
              <span class="cp-tag" :class="orderClass(o.status)">{{ o.statusName }}</span>
              <span class="cp-muted" style="margin-left: auto">{{ o.mixerName || '未排产' }}</span>
            </div>
            <div class="mr-proj">{{ o.projectName }}</div>
            <div class="cp-muted mr-line">
              {{ o.contractor || '—' }} · {{ o.pourPart || '—' }}
            </div>
            <div class="cp-muted mr-line">
              计划 {{ o.plannedVolume }} m³ · 坍落度 {{ o.slump || '—' }} · 已签收 {{ o.deliveredVolume }} m³
            </div>

            <div class="mr-body">
              <div class="mr-chart">
                <div class="mr-donut" :style="{ background: conic(o) }">
                  <div class="mr-hole">
                    <div class="mr-wb" :class="{ bad: localOver(o) }">{{ localRatio(o) }}</div>
                    <div class="mr-wb-label">水胶比</div>
                  </div>
                </div>
                <div class="mr-legend">
                  <div v-for="i in o.items" :key="i.id" class="mr-legend-item">
                    <i :style="{ background: color(i.materialCode) }"></i>
                    <span>{{ shortName(i.materialName) }}</span>
                    <b>{{ i.ratioPercent }}%</b>
                  </div>
                </div>
              </div>

              <div class="mr-table">
                <div class="mr-row mr-row-head">
                  <span>材料</span><span>每方用量 kg</span><span>占比</span>
                </div>
                <div v-for="i in o.items" :key="i.id" class="mr-row">
                  <span class="mr-mat">
                    <i :style="{ background: color(i.materialCode) }"></i>{{ i.materialName }}
                  </span>
                  <span>
                    <el-input-number
                      v-model="i.dosage"
                      size="small"
                      :min="1"
                      :max="2000"
                      :step="5"
                      :precision="2"
                      :disabled="o.status === 'DONE'"
                      controls-position="right"
                      style="width: 118px"
                      @change="(val, old) => onDosageChange(o, i, old)"
                    />
                  </span>
                  <span class="mr-pct">{{ pctOf(o, i) }}%</span>
                </div>
                <div class="mr-row mr-row-sum">
                  <span>每方合计</span><span>{{ sumDosage(o) }} kg</span><span></span>
                </div>
              </div>
            </div>

            <div class="mr-foot">
              <span :class="localOver(o) ? 'mr-warn' : 'mr-ok'">
                水胶比 {{ localRatio(o) }} / 上限 {{ o.ratioLimit }}
                <b v-if="localOver(o)">· 超标</b>
                <b v-else>· 合格</b>
              </span>
              <span class="cp-muted">胶材 {{ o.binderDosage }} kg/m³</span>
              <span style="margin-left: auto; display: flex; gap: 6px">
                <el-button size="small" @click="viewChain(o)">试块检测链</el-button>
                <el-button v-if="o.status === 'PENDING'" size="small" type="primary" @click="start(o)">开工校验</el-button>
                <el-button v-if="o.status === 'PRODUCING'" size="small" @click="finish(o)">收尾完成</el-button>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { goQualityForOrder, nav } from '../nav'

const orders = ref([])
const grades = ['C25', 'C30', 'C35', 'C40']
const filter = reactive({ status: '', grade: '' })

const COLORS = {
  CEMENT: '#2f4f4f',
  FLYASH: '#8a6f4f',
  SLAG: '#4f7d8a',
  SAND: '#c9a227',
  STONE: '#7d8a8a',
  WATER: '#5b8fa8',
  ADMIXTURE: '#a0522d'
}

function color(code) {
  return COLORS[code] || '#98a5a4'
}

function shortName(name) {
  if (!name) return ''
  return name.replace(/\s+/g, '').slice(0, 4)
}

function sumDosage(o) {
  return (o.items || []).reduce((s, i) => s + Number(i.dosage || 0), 0).toFixed(2)
}

function pctOf(o, item) {
  const total = Number(sumDosage(o))
  if (!total) return '0.00'
  return ((Number(item.dosage || 0) / total) * 100).toFixed(2)
}

function binderOf(o) {
  return (o.items || [])
    .filter((i) => ['CEMENT', 'FLYASH', 'SLAG'].includes(i.materialCode))
    .reduce((s, i) => s + Number(i.dosage || 0), 0)
}

function waterOf(o) {
  const w = (o.items || []).find((i) => i.materialCode === 'WATER')
  return Number(w?.dosage || 0)
}

function localRatio(o) {
  const binder = binderOf(o)
  if (!binder) return '0.000'
  return (waterOf(o) / binder).toFixed(3)
}

function localOver(o) {
  return Number(localRatio(o)) > Number(o.ratioLimit || 0.6)
}

function conic(o) {
  const items = o.items || []
  const total = Number(sumDosage(o)) || 1
  let acc = 0
  const parts = items.map((i) => {
    const p = (Number(i.dosage || 0) / total) * 100
    const seg = `${color(i.materialCode)} ${acc.toFixed(2)}% ${(acc + p).toFixed(2)}%`
    acc += p
    return seg
  })
  return `conic-gradient(${parts.join(', ')})`
}

async function load() {
  try {
    const res = await api.orders({ status: filter.status || undefined, grade: filter.grade || undefined })
    orders.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function onDosageChange(order, item, oldValue) {
  const limit = Number(order.ratioLimit || 0.6)
  const ratio = Number(localRatio(order))
  if (ratio > limit) {
    item.dosage = oldValue
    ElMessage.error(
      `水胶比 ${ratio.toFixed(3)} 超过 ${order.strengthGrade} 上限 ${limit}，已撤销本次修改`
    )
    return
  }
  try {
    const res = await api.updateItems(
      order.id,
      order.items.map((i) => ({ materialCode: i.materialCode, dosage: i.dosage }))
    )
    const fresh = res.data
    const idx = orders.value.findIndex((x) => x.id === order.id)
    if (idx >= 0) orders.value[idx] = fresh
    ElMessage.success(`配合比已重算，水胶比 ${fresh.waterBinderRatio}`)
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  }
}

async function start(order) {
  try {
    await api.startOrder(order.id)
    ElMessage.success('筒仓库存校验通过，任务单已开工')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function finish(order) {
  try {
    await api.finishOrder(order.id)
    ElMessage.success('任务单已收尾')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function gradeColor(grade) {
  return { C25: '#5b8fa8', C30: '#2f4f4f', C35: '#8a6f4f', C40: '#8c4a3a' }[grade] || '#2f4f4f'
}

function orderClass(status) {
  return { PENDING: 'warn', PRODUCING: 'run', DONE: 'ok' }[status] || ''
}

/** 从任务单查看完整检测链（留样 → 7d/28d → 复检） */
function viewChain(order) {
  goQualityForOrder(order.id)
}

// 从试块详情页跳回任务单时清空筛选并定位
async function handleMixOrder(orderId) {
  if (orderId == null) return
  filter.status = ''
  filter.grade = ''
  await load()
  await nextTick()
  const el = document.querySelector(`[data-order-id="${orderId}"]`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  nav.mixOrderId = null
}

watch(() => nav.mixOrderId, handleMixOrder)

onMounted(() => {
  load()
  if (nav.mixOrderId != null) handleMixOrder(nav.mixOrderId)
})
</script>

<style scoped>
.mr-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(520px, 1fr));
  gap: 14px;
}

.mr-card {
  border: 1px solid var(--cp-border);
  border-radius: 8px;
  padding: 12px 14px 10px;
  background: linear-gradient(180deg, #ffffff, #fbfdfc);
}

.mr-card.is-over {
  border-color: #e3bcb7;
  background: linear-gradient(180deg, #fffaf9, #fdf3f2);
}

.mr-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.mr-proj {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
}

.mr-line {
  font-size: 11px;
  margin-top: 2px;
}

.mr-body {
  display: flex;
  gap: 16px;
  margin-top: 10px;
}

.mr-chart {
  width: 168px;
  flex: 0 0 168px;
}

.mr-donut {
  width: 148px;
  height: 148px;
  border-radius: 50%;
  position: relative;
  margin: 0 auto;
  box-shadow: 0 2px 8px rgba(47, 79, 79, 0.18);
}

.mr-hole {
  position: absolute;
  inset: 26px;
  background: #fff;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.mr-wb {
  font-size: 20px;
  font-weight: 700;
  color: var(--cp-primary);
  font-variant-numeric: tabular-nums;
}

.mr-wb.bad {
  color: var(--cp-danger);
}

.mr-wb-label {
  font-size: 11px;
  color: var(--cp-muted);
  margin-top: 2px;
}

.mr-legend {
  margin-top: 8px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 8px;
  font-size: 11px;
}

.mr-legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.mr-legend-item i,
.mr-mat i {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  display: inline-block;
  flex: 0 0 9px;
}

.mr-legend-item b {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.mr-table {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  overflow: hidden;
}

.mr-row {
  display: grid;
  grid-template-columns: 1fr 130px 60px;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  font-size: 12px;
  border-bottom: 1px solid #eef2f1;
}

.mr-row:last-child {
  border-bottom: none;
}

.mr-row-head {
  background: #f4f7f6;
  font-weight: 600;
  color: var(--cp-muted);
  font-size: 11px;
}

.mr-row-sum {
  background: #f4f7f6;
  font-weight: 600;
}

.mr-mat {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mr-pct {
  text-align: right;
  color: var(--cp-muted);
  font-variant-numeric: tabular-nums;
}

.mr-foot {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--cp-border);
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.mr-ok {
  color: var(--cp-ok);
}

.mr-warn {
  color: var(--cp-danger);
}
</style>
