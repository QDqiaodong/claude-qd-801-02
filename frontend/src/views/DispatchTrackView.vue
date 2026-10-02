<template>
  <div>
    <div class="cp-panel">
      <div class="cp-panel-head">
        <div class="cp-panel-title">罐车发车排队轨道</div>
        <div class="cp-panel-sub">按住胶囊左右拖拽换排队顺序；点胶囊推进「装车 → 出厂 → 到工地 → 签收」</div>
        <div style="margin-left: auto">
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>
      <div class="cp-panel-body">
        <div class="dt-stations">
          <span v-for="s in stations" :key="s" class="dt-station">{{ s }}</span>
        </div>

        <div class="dt-track">
          <div class="dt-rail"></div>
          <div
            v-for="(t, index) in trucks"
            :key="t.id"
            class="dt-capsule"
            :class="[statusClass(t.status), { dragging: dragIndex === index }]"
            draggable="true"
            @dragstart="onDragStart(index, $event)"
            @dragover.prevent
            @dragenter.prevent
            @drop.prevent="onDrop(index)"
            @dragend="dragIndex = -1"
            @click="openDialog(t)"
          >
            <div class="dt-seq">{{ index + 1 }}</div>
            <div class="dt-cap-body">
              <div class="dt-plate">{{ t.plateNo }}</div>
              <div class="dt-driver">{{ t.driverName }} · {{ t.capacity }} m³</div>
            </div>
            <div class="dt-state">{{ statusName(t.status) }}</div>
          </div>
          <div v-if="!trucks.length" class="cp-empty">暂无罐车</div>
        </div>

        <div class="dt-tip">
          排队中的罐车按序号依次装车；只有「待命」车辆可以被派车发料。
        </div>
      </div>
    </div>

    <div class="cp-panel" style="margin-top: 14px">
      <div class="cp-panel-head">
        <div class="cp-panel-title">今日发车单</div>
        <div class="cp-panel-sub">装车 / 出厂 / 到工地 / 签收 时间轴</div>
      </div>
      <div class="cp-panel-body">
        <div v-if="!tickets.length" class="cp-empty">暂无发车记录</div>
        <div v-else class="dt-list">
          <div v-for="k in tickets" :key="k.id" class="dt-ticket">
            <div class="dt-ticket-head">
              <b>{{ k.ticketNo }}</b>
              <span class="cp-badge" :style="{ background: gradeColor(k.strengthGrade) }">{{ k.strengthGrade }}</span>
              <span class="cp-tag" :class="ticketClass(k.status)">{{ k.statusName }}</span>
              <span class="cp-muted">{{ k.plateNo }} · {{ k.driverName }}</span>
              <span class="cp-muted" style="margin-left: auto">{{ k.projectName }}</span>
            </div>
            <div class="dt-timeline">
              <div class="dt-node" :class="{ done: k.loadTime }">
                <span class="dt-dot"></span>
                <span class="dt-label">装车</span>
                <span class="dt-time">{{ fmtTime(k.loadTime) }}</span>
                <span class="dt-vol">{{ k.loadVolume }} m³</span>
              </div>
              <div class="dt-node" :class="{ done: k.departTime }">
                <span class="dt-dot"></span>
                <span class="dt-label">出厂</span>
                <span class="dt-time">{{ fmtTime(k.departTime) }}</span>
              </div>
              <div class="dt-node" :class="{ done: k.arriveTime }">
                <span class="dt-dot"></span>
                <span class="dt-label">到工地</span>
                <span class="dt-time">{{ fmtTime(k.arriveTime) }}</span>
              </div>
              <div class="dt-node" :class="{ done: k.signVolume }">
                <span class="dt-dot"></span>
                <span class="dt-label">签收</span>
                <span class="dt-time">{{ fmtTime(k.arriveTime) }}</span>
                <span class="dt-vol">{{ k.signVolume ?? '—' }} m³</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 罐车详情 / 状态推进 -->
    <el-dialog v-model="dialog" :title="`${cur.plateNo || ''} · ${statusName(cur.status)}`" width="520px">
      <div v-if="cur.id">
        <div class="dt-info">
          <div><span class="cp-muted">司机</span> {{ cur.driverName }}（{{ cur.driverPhone }}）</div>
          <div><span class="cp-muted">容积</span> {{ cur.capacity }} m³</div>
          <div><span class="cp-muted">当前状态</span> {{ statusName(cur.status) }}</div>
        </div>

        <!-- 待命：派车 -->
        <div v-if="cur.status === 'STANDBY'" class="dt-block">
          <div class="dt-sub">派车装料</div>
          <el-form label-width="90px" size="small">
            <el-form-item label="任务单">
              <el-select v-model="cmd.orderId" style="width: 100%" placeholder="选择生产中的任务单">
                <el-option
                  v-for="o in activeOrders"
                  :key="o.id"
                  :label="`${o.orderNo} · ${o.projectName}`"
                  :value="o.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="装车方量">
              <el-input-number v-model="cmd.loadVolume" :min="1" :max="20" :step="0.5" :precision="2" />
              <span class="cp-muted" style="margin-left: 8px">罐体容积 {{ cur.capacity }} m³</span>
            </el-form-item>
          </el-form>
          <div style="text-align: right">
            <el-button size="small" type="primary" @click="doDispatch">确认装车</el-button>
          </div>
        </div>

        <!-- 有在途发车单：状态推进 -->
        <div v-else-if="activeTicket" class="dt-block">
          <div class="dt-sub">发车单 {{ activeTicket.ticketNo }}</div>
          <div class="dt-info">
            <div><span class="cp-muted">任务单</span> {{ activeTicket.orderNo }} · {{ activeTicket.projectName }}</div>
            <div><span class="cp-muted">装车方量</span> {{ activeTicket.loadVolume }} m³</div>
            <div><span class="cp-muted">装车时间</span> {{ fmtTime(activeTicket.loadTime) }}</div>
          </div>
          <div class="dt-actions">
            <el-button
              v-if="activeTicket.status === 'LOADED'"
              size="small"
              type="primary"
              @click="advance('depart')"
            >
              登记出厂
            </el-button>
            <el-button
              v-if="activeTicket.status === 'DEPARTED'"
              size="small"
              type="primary"
              @click="advance('arrive')"
            >
              登记到工地
            </el-button>
            <template v-if="activeTicket.status === 'ARRIVED'">
              <el-input-number
                v-model="signVolume"
                :min="0.5"
                :max="Number(activeTicket.loadVolume)"
                :step="0.1"
                :precision="2"
                size="small"
              />
              <el-button size="small" type="primary" @click="advance('sign')">签收回单</el-button>
            </template>
            <span v-if="activeTicket.status === 'SIGNED'" class="cp-tag ok">
              已签收 {{ activeTicket.signVolume }} m³
            </span>
          </div>
          <div class="cp-muted" style="margin-top: 8px">
            签收方量不得超过装车方量 {{ activeTicket.loadVolume }} m³
          </div>
        </div>

        <div v-if="cur.status === 'WASHING'" class="dt-block">
          <div class="dt-sub">洗车回场</div>
          <el-button size="small" type="primary" @click="ready">洗车完毕，回场待命</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, fmtTime } from '../api'

