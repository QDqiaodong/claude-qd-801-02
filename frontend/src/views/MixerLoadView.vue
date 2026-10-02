<template>
  <div>
    <div class="cp-panel">
      <div class="cp-panel-head">
        <div class="cp-panel-title">主机产能负载（当日完成方量 / 计划方量）</div>
        <div class="cp-panel-sub">点竖条右侧滑出抽屉，可直接给该主机派发任务单</div>
        <div style="margin-left: auto; display: flex; gap: 14px; align-items: center">
          <span class="cp-legend"><i class="sw sw-done"></i>已完成</span>
          <span class="cp-legend"><i class="sw sw-over"></i>超计划</span>
          <span class="cp-legend"><i class="sw sw-plan"></i>计划线</span>
        </div>
      </div>
      <div class="cp-panel-body">
        <div v-if="!board.length" class="cp-empty">暂无主机数据</div>
        <div v-else class="ml-board">
          <div
            v-for="m in board"
            :key="m.mixerId"
            class="ml-bar"
            :class="{ 'is-over': m.overPlan, 'is-down': m.status === 'FAULT' || m.status === 'MAINTENANCE' }"
            @click="openDrawer(m)"
          >
            <div class="ml-bar-top">
              <span class="ml-num">{{ m.doneVolume }}</span>
              <span class="ml-unit">m³</span>
            </div>

            <div class="ml-track">
              <div class="ml-fill" :style="{ height: pct(m.doneVolume) + '%' }"></div>
              <div
                v-if="m.overPlan"
                class="ml-over"
                :style="{
                  bottom: pct(m.dailyPlanVolume) + '%',
                  height: Math.max(pct(m.doneVolume) - pct(m.dailyPlanVolume), 1.5) + '%'
                }"
              ></div>
              <div class="ml-planline" :style="{ bottom: pct(m.dailyPlanVolume) + '%' }"></div>
              <div class="ml-planlabel" :style="{ bottom: pct(m.dailyPlanVolume) + '%' }">
                计划 {{ m.dailyPlanVolume }}
              </div>
              <div class="ml-loadtag" :class="{ over: m.overPlan }">{{ m.loadRate }}%</div>
            </div>

            <div class="ml-name">{{ m.mixerName }}</div>
            <div class="ml-meta">{{ m.model }} · {{ m.theoreticalCapacity }} m³/h</div>
            <div class="ml-meta">{{ m.plantName }}</div>
            <div class="ml-foot">
              <span class="cp-tag" :class="statusClass(m.status)">{{ m.statusName }}</span>
              <span class="cp-muted">{{ m.orderCount }} 单</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧抽屉：派发任务单 -->
    <el-drawer v-model="drawer" :title="`派发任务单 · ${cur.mixerName || ''}`" size="430px">
      <div v-if="cur.mixerId">
        <div class="ml-drawer-info">
          <div><span class="cp-muted">主机</span> {{ cur.mixerCode }} / {{ cur.model }}</div>
          <div><span class="cp-muted">所属</span> {{ cur.plantName }}</div>
          <div><span class="cp-muted">状态</span> {{ cur.statusName }} · 理论产能 {{ cur.theoreticalCapacity }} m³/h</div>
          <div>
            <span class="cp-muted">完成</span> {{ cur.doneVolume }} m³ /
            <span class="cp-muted">计划</span> {{ cur.dailyPlanVolume }} m³（{{ cur.loadRate }}%）
          </div>
        </div>

        <div class="ml-planedit">
          <span class="cp-muted">当日计划方量</span>
          <el-input-number v-model="planVolume" :min="1" :step="10" size="small" />
          <el-button size="small" type="primary" @click="savePlan">保存</el-button>
          <el-button
            size="small"
            type="danger"
            plain
            style="margin-left: auto"
            :disabled="cur.status !== 'RUNNING' && cur.status !== 'IDLE'"
            @click="openFault"
          >
            临时故障停机
          </el-button>
        </div>

        <div class="ml-sub">该主机任务单</div>
        <div v-if="!myOrders.length" class="cp-empty" style="padding: 14px">暂无排产任务单</div>
        <div v-for="o in myOrders" :key="o.id" class="ml-order">
          <div class="ml-order-top">
            <b>{{ o.orderNo }}</b>
            <span class="cp-badge" :style="{ background: gradeColor(o.strengthGrade) }">{{ o.strengthGrade }}</span>
            <span class="cp-tag" :class="orderClass(o.status)">{{ o.statusName }}</span>
          </div>
          <div class="cp-muted">{{ o.projectName }} · {{ o.pourPart || '未指定部位' }}</div>
          <div class="cp-muted">
            计划 {{ o.plannedVolume }} m³ · 已签收 {{ o.deliveredVolume }} m³
          </div>
        </div>

        <div class="ml-sub">新建任务单</div>
        <el-form label-width="92px" size="small">
          <el-form-item label="工程名称">
            <el-input v-model="form.projectName" placeholder="如：滨海国际中心 A 座" />
          </el-form-item>
          <el-form-item label="施工单位">
            <el-input v-model="form.contractor" placeholder="如：中建三局集团有限公司" />
          </el-form-item>
          <el-form-item label="浇筑部位">
            <el-input v-model="form.pourPart" placeholder="如：三层剪力墙" />
          </el-form-item>
          <el-form-item label="强度等级">
            <el-select v-model="form.strengthGrade" style="width: 100%">
              <el-option v-for="g in grades" :key="g" :label="g" :value="g" />
            </el-select>
          </el-form-item>
          <el-form-item label="计划方量">
            <el-input-number v-model="form.plannedVolume" :min="1" :max="3000" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="坍落度">
            <el-select v-model="form.slump" style="width: 100%">
              <el-option v-for="s in slumps" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="计划时段">
            <el-date-picker
              v-model="range"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              format="MM-DD HH:mm"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <div style="text-align: right">
          <el-button size="small" @click="drawer = false">取消</el-button>
          <el-button size="small" type="primary" @click="submit">派发任务单</el-button>
        </div>
      </div>
    </el-drawer>

    <FaultShutdownDialog v-model="faultVisible" :mixer="cur" @confirmed="handleFaultConfirmed" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, fmtDateTime } from '../api'
