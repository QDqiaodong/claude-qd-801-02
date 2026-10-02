<template>
  <el-dialog
    :model-value="modelValue"
    :title="`临时故障停机 · ${mixer?.mixerName || ''}`"
    width="980px"
    :close-on-click-modal="false"
    @close="close"
  >
    <div v-if="!result" class="fault-dialog">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="预演不会改变任何数据；确认时将在同一次业务操作中置故障、保存处置记录并整批改派。"
        class="fault-alert"
      />

      <el-form label-width="96px" size="small" class="fault-form">
        <el-form-item label="故障主机" required>
          <el-tag>{{ mixer?.mixerCode }} / {{ mixer?.mixerName }} / {{ mixer?.statusName }}</el-tag>
        </el-form-item>
        <el-form-item label="故障原因" required>
          <el-input v-model="reason" maxlength="255" show-word-limit placeholder="如：主轴减速机异响，需停机检查" />
        </el-form-item>
        <el-form-item label="发生时间" required>
          <el-date-picker
            v-model="occurredAt"
            type="datetime"
            placeholder="选择故障发生时间"
            format="YYYY-MM-DD HH:mm"
            value-format="x"
            :disabled-date="disabledFutureDate"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain :loading="loading" @click="loadPreview">生成受影响任务与改派预演</el-button>
          <span class="cp-muted">确认前可反复调整接替主机并重算冲突</span>
        </el-form-item>
      </el-form>

      <template v-if="preview">
        <el-alert
          v-if="preview.blockingMessages.length"
          type="error"
          :closable="false"
          show-icon
          class="fault-alert"
          title="当前不能整批确认，请先处理以下问题"
        >
          <div v-for="(msg, i) in preview.blockingMessages" :key="i" class="fault-line">• {{ msg }}</div>
        </el-alert>
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          title="预演通过：每张可改派任务均已选择非故障、非检修主机，且原时段无排期冲突。"
          class="fault-alert"
        />

        <div class="fault-section">
          <div class="fault-section-title">可改派任务（{{ preview.reassignableTasks.length }}）</div>
          <el-table :data="preview.reassignableTasks" size="small" border empty-text="暂无尚未开工且无发车记录的任务">
            <el-table-column prop="orderNo" label="任务单" width="104" />
            <el-table-column label="工程 / 部位" min-width="210">
              <template #default="{ row }">
                <div>{{ row.projectName }}</div>
                <div class="cp-muted">{{ row.pourPart || '未指定部位' }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="plannedVolume" label="方量" width="78" />
            <el-table-column label="原计划时段（必须沿用）" width="190">
              <template #default="{ row }">{{ range(row) }}</template>
            </el-table-column>
            <el-table-column label="接替主机" width="190">
              <template #default="{ row }">
                <el-select
                  v-model="assignments[row.orderId]"
                  placeholder="选择主机"
                  size="small"
                  style="width: 100%"
                  @change="loadPreview"
                >
                  <el-option
                    v-for="m in preview.availableMixers"
                    :key="m.mixerId"
                    :label="`${m.mixerName}（${m.statusName}）`"
                    :value="m.mixerId"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="冲突检查" min-width="220">
              <template #default="{ row }">
                <el-tag v-if="!assignments[row.orderId]" type="info" size="small">待选择</el-tag>
                <el-tag v-else-if="row.conflicts.length" type="danger" size="small">
                  {{ row.conflicts.length }} 个冲突，停止确认
                </el-tag>
                <el-tag v-else type="success" size="small">原时段可排入</el-tag>
                <div v-for="c in row.conflicts" :key="c.orderId" class="fault-conflict">
                  冲突：{{ c.orderNo }} {{ range(c) }}
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="fault-section">
          <div class="fault-section-title">不可改派任务（{{ preview.blockedTasks.length }}）</div>
          <el-table :data="preview.blockedTasks" size="small" border>
            <el-table-column prop="orderNo" label="任务单" width="104" />
            <el-table-column label="工程 / 部位" min-width="210">
              <template #default="{ row }">
                <div>{{ row.projectName }}</div>
                <div class="cp-muted">{{ row.pourPart || '未指定部位' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="阶段 / 发车" width="126">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'PRODUCING' ? 'warning' : 'info'">{{ row.statusName }}</el-tag>
                <div class="cp-muted">发车 {{ row.ticketCount }} 条</div>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="不可改派说明" min-width="230" />
          </el-table>
        </div>
      </template>
    </div>

    <div v-else class="fault-result">
      <el-result icon="success" title="故障停机已确认" :sub-title="`处置单 ${result.recordNo}`" />
      <el-descriptions :column="2" border size="small" class="fault-desc">
        <el-descriptions-item label="故障主机">{{ result.faultMixerName }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ fmt(result.occurredAt) }}</el-descriptions-item>
        <el-descriptions-item label="故障原因" :span="2">{{ result.reason }}</el-descriptions-item>
      </el-descriptions>

      <div class="fault-section-title">逐项变化核对</div>
      <el-table :data="result.changes" size="small" border>
        <el-table-column prop="label" label="对象" min-width="220" />
        <el-table-column prop="before" label="确认前" min-width="190" />
        <el-table-column prop="after" label="确认后" min-width="230" />
      </el-table>

      <div class="fault-section-title">改派后任务归属</div>
      <el-table :data="result.reassignments" size="small" border>
        <el-table-column prop="orderNo" label="任务单" width="104" />
        <el-table-column prop="projectName" label="工程" min-width="190" />
        <el-table-column prop="sourceMixerName" label="原主机" width="110" />
        <el-table-column label="新主机" width="110">
          <template #default="{ row }">
            <el-tag type="success" size="small">{{ row.selectedMixerName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计划时段" width="180">
          <template #default="{ row }">{{ range(row) }}</template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="result.blockedTasks.length"
        type="info"
        :closable="false"
        show-icon
        :title="`${result.blockedTasks.length} 张生产中、已完成或有发车记录的任务保留原主机，未随批迁走。`"
        class="fault-alert"
      />
    </div>

    <template #footer>
      <el-button @click="close">{{ result ? '关闭并刷新看板' : '取消' }}</el-button>
      <el-button
        v-if="!result"
        type="danger"
        :disabled="!preview?.readyToConfirm"
        :loading="confirming"
        @click="confirm"
      >
        确认故障停机并整批改派
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, fmtDateTime } from '../api'

const props = defineProps({
  modelValue: Boolean,
  mixer: Object
})
const emit = defineEmits(['update:modelValue', 'confirmed'])

const reason = ref('')
const occurredAt = ref(Date.now())
const assignments = ref({})
const preview = ref(null)
const result = ref(null)
const loading = ref(false)
const confirming = ref(false)

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      reason.value = ''
      occurredAt.value = Date.now()
      assignments.value = {}
      preview.value = null
      result.value = null
    }
  }
)