const trucks = ref([])
const tickets = ref([])
const orders = ref([])
const dialog = ref(false)
const cur = ref({})
const dragIndex = ref(-1)
const signVolume = ref(10)
const cmd = ref({ orderId: null, loadVolume: 10 })
const stations = ['待命区', '装载位', '出厂口', '在途', '工地卸料', '清洗回场']

const activeOrders = computed(() => orders.value.filter((o) => o.status === 'PRODUCING'))

const activeTicket = computed(() => {
  if (!cur.value.id) return null
  const list = tickets.value
    .filter((k) => k.truckId === cur.value.id)
    .sort((a, b) => String(b.loadTime || '').localeCompare(String(a.loadTime || '')))
  return list.length ? list[0] : null
})

async function load() {
  try {
    const [tr, tk, od] = await Promise.all([api.trucks(), api.tickets(), api.orders()])
    trucks.value = tr.data || []
    tickets.value = tk.data || []
    orders.value = od.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function onDragStart(index, event) {
  dragIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

async function onDrop(index) {
  const from = dragIndex.value
  dragIndex.value = -1
  if (from < 0 || from === index) return
  const arr = [...trucks.value]
  const [moved] = arr.splice(from, 1)
  arr.splice(index, 0, moved)
  trucks.value = arr
  try {
    await api.reorderTrucks(arr.map((t) => t.id))
    ElMessage.success('排队顺序已保存')
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  }
}

function openDialog(truck) {
  cur.value = truck
  cmd.value = { orderId: activeOrders.value[0]?.id ?? null, loadVolume: Number(truck.capacity) || 10 }
  signVolume.value = Number(activeTicketFor(truck)?.loadVolume || 10)
  dialog.value = true
}

function activeTicketFor(truck) {
  const list = tickets.value
    .filter((k) => k.truckId === truck.id)
    .sort((a, b) => String(b.loadTime || '').localeCompare(String(a.loadTime || '')))
  return list.length ? list[0] : null
}

async function doDispatch() {
  if (!cmd.value.orderId) {
    ElMessage.warning('请选择任务单')
    return
  }
  try {
    await api.dispatch({ orderId: cmd.value.orderId, truckId: cur.value.id, loadVolume: cmd.value.loadVolume })
    ElMessage.success('已装车，等待出厂')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function advance(action) {
  try {
    await api.advance(activeTicket.value.id, action, action === 'sign' ? signVolume.value : undefined)
    ElMessage.success('状态已推进')
    await load()
    const fresh = trucks.value.find((t) => t.id === cur.value.id)
    if (fresh) cur.value = fresh
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function ready() {
  try {
    await api.truckReady(cur.value.id)
    ElMessage.success('车辆已回场待命')
    await load()
    const fresh = trucks.value.find((t) => t.id === cur.value.id)
    if (fresh) cur.value = fresh
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function statusName(status) {
  const map = {
    STANDBY: '待命',
    LOADING: '装料',
    ON_ROAD: '在途',
    UNLOADING: '卸料',
    WASHING: '清洗'
  }
  return map[status] || status || ''
}

function statusClass(status) {
  return {
    STANDBY: 'st-standby',
    LOADING: 'st-loading',
    ON_ROAD: 'st-onroad',
    UNLOADING: 'st-unloading',
    WASHING: 'st-washing'
  }[status] || ''
}

function ticketClass(status) {
  return { LOADED: 'warn', DEPARTED: 'run', ARRIVED: 'run', SIGNED: 'ok' }[status] || ''
}

function gradeColor(grade) {
  return { C25: '#5b8fa8', C30: '#2f4f4f', C35: '#8a6f4f', C40: '#8c4a3a' }[grade] || '#2f4f4f'
}

onMounted(load)
</script>

<style scoped>
.dt-stations {
  display: flex;
  gap: 26px;
  padding: 0 4px 8px;
  font-size: 11px;
  color: var(--cp-muted);
}

.dt-station {
  position: relative;
  padding-left: 10px;
}

.dt-station::before {
  content: '';
  position: absolute;
  left: 0;
  top: 5px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  border: 1px solid var(--cp-primary);
}

.dt-track {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 4px 26px;
  overflow-x: auto;
  background: linear-gradient(180deg, #f7faf9, #eef3f2);
  border: 1px solid var(--cp-border);
  border-radius: 8px;
}

.dt-rail {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 6px;
  background: repeating-linear-gradient(90deg, #cfdad8, #cfdad8 14px, #e4ebea 14px, #e4ebea 20px);
}

.dt-capsule {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px 8px 8px;
  border-radius: 24px;
  background: #ffffff;
  border: 1px solid var(--cp-border);
  box-shadow: 0 2px 6px rgba(47, 79, 79, 0.12);
  cursor: grab;
  user-select: none;
  transition: transform 0.16s, box-shadow 0.16s;
}

.dt-capsule:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 14px rgba(47, 79, 79, 0.2);
}

.dt-capsule.dragging {
  opacity: 0.45;
  transform: scale(0.97);
}

.dt-seq {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--cp-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dt-cap-body {
  line-height: 1.35;
}

.dt-plate {
  font-size: 13px;
  font-weight: 700;
}

.dt-driver {
  font-size: 11px;
  color: var(--cp-muted);
}

.dt-state {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  color: #fff;
  background: #7d8a8a;
}

.st-standby .dt-state {
  background: #3f7d5f;
}

.st-loading .dt-state {
  background: #c26a1b;
}

.st-onroad .dt-state {
  background: #2f6f8f;
}

.st-unloading .dt-state {
  background: #7a5c9e;
}

.st-washing .dt-state {
  background: #8a8f5a;
}

.st-standby {
  border-color: #bcd8ca;
}

.st-loading {
  border-color: #e5cdae;
}

.dt-tip {
  margin-top: 8px;
  font-size: 11px;
  color: var(--cp-muted);
}

.dt-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(430px, 1fr));
  gap: 12px;
}

.dt-ticket {
  border: 1px solid var(--cp-border);
  border-radius: 8px;
  padding: 10px 12px;
}

.dt-ticket-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.dt-timeline {
  display: flex;
  margin-top: 10px;
  position: relative;
}

.dt-node {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--cp-muted);
  position: relative;
}

.dt-node::before {
  content: '';
  position: absolute;
  top: 4px;
  left: -50%;
  width: 100%;
  height: 2px;
  background: var(--cp-border);
}

.dt-node:first-child::before {
  display: none;
}

.dt-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--cp-border);
  z-index: 1;
}

.dt-node.done .dt-dot {
  background: var(--cp-primary);
  border-color: var(--cp-primary);
}

.dt-node.done::before {
  background: var(--cp-primary);
}

.dt-label {
  font-size: 11px;
}

.dt-node.done .dt-label {
  color: var(--cp-primary);
  font-weight: 600;
}

.dt-time {
  font-variant-numeric: tabular-nums;
}

.dt-vol {
  color: var(--cp-primary);
}

.dt-info {
  background: #f4f8f7;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.9;
}

.dt-block {
  margin-top: 14px;
}

.dt-sub {
  font-size: 12px;
  font-weight: 700;
  color: var(--cp-primary);
  border-left: 3px solid var(--cp-primary);
  padding-left: 8px;
  margin-bottom: 8px;
}

.dt-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
</style>