import FaultShutdownDialog from './FaultShutdownDialog.vue'

const board = ref([])
const orders = ref([])
const drawer = ref(false)
const faultVisible = ref(false)
const cur = ref({})
const planVolume = ref(10)
const range = ref([])
const grades = ['C25', 'C30', 'C35', 'C40']
const slumps = ['120±30mm', '160±30mm', '180±30mm', '200±30mm']

const form = ref({
  projectName: '',
  contractor: '',
  pourPart: '',
  strengthGrade: 'C30',
  plannedVolume: 300,
  slump: '180±30mm',
  remark: ''
})

const scale = computed(() => {
  let max = 100
  for (const m of board.value) {
    max = Math.max(max, Number(m.dailyPlanVolume || 0), Number(m.doneVolume || 0))
  }
  return max * 1.18
})

function pct(value) {
  return Math.min((Number(value || 0) / scale.value) * 100, 100)
}

const myOrders = computed(() =>
  orders.value.filter((o) => o.mixerId === cur.value.mixerId)
)

async function load() {
  try {
    const [loadRes, orderRes] = await Promise.all([api.mixerLoad(), api.orders()])
    board.value = loadRes.data || []
    orders.value = orderRes.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openDrawer(m) {
  cur.value = m
  planVolume.value = Number(m.dailyPlanVolume)
  const start = new Date()
  start.setHours(8, 0, 0, 0)
  const end = new Date()
  end.setHours(18, 0, 0, 0)
  range.value = [start, end]
  drawer.value = true
}

async function savePlan() {
  try {
    await api.updateMixerPlan(cur.value.mixerId, planVolume.value)
    ElMessage.success('当日计划方量已更新')
    await load()
    const hit = board.value.find((m) => m.mixerId === cur.value.mixerId)
    if (hit) cur.value = hit
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openFault() {
  faultVisible.value = true
}

async function handleFaultConfirmed() {
  await load()
  const hit = board.value.find((m) => m.mixerId === cur.value.mixerId)
  if (hit) cur.value = hit
}

async function submit() {
  if (!form.value.projectName) {
    ElMessage.warning('请填写工程名称')
    return
  }
  if (!range.value || range.value.length !== 2) {
    ElMessage.warning('请选择计划时段')
    return
  }
  try {
    await api.createOrder({
      ...form.value,
      mixerId: cur.value.mixerId,
      plannedStart: fmtDateTime(range.value[0]),
      plannedEnd: fmtDateTime(range.value[1])
    })
    ElMessage.success('任务单已排产')
    drawer.value = false
    form.value = {
      projectName: '',
      contractor: '',
      pourPart: '',
      strengthGrade: 'C30',
      plannedVolume: 300,
      slump: '180±30mm',
      remark: ''
    }
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function statusClass(status) {
  return { RUNNING: 'run', IDLE: '', MAINTENANCE: 'warn', FAULT: 'danger' }[status] || ''
}

function orderClass(status) {
  return { PENDING: 'warn', PRODUCING: 'run', DONE: 'ok' }[status] || ''
}

function gradeColor(grade) {
  return { C25: '#5b8fa8', C30: '#2f4f4f', C35: '#8a6f4f', C40: '#8c4a3a' }[grade] || '#2f4f4f'
}

onMounted(load)
</script>

<style scoped>
.ml-board {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  overflow-x: auto;
  padding: 6px 2px 2px;
}

.ml-bar {
  width: 104px;
  flex: 0 0 104px;
  cursor: pointer;
  text-align: center;
  padding: 8px 4px 6px;
  border-radius: 8px;
  border: 1px solid transparent;
  transition: background 0.18s, border-color 0.18s, transform 0.18s;
}

.ml-bar:hover {
  background: #f4f8f7;
  border-color: var(--cp-border);
  transform: translateY(-2px);
}

.ml-bar.is-down {
  opacity: 0.72;
}

.ml-bar-top {
  height: 22px;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 3px;
}

.ml-num {
  font-size: 17px;
  font-weight: 700;
  color: var(--cp-primary);
  font-variant-numeric: tabular-nums;
}

.ml-bar.is-over .ml-num {
  color: var(--cp-danger);
}

.ml-unit {
  font-size: 11px;
  color: var(--cp-muted);
}

.ml-track {
  position: relative;
  height: 268px;
  margin: 6px auto 8px;
  width: 54px;
  background: linear-gradient(180deg, #f3f6f5, #e8eeee);
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  overflow: visible;
}

.ml-fill {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, #4d7a72, #2f4f4f);
  border-radius: 0 0 5px 5px;
  transition: height 0.35s ease;
}

.ml-over {
  position: absolute;
  left: -4px;
  right: -4px;
  background: repeating-linear-gradient(45deg, #b23a2f, #b23a2f 5px, #cf5a4e 5px, #cf5a4e 10px);
  border-radius: 3px;
  box-shadow: 0 0 0 1px #8f2b22;
}

.ml-planline {
  position: absolute;
  left: -8px;
  right: -8px;
  height: 0;
  border-top: 2px dashed #c26a1b;
}

.ml-planlabel {
  position: absolute;
  left: 58px;
  transform: translateY(50%);
  white-space: nowrap;
  font-size: 10px;
  color: var(--cp-warn);
  background: #fff8ef;
  border: 1px solid #ecd7ba;
  border-radius: 3px;
  padding: 0 4px;
}

.ml-loadtag {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: -9px;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--cp-primary);
  color: #fff;
  line-height: 16px;
}

.ml-loadtag.over {
  background: var(--cp-danger);
}

.ml-name {
  font-size: 13px;
  font-weight: 600;
}

.ml-meta {
  font-size: 11px;
  color: var(--cp-muted);
  line-height: 1.5;
}

.ml-foot {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: center;
}

.cp-legend {
  font-size: 11px;
  color: var(--cp-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.sw {
  width: 12px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

.sw-done {
  background: linear-gradient(180deg, #4d7a72, #2f4f4f);
}

.sw-over {
  background: repeating-linear-gradient(45deg, #b23a2f, #b23a2f 3px, #cf5a4e 3px, #cf5a4e 6px);
}

.sw-plan {
  border-top: 2px dashed #c26a1b;
  height: 0;
  border-radius: 0;
}

.ml-drawer-info {
  background: #f4f8f7;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.9;
}

.ml-planedit {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 4px;
}

.ml-sub {
  margin: 16px 0 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--cp-primary);
  border-left: 3px solid var(--cp-primary);
  padding-left: 8px;
}

.ml-order {
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.ml-order-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}
</style>
