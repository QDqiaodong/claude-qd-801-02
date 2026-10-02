<template>
  <div>
    <!-- 待检清单 -->
    <div class="cp-panel">
      <div class="cp-panel-head">
        <div class="cp-panel-title">抗压检测待检清单</div>
        <div class="cp-panel-sub">未到期 / 待检 / 逾期 / 已合格 / 待复检 / 不合格 / 7d 趋势，状态与判定均由服务端实时给出</div>
        <div style="margin-left: auto; display: flex; gap: 8px">
          <el-select v-model="gradeFilter" size="small" clearable placeholder="强度等级" style="width: 110px" @change="loadItems">
            <el-option v-for="g in grades" :key="g" :label="g" :value="g" />
          </el-select>
          <el-button size="small" @click="loadItems">刷新</el-button>
        </div>
      </div>
      <div class="cp-panel-body">
        <div class="qv-tabs">
          <span
            v-for="t in flowTabs"
            :key="t.value"
            class="qv-tab"
            :class="{ active: flowFilter === t.value }"
            @click="changeFlow(t.value)"
          >
            {{ t.label }}
          </span>
        </div>

        <el-table :data="items" size="small" stripe style="margin-top: 10px" empty-text="暂无检测项">
          <el-table-column label="试块编号" width="120">
            <template #default="{ row }">
              <el-link type="primary" @click="$emit('open-group', row.groupId)">{{ row.cubeNo }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="orderNo" label="任务单" width="100" />
          <el-table-column prop="projectName" label="工程" min-width="170" show-overflow-tooltip />
          <el-table-column label="等级" width="70">
            <template #default="{ row }">
              <span class="cp-badge" :style="{ background: gradeColor(row.strengthGrade) }">{{ row.strengthGrade }}</span>
            </template>
          </el-table-column>
          <el-table-column label="龄期" width="70">
            <template #default="{ row }">{{ row.ageDays }}d</template>
          </el-table-column>
          <el-table-column label="到期时间" width="160">
            <template #default="{ row }">{{ fmtDateTime(row.dueAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <span class="cp-tag" :class="flowTagClass(row.flowStatus)">{{ row.flowStatusName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="平均/最小 MPa" width="150">
            <template #default="{ row }">
              <span v-if="row.result" class="qv-nums">
                {{ row.result.avgValue }} / {{ row.result.minValue }}
              </span>
              <span v-else class="cp-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="canInput(row)"
                size="small"
                type="primary"
                @click="$emit('input-result', row)"
              >
                录入结果
              </el-button>
              <el-button
                v-else-if="row.flowStatus === 'RECHECK'"
                size="small"
                type="warning"
                @click="$emit('open-group', row.groupId)"
              >
                去复检
              </el-button>
              <span v-else class="cp-muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="cp-muted qv-tip">
          7d 结果只反映早龄期趋势，不改变最终质量状态；28d 未达门槛自动生成唯一复检单，原始读数不可覆盖。
        </div>
      </div>
    </div>

    <!-- 试块组 -->
    <div class="cp-panel" style="margin-top: 14px">
      <div class="cp-panel-head">
        <div class="cp-panel-title">试块组留样台账</div>
        <div class="cp-panel-sub">登记时关联同任务单已装车发车单，并快照工程、强度等级与配合比</div>
        <div style="margin-left: auto; display: flex; gap: 8px">
          <el-select v-model="groupStatus" size="small" clearable placeholder="组状态" style="width: 120px" @change="loadGroups">
            <el-option v-for="g in groupStatusOptions" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
          <el-select v-model="groupGrade" size="small" clearable placeholder="强度等级" style="width: 110px" @change="loadGroups">
            <el-option v-for="g in grades" :key="g" :label="g" :value="g" />
          </el-select>
          <el-button size="small" type="primary" @click="$emit('register')">登记试块组</el-button>
          <el-button size="small" @click="loadGroups">刷新</el-button>
        </div>
      </div>
      <div class="cp-panel-body">
        <el-table :data="groups" size="small" stripe empty-text="暂无试块组">
          <el-table-column label="试块编号" width="120">
            <template #default="{ row }">
              <el-link type="primary" @click="$emit('open-group', row.id)">{{ row.cubeNo }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="orderNo" label="任务单" width="100" />
          <el-table-column prop="projectName" label="工程" min-width="170" show-overflow-tooltip />
          <el-table-column label="等级" width="70">
            <template #default="{ row }">
              <span class="cp-badge" :style="{ background: gradeColor(row.strengthGrade) }">{{ row.strengthGrade }}</span>
            </template>
          </el-table-column>
          <el-table-column label="养护" width="100">
            <template #default="{ row }">{{ row.curingMethodName }}</template>
          </el-table-column>
          <el-table-column prop="inspector" label="质检员" width="90" />
          <el-table-column label="取样时间" width="160">
            <template #default="{ row }">{{ fmtDateTime(row.sampledAt) }}</template>
          </el-table-column>
          <el-table-column label="关联发车单" width="90">
            <template #default="{ row }">{{ row.ticketCount }} 张</template>
          </el-table-column>
          <el-table-column label="组状态" width="100">
            <template #default="{ row }">
              <span class="cp-tag" :class="groupTagClass(row.status)">{{ row.statusName }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, fmtDateTime } from '../../api'

const props = defineProps({ orderId: { type: Number, default: null } })
const emit = defineEmits(['open-group', 'input-result', 'register'])

const grades = ['C25', 'C30', 'C35', 'C40']
const flowFilter = ref('')
const gradeFilter = ref('')
const groupStatus = ref('')
const groupGrade = ref('')
const items = ref([])
const groups = ref([])

const flowTabs = [
  { value: '', label: '全部' },
  { value: 'NOT_DUE', label: '未到期' },
  { value: 'DUE', label: '待检' },
  { value: 'OVERDUE', label: '逾期' },
  { value: 'QUALIFIED', label: '已合格' },
  { value: 'RECHECK', label: '待复检' },
  { value: 'UNQUALIFIED', label: '不合格' },
  { value: 'TREND_OK', label: '7d 趋势正常' },
  { value: 'TREND_LOW', label: '7d 趋势偏低' }
]

const groupStatusOptions = [
  { value: 'TESTING', label: '检测中' },
  { value: 'QUALIFIED', label: '已合格' },
  { value: 'RECHECK', label: '待复检' },
  { value: 'UNQUALIFIED', label: '不合格' }
]

async function loadItems() {
  try {
    const params = {}
    if (flowFilter.value) params.flowStatus = flowFilter.value
    if (gradeFilter.value) params.grade = gradeFilter.value
    const res = await api.qualityItems(params)
    items.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadGroups(extra) {
  try {
    const params = { ...extra }
    if (groupStatus.value) params.status = groupStatus.value
    if (groupGrade.value) params.grade = groupGrade.value
    const res = await api.cubeGroups(params)
    groups.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 从任务单进入：只查该任务单的试块组，返回最新一组（含检测链） */
async function loadForOrder(orderId) {
  await loadGroups({ orderId })
  return groups.value
}

watch(
  () => props.orderId,
  (orderId) => {
    if (orderId != null) loadForOrder(orderId)
  }
)

function changeFlow(value) {
  flowFilter.value = value
  loadItems()
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

function groupTagClass(status) {
  if (status === 'QUALIFIED') return 'ok'
  if (status === 'RECHECK') return 'warn'
  if (status === 'UNQUALIFIED') return 'danger'
  return 'run'
}

/** 未到期禁止录入正式结果：只有待检/逾期可录；7d 趋势/合格/待复检/不合格由详情页处理 */
function canInput(row) {
  return row.flowStatus === 'DUE' || row.flowStatus === 'OVERDUE'
}

onMounted(() => {
  loadItems()
  loadGroups()
})

defineExpose({ reload() { loadItems(); loadGroups() } })
</script>

<style scoped>
.qv-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.qv-tab {
  padding: 3px 12px;
  font-size: 12px;
  border: 1px solid var(--cp-border);
  border-radius: 14px;
  background: #f4f7f6;
  color: var(--cp-muted);
  cursor: pointer;
}

.qv-tab.active {
  background: var(--cp-primary);
  border-color: var(--cp-primary);
  color: #fff;
}

.qv-nums {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.qv-tip {
  margin-top: 8px;
  font-size: 11px;
}
</style>
