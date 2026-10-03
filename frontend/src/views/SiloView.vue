<template>
  <div>
    <!-- 筒仓液位柱 -->
    <div class="cp-panel">
      <div class="cp-panel-head">
        <div class="cp-panel-title">原材料筒仓液位</div>
        <div class="cp-panel-sub">低于安全库存的筒仓会闪烁告警，可直接发起补料申请</div>
        <div style="margin-left: auto">
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>
      <div class="cp-panel-body">
        <div v-if="!silos.length" class="cp-empty">暂无筒仓数据</div>
        <div v-else class="sv-grid">
          <div
            v-for="s in silos"
            :key="s.id"
            class="sv-silo"
            :class="{ alarm: s.lowStock }"
          >
            <div class="sv-cyl">
              <div class="sv-cap" :style="{ background: capColor(s.materialCode) }"></div>
              <div class="sv-tube">
                <div
                  class="sv-fill"
                  :style="{ height: fillH(s) + '%', background: fillColor(s.materialCode) }"
                ></div>
                <div class="sv-safe" :style="{ bottom: safeH(s) + '%' }">
                  <span>安全 {{ s.safetyStockTon }}t</span>
                </div>
                <div class="sv-pct">{{ s.fillPercent }}%</div>
              </div>
            </div>
            <div class="sv-name">{{ s.name }}</div>
            <div class="sv-meta">{{ s.materialName }} · 仓容 {{ s.capacityTon }}t</div>
            <div class="sv-stock" :class="{ low: s.lowStock }">
              现存 {{ s.stockTon }} t
              <span v-if="s.lowStock" class="sv-gap">缺 {{ s.gapTon }}t</span>
            </div>
            <div class="sv-occupy">
              待审批占用 <b>{{ s.pendingOccupiedTon }}</b> t · 可申请余量
              <b :class="{ zero: Number(s.availableTon) <= 0 }">{{ s.availableTon }}</b> t
            </div>
            <el-button size="small" type="primary" plain @click="openApply(s)">补料</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="sv-row">
      <!-- 补料审批 -->
      <div class="cp-panel" style="flex: 2">
        <div class="cp-panel-head">
          <div class="cp-panel-title">补料申请与审批</div>
          <div class="cp-panel-sub">通过后自动回补筒仓库存</div>
          <div style="margin-left: auto; font-size: 12px">
            <span class="cp-muted">待审批</span>
            <b style="color: var(--cp-warn)">{{ pending.length }}</b>
          </div>
        </div>
        <div class="cp-panel-body">
          <div v-if="!requests.length" class="cp-empty">暂无补料申请</div>
          <div v-for="r in requests" :key="r.id" class="sv-req">
            <div class="sv-req-left">
              <div class="sv-req-top">
                <b>{{ r.requestNo }}</b>
                <span class="cp-tag" :class="reqClass(r.status)">{{ r.statusName }}</span>
              </div>
              <div class="sv-req-line">
                {{ r.siloName }} · {{ r.materialName }} · 申请 <b>{{ r.quantityTon }}</b> 吨
              </div>
              <div v-if="r.status === 'PENDING' && approveMargin(r)" class="sv-req-margin">
                排除本单后审批余量
                <b :class="{ 'sv-over': approveMargin(r).over }">{{ approveMargin(r).margin }}</b> 吨
                <span v-if="approveMargin(r).over" class="sv-over-text">（通过将超仓容，审批会被拒绝）</span>
              </div>
              <div class="cp-muted" style="font-size: 11px">
                {{ r.applicant }} · {{ fmtTime(r.createdAt) }}
                <span v-if="r.remark"> · {{ r.remark }}</span>
              </div>
            </div>
            <div class="sv-req-right">
              <template v-if="r.status === 'PENDING'">
                <el-button size="small" type="primary" @click="approve(r)">通过</el-button>
                <el-button size="small" @click="reject(r)">驳回</el-button>
              </template>
              <span v-else class="cp-muted" style="font-size: 11px">
                {{ r.decidedBy }} · {{ fmtTime(r.decidedAt) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 工地签收汇总 -->
      <div class="cp-panel" style="flex: 1">
        <div class="cp-panel-head">
          <div class="cp-panel-title">工地签收汇总</div>
        </div>
        <div class="cp-panel-body">
          <div v-if="!sites.length" class="cp-empty">暂无签收数据</div>
          <div v-for="(s, idx) in sites" :key="s.projectName" class="sv-site">
            <span class="sv-site-no">{{ idx + 1 }}</span>
            <div class="sv-site-body">
              <div class="sv-site-name">{{ s.projectName }}</div>
              <div class="sv-site-bar">
                <i :style="{ width: siteWidth(s) + '%' }"></i>
              </div>
            </div>
            <div class="sv-site-num">
              <b>{{ s.deliveredVolume }}</b>
              <span>m³ / {{ s.ticketCount }} 车</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialog" title="发起补料申请" width="420px">
      <div v-if="cur.id" class="sv-info">
        <div><span class="cp-muted">筒仓</span> {{ cur.name }}（{{ cur.materialName }}）</div>
        <div><span class="cp-muted">仓容</span> {{ cur.capacityTon }} t</div>
        <div><span class="cp-muted">当前库存</span> <b>{{ cur.stockTon }}</b> t</div>
        <div><span class="cp-muted">待审批占用</span> <b>{{ cur.pendingOccupiedTon }}</b> t（其他待审批申请合计）</div>
        <div>
          <span class="cp-muted">可申请余量</span>
          <b :class="{ 'sv-over': Number(cur.availableTon) <= 0 }">{{ cur.availableTon }}</b> t
        </div>
        <div><span class="cp-muted">安全库存</span> {{ cur.safetyStockTon }} t</div>
      </div>
      <el-form label-width="86px" size="small" style="margin-top: 12px">
        <el-form-item label="补料量(吨)">
          <el-input-number
            v-model="applyForm.quantityTon"
            :min="1"
            :max="maxQuantity"
            :step="5"
          />
        </el-form-item>
        <el-form-item label="申请人">
          <el-input v-model="applyForm.applicant" style="width: 180px" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="applyForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div v-if="applyGap > 0" class="sv-over-tip">
        超出可申请余量 {{ applyGap }} 吨，请调减补料量后再提交
      </div>
      <div v-else class="sv-after-tip">
        提交后库存口径：{{ cur.stockTon }} + {{ cur.pendingOccupiedTon }} + {{ applyForm.quantityTon }}
        = {{ projectedTotal }} t / {{ cur.capacityTon }} t
      </div>
      <div style="text-align: right">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" :disabled="applyGap > 0" @click="submitApply">提交申请</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, fmtTime } from '../api'

const silos = ref([])
const requests = ref([])
const sites = ref([])
const dialog = ref(false)
const cur = ref({})
const applyForm = ref({ quantityTon: 50, applicant: '调度中心', remark: '' })

const pending = computed(() => requests.value.filter((r) => r.status === 'PENDING'))

// 申请弹窗内的实时口径：可申请余量 / 缺口 / 提交后合计，全部只取服务端下发的库存与占用
const availableNow = computed(() => Number(cur.value.availableTon ?? 0))
const applyGap = computed(() => {
  const qty = Number(applyForm.value.quantityTon || 0)
  return qty > availableNow.value ? Number((qty - availableNow.value).toFixed(2)) : 0
})
const projectedTotal = computed(() => {
  const qty = Number(applyForm.value.quantityTon || 0)
  return (Number(cur.value.stockTon || 0) + Number(cur.value.pendingOccupiedTon || 0) + qty).toFixed(2)
})
const maxQuantity = computed(() => Math.max(Math.min(200, Math.floor(availableNow.value)), 1))

const MATERIAL_COLOR = {
  CEMENT: 'linear-gradient(180deg,#7e918d,#4d5f5c)',
  FLYASH: 'linear-gradient(180deg,#a08f7f,#6f6153)',
  SLAG: 'linear-gradient(180deg,#6f9aa6,#3f6b76)',
  SAND: 'linear-gradient(180deg,#dcbb52,#a8871a)',
  STONE: 'linear-gradient(180deg,#8b9994,#57645f)'
}

function fillColor(code) {
  return MATERIAL_COLOR[code] || 'linear-gradient(180deg,#7e918d,#4d5f5c)'
}

function capColor(code) {
  const map = {
    CEMENT: '#e7eeec',
    FLYASH: '#f0e9e2',
    SLAG: '#e3edf0',
    SAND: '#f7efd8',
    STONE: '#e9eeec'
  }
  return map[code] || '#e7eeec'
}

function fillH(s) {
  return Math.max(Math.min(Number(s.fillPercent || 0), 100), 0)
}

function safeH(s) {
  const cap = Number(s.capacityTon || 1)
  return Math.min((Number(s.safetyStockTon || 0) / cap) * 100, 100)
}

function siteWidth(s) {
  const max = Math.max(...sites.value.map((x) => Number(x.deliveredVolume || 0)), 1)
  return (Number(s.deliveredVolume || 0) / max) * 100
}

async function load() {
  try {
    const [sl, rq, st] = await Promise.all([api.silos(), api.replenishes(), api.statSite()])
    silos.value = sl.data || []
    requests.value = rq.data || []
    sites.value = st.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openApply(silo) {
  cur.value = silo
  // 默认补到安全库存与仓容之间的可行量，但不超过可申请余量
  const target = Math.max(Number(silo.safetyStockTon || 0) - Number(silo.stockTon || 0), 5)
  const qty = Math.min(200, Math.max(1, Math.ceil(target)), Number(silo.availableTon || 0))
  applyForm.value = { quantityTon: qty || 1, applicant: '调度中心', remark: '' }
  dialog.value = true
}

async function submitApply() {
  if (applyGap.value > 0) {
    ElMessage.error(`超出可申请余量 ${applyGap.value} 吨，请调减补料量`)
    return
  }
  try {
    await api.applyReplenish({ siloId: cur.value.id, ...applyForm.value })
    ElMessage.success('补料申请已提交，等待审批')
    dialog.value = false
    await load()
  } catch (e) {
    // 服务端为最终口径（并发/审批期间数据可能已变化），拒绝后刷新保持口径一致
    dialog.value = false
    ElMessage.error(e.message)
    await load()
  }
}

async function approve(row) {
  try {
    await api.approveReplenish(row.id, '调度主管 周炯')
    ElMessage.success('已通过，筒仓库存已回补')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  }
}

async function reject(row) {
  try {
    await api.rejectReplenish(row.id, '调度主管 周炯', '暂缓采购')
    ElMessage.success('已驳回，占用余量已释放')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  }
}

/** 审批口径：排除该单自身后的可申请余量 = 仓容 - 库存 - (待审批占用 - 本单量) */
function approveMargin(row) {
  const s = silos.value.find((x) => x.id === row.siloId)
  if (!s) return null
  const margin = Number(s.availableTon || 0) + Number(row.quantityTon || 0)
  return {
    margin: margin.toFixed(2),
    over: Number(row.quantityTon || 0) > margin
  }
}

function reqClass(status) {
  return { PENDING: 'warn', APPROVED: 'ok', REJECTED: 'danger' }[status] || ''
}

onMounted(load)
</script>

<style scoped>
.sv-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(108px, 1fr));
  gap: 10px;
  overflow-x: auto;
}

.sv-silo {
  text-align: center;
  padding: 8px 4px 10px;
  border: 1px solid var(--cp-border);
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff, #f7fbfa);
}

.sv-cyl {
  width: 86px;
  margin: 0 auto 8px;
}

.sv-cap {
  height: 20px;
  border-radius: 50%;
  border: 2px solid #b9c8c6;
  margin-bottom: -7px;
  position: relative;
  z-index: 2;
}

.sv-tube {
  position: relative;
  height: 176px;
  border: 2px solid #b9c8c6;
  border-radius: 6px 6px 16px 16px / 6px 6px 22px 22px;
  overflow: hidden;
  background: linear-gradient(90deg, #f1f5f4, #dee7e6 48%, #f1f5f4);
}

.sv-fill {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  transition: height 0.4s ease;
}

.sv-fill::before {
  content: '';
  position: absolute;
  top: -6px;
  left: 0;
  right: 0;
  height: 12px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.32);
}

.sv-safe {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  border-top: 2px dashed #c26a1b;
  z-index: 3;
}

.sv-safe span {
  position: absolute;
  right: 2px;
  top: -14px;
  font-size: 10px;
  color: var(--cp-warn);
  background: rgba(255, 255, 255, 0.9);
  border-radius: 3px;
  padding: 0 3px;
}

.sv-pct {
  position: absolute;
  left: 0;
  right: 0;
  top: 42%;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.45);
}

.sv-silo.alarm .sv-tube {
  animation: sv-blink 1.1s infinite;
}

.sv-silo.alarm {
  border-color: #e3bcb7;
  background: linear-gradient(180deg, #fffaf9, #fdf2f1);
}

@keyframes sv-blink {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(178, 58, 47, 0.05);
    border-color: #b9c8c6;
  }
  50% {
    box-shadow: 0 0 0 5px rgba(178, 58, 47, 0.28);
    border-color: #b23a2f;
  }
}

.sv-name {
  font-size: 12px;
  font-weight: 600;
}

.sv-meta {
  font-size: 10px;
  color: var(--cp-muted);
  margin-bottom: 2px;
}

.sv-stock {
  font-size: 13px;
  font-weight: 700;
  color: var(--cp-primary);
  margin-bottom: 6px;
  font-variant-numeric: tabular-nums;
}

.sv-stock.low {
  color: var(--cp-danger);
}

.sv-gap {
  font-size: 10px;
  font-weight: 400;
  color: var(--cp-danger);
  margin-left: 4px;
}

.sv-occupy {
  font-size: 10px;
  color: var(--cp-muted);
  margin: -2px 0 6px;
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
}

.sv-occupy b {
  color: var(--cp-warn);
}

.sv-occupy b.zero,
.sv-over {
  color: var(--cp-danger) !important;
}

.sv-req-margin {
  font-size: 11px;
  color: var(--cp-muted);
  font-variant-numeric: tabular-nums;
}

.sv-over-text {
  color: var(--cp-danger);
}

.sv-over-tip {
  margin: 4px 0 10px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--cp-danger);
  background: #fdf2f1;
  border: 1px solid #e3bcb7;
  border-radius: 6px;
}