function disabledFutureDate(date) {
  return date.getTime() > Date.now() + 86400000
}

function payload() {
  const data = {
    mixerId: props.mixer?.mixerId,
    reason: reason.value.trim(),
    occurredAt: fmtDateTime(new Date(Number(occurredAt.value))),
    operatorName: '调度员'
  }
  if (preview.value) {
    data.assignments = preview.value.reassignableTasks
      .filter((task) => assignments.value[task.orderId])
      .map((task) => ({
        orderId: task.orderId,
        targetMixerId: assignments.value[task.orderId],
        plannedStart: task.plannedStart,
        plannedEnd: task.plannedEnd
      }))
  }
  return data
}

async function loadPreview() {
  if (!reason.value.trim()) {
    ElMessage.warning('请先填写故障原因')
    return
  }
  if (!occurredAt.value) {
    ElMessage.warning('请选择故障发生时间')
    return
  }
  loading.value = true
  try {
    const res = await api.faultPreview(payload())
    preview.value = res.data
  } catch (e) {
    preview.value = null
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function confirm() {
  await ElMessageBox.confirm(
    '确认前系统会重新核对任务阶段、发车记录和接替主机排期；任一条件变化将整批回滚。是否确认？',
    '整批故障改派确认',
    { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '再检查一下' }
  )
  confirming.value = true
  try {
    const res = await api.faultConfirm(payload())
    result.value = res.data
    ElMessage.success('故障处置与整批改派已完成')
    emit('confirmed', res.data)
  } catch (e) {
    ElMessage.error(e.message)
    await loadPreview()
  } finally {
    confirming.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function range(row) {
  return `${fmt(row.plannedStart)} ~ ${fmt(row.plannedEnd)}`
}

function fmt(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(5, 16)
}
</script>

<style scoped>
.fault-alert {
  margin-bottom: 12px;
}

.fault-form {
  margin-top: 12px;
}

.fault-section {
  margin-top: 16px;
}

.fault-section-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--cp-primary);
  margin: 14px 0 8px;
  border-left: 3px solid var(--cp-primary);
  padding-left: 8px;
}

.fault-line {
  line-height: 1.7;
}

.fault-conflict {
  color: var(--cp-danger);
  font-size: 11px;
  line-height: 1.5;
}

.fault-desc {
  margin-bottom: 14px;
}
</style>
