<template>
  <div v-if="group">
    <div class="cp-panel">
      <div class="cp-panel-head">
        <el-button size="small" @click="$emit('back')">← 返回待检清单</el-button>
        <div class="cp-panel-title" style="margin-left: 6px">{{ group.cubeNo }}</div>
        <span class="cp-tag" :class="groupTagClass(group.status)">{{ group.statusName }}</span>
        <span class="cp-badge" :style="{ background: gradeColor(group.strengthGrade) }">{{ group.strengthGrade }}</span>
        <div style="margin-left: auto">
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>
      <div class="cp-panel-body">
        <div class="gd-info">
          <div><span class="cp-muted">任务单</span> <el-link type="primary" @click="$emit('view-order', group.orderId)">{{ group.orderNo }}</el-link></div>
          <div><span class="cp-muted">工程</span> {{ group.projectName }}</div>
          <div><span class="cp-muted">施工单位</span> {{ group.contractor || '—' }}</div>
          <div><span class="cp-muted">浇筑部位</span> {{ group.pourPart || '—' }}</div>
          <div><span class="cp-muted">取样时间</span> {{ fmtDateTime(group.sampledAt) }}</div>
          <div><span class="cp-muted">养护方式</span> {{ group.curingMethodName }}</div>
          <div><span class="cp-muted">登记质检员</span> {{ group.inspector }}</div>
          <div><span class="cp-muted">登记时间</span> {{ fmtDateTime(group.createdAt) }}</div>
        </div>
        <div v-if="group.remark" class="cp-muted gd-remark">备注：{{ group.remark }}</div>
      </div>
    </div>

    <!-- 快照配合比 -->
    <div class="cp-panel" style="margin-top: 14px">
      <div class="cp-panel-head">
        <div class="cp-panel-title">配合比快照</div>
        <div class="cp-panel-sub">登记留样时固化，任务单后续调整不影响本组判定来源</div>
      </div>
      <div class="cp-panel-body">
        <div class="gd-mix">
          <div v-for="m in group.mixItems" :key="m.materialCode" class="gd-mix-item">
            <i :style="{ background: color(m.materialCode) }"></i>
            <span>{{ m.materialName }}</span>
            <b>{{ m.dosage }} kg/m³</b>
            <span class="cp-muted">{{ m.ratioPercent }}%</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 关联发车单 -->
    <div class="cp-panel" style="margin-top: 14px">
      <div class="cp-panel-head">
        <div class="cp-panel-title">关联已装车发车单（{{ group.tickets.length }}）</div>
      </div>
      <div class="cp-panel-body">
        <div class="gd-tickets">
          <div v-for="t in group.tickets" :key="t.id" class="gd-ticket">
            <b>{{ t.ticketNo }}</b>
            <span>{{ t.plateNo }}</span>
            <span>装车 {{ fmtDateTime(t.loadTime) }}</span>
            <span>{{ t.loadVolume }} m³</span>
            <span class="cp-tag" :class="ticketClass(t.status)">{{ t.statusName }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 检测链：7d / 28d / 复检 -->
    <div class="cp-panel" style="margin-top: 14px">
      <div class="cp-panel-head">
        <div class="cp-panel-title">完整检测链</div>
        <div class="cp-panel-sub">7d 趋势 → 28d 判定 → 复检（原检测只读引用）</div>
      </div>
      <div class="cp-panel-body">
        <div v-for="item in group.items" :key="item.id" class="gd-chain">
          <div class="gd-chain-head">
            <span class="gd-age">{{ item.ageDays }}d 检测项</span>
            <span class="cp-muted">到期 {{ fmtDateTime(item.dueAt) }}</span>
            <span class="cp-tag" :class="flowTagClass(item.flowStatus)">{{ item.flowStatusName }}</span>
            <span style="margin-left: auto">
              <el-button
                v-if="canInput(item)"
                size="small"
                type="primary"
                @click="$emit('input-result', item)"
              >
                录入结果
              </el-button>
            </span>
          </div>

          <div v-if="item.result" class="gd-result">
            <div class="gd-readings">
              <span>① {{ item.result.strength1 }}</span>
              <span>② {{ item.result.strength2 }}</span>
              <span>③ {{ item.result.strength3 }}</span>
              <span>平均 <b>{{ item.result.avgValue }}</b></span>
              <span>最小 <b>{{ item.result.minValue }}</b></span>
              <span class="cp-tag" :class="verdictClass(item.result.verdict)">{{ item.result.verdictName }}</span>
            </div>
            <div class="cp-muted gd-meta">
              设计 fcu,k {{ item.result.designValue }} MPa；
              <template v-if="item.ageDays === 28">
                门槛 平均 ≥ {{ item.result.avgThreshold }}、最小 ≥ {{ item.result.minThreshold }} MPa；
              </template>
              <template v-else>
                7d 趋势线 ≥ {{ item.result.avgThreshold }} MPa（仅趋势，不改变终态）；
              </template>
              检测 {{ item.result.operatorName }} · {{ fmtDateTime(item.result.testedAt) }}
            </div>
          </div>
          <div v-else class="cp-muted gd-empty">
            {{ item.flowStatus === 'NOT_DUE' ? '未到目标龄期，不能录入正式结果' : '尚未录入结果' }}
          </div>

          <!-- 复检 -->
          <div v-if="item.recheck" class="gd-recheck">
            <div class="gd-chain-head">
              <span class="gd-age gd-age-rc">复检 {{ item.recheck.recheckNo }}</span>
              <span class="cp-tag" :class="item.recheck.status === 'DONE' ? (item.recheck.verdict === 'QUALIFIED' ? 'ok' : 'danger') : 'warn'">
                {{ item.recheck.statusName }}
              </span>
              <span v-if="item.recheck.status === 'DONE'" class="cp-muted">
                平均 {{ item.recheck.avgValue }} / 最小 {{ item.recheck.minValue }} MPa ·
                {{ item.recheck.operatorName }} · {{ fmtDateTime(item.recheck.testedAt) }}
              </span>
              <span style="margin-left: auto">
                <el-button
                  v-if="item.recheck.status === 'PENDING'"
                  size="small"
                  type="warning"
                  @click="$emit('recheck', item.recheck)"
                >
                  录入复检结果
                </el-button>
              </span>
            </div>
            <div v-if="item.recheck.status === 'PENDING'" class="cp-muted gd-meta">
              {{ item.recheck.remark }}；生成人 {{ item.recheck.createdBy }} · {{ fmtDateTime(item.recheck.createdAt) }}
            </div>
            <div v-else class="cp-muted gd-meta">{{ item.recheck.remark }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { api, fmtDateTime } from '../../api'

const props = defineProps({ groupId: { type: Number, default: null } })
defineEmits(['back', 'input-result', 'recheck', 'view-order'])

const group = ref(null)

const COLORS = {
  CEMENT: '#2f4f4f',
  FLYASH: '#8a6f4f',
  SLAG: '#4f7d8a',
  SAND: '#c9a227',
  STONE: '#7d8a8a',
  WATER: '#5b8fa8',
  ADMIXTURE: '#a0522d'
}

async function load() {
  if (!props.groupId) return
  const res = await api.cubeGroup(props.groupId)
  group.value = res.data
}

function color(code) {
  return COLORS[code] || '#98a5a4'
}

function gradeColor(grade) {
  return { C25: '#5b8fa8', C30: '#2f4f4f', C35: '#8a6f4f', C40: '#8c4a3a' }[grade] || '#2f4f4f'
}

function flowTagClass(flow) {
  if (flow === 'QUALIFIED' || flow === 'TREND_OK') return 'ok'
  if (flow === 'RECHECK' || flow === 'TREND_LOW') return 'warn'
  if (flow === 'UNQUALIFIED' || flow === 'OVERDUE') return 'danger'
  if (flow === 'DUE') return 'run'
  return ''
}

function verdictClass(verdict) {
  if (verdict === 'QUALIFIED' || verdict === 'TREND_OK') return 'ok'
  if (verdict === 'UNQUALIFIED' || verdict === 'TREND_LOW') return 'danger'
  return ''
}

function groupTagClass(status) {
  if (status === 'QUALIFIED') return 'ok'
  if (status === 'RECHECK') return 'warn'
  if (status === 'UNQUALIFIED') return 'danger'
  return 'run'
}

function ticketClass(status) {
  return { LOADED: 'warn', DEPARTED: 'run', ARRIVED: 'run', SIGNED: 'ok' }[status] || ''
}

function canInput(item) {
  return item.flowStatus === 'DUE' || item.flowStatus === 'OVERDUE'
}

load()
defineExpose({ reload: load })
</script>

<style scoped>
.gd-info {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px 16px;
  font-size: 12px;
  line-height: 1.8;
}

.gd-remark {
  margin-top: 8px;
  font-size: 11px;
}

.gd-mix {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 8px;
}

.gd-mix-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 6px 10px;
}

.gd-mix-item i {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

.gd-mix-item b {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.gd-tickets {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 8px;
}

.gd-ticket {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 6px 10px;
}

.gd-chain {
  border: 1px solid var(--cp-border);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.gd-chain-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.gd-age {
  font-weight: 700;
  color: var(--cp-primary);
}

.gd-age-rc {
  color: var(--cp-warn);
}

.gd-result {
  margin-top: 8px;
  background: #f4f8f7;
  border-radius: 6px;
  padding: 8px 10px;
}

.gd-readings {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.gd-meta {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.7;
}

.gd-empty {
  margin-top: 8px;
  font-size: 12px;
}

.gd-recheck {
  margin-top: 8px;
  border-top: 1px dashed var(--cp-border);
  padding-top: 8px;
}
</style>