.sv-after-tip {
  margin: 4px 0 10px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--cp-muted);
  background: #f4f8f7;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  font-variant-numeric: tabular-nums;
}

.sv-row {
  display: flex;
  gap: 14px;
  margin-top: 14px;
  align-items: flex-start;
}

.sv-req {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  margin-bottom: 8px;
}

.sv-req-left {
  flex: 1;
  min-width: 0;
}

.sv-req-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.sv-req-line {
  font-size: 12px;
  margin: 2px 0;
}

.sv-req-right {
  display: flex;
  gap: 6px;
  align-items: center;
}

.sv-site {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--cp-border);
}

.sv-site-no {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--cp-primary);
  color: #fff;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 18px;
}

.sv-site-body {
  flex: 1;
  min-width: 0;
}

.sv-site-name {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sv-site-bar {
  height: 6px;
  background: #eef2f1;
  border-radius: 3px;
  margin-top: 3px;
  overflow: hidden;
}

.sv-site-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #4d7a72, #2f4f4f);
}

.sv-site-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.sv-site-num b {
  display: block;
  font-size: 13px;
  color: var(--cp-primary);
}

.sv-site-num span {
  font-size: 10px;
  color: var(--cp-muted);
}

.sv-info {
  background: #f4f8f7;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.9;
}
</style>
