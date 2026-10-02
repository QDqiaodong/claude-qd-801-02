<template>
  <el-dialog v-model="visible" title="登记试块组（留样）" width="640px" @closed="onClosed">
    <el-form :model="form" label-width="110px" size="small">
      <el-form-item label="生产任务单" required>
        <el-select
          v-model="form.orderId"
          filterable
          placeholder="选择已生产（有已装车发车单）的任务单"
          style="width: 100%"
          @change="onOrderChange"
        >
          <el-option
            v-for="o in orders"
            :key="o.id"
            :label="`${o.orderNo} · ${o.projectName}（${o.strengthGrade}）`"
            :value="o.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="工程 / 等级">
        <span class="cp-muted">
          {{ currentOrder?.projectName || '—' }} ·
          <b>{{ currentOrder?.strengthGrade || '—' }}</b>
          （登记时快照，之后任务单修改不影响本组）
        </span>
      </el-form-item>

      <el-form-item label="试块编号" required>
        <el-input v-model="form.cubeNo" maxlength="32" show-word-limit placeholder="如 SK-2609，全系统唯一" />
      </el-form-item>

      <el-form-item label="取样时间" required>
        <el-date-picker
          v-model="form.sampledAt"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="取样时间"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="养护方式" required>
        <el-radio-group v-model="form.curingMethod">
          <el-radio value="STANDARD">标准养护</el-radio>
          <el-radio value="ONSITE">同条件养护</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="关联发车单" required>
        <div class="qr-tickets">
          <div v-if="!eligibleTickets.length" class="cp-muted">
            该任务单暂无已装车发车单，不能留样登记
          </div>
          <el-checkbox-group v-model="form.ticketIds">
            <el-checkbox
              v-for="t in eligibleTickets"
              :key="t.id"
              :value="t.id"
              class="qr-ticket"
            >
              {{ t.ticketNo }} · {{ t.plateNo }} · 装车 {{ fmtDateTime(t.loadTime) }} · {{ t.loadVolume }} m³ ·
              {{ t.statusName }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="cp-muted qr-hint">至少选择一张同一任务单下已装车（含出厂/到工地/签收）的发车单</div>
        </div>
      </el-form-item>

      <el-form-item label="登记质检员" required>
        <el-input v-model="form.inspector" maxlength="32" placeholder="质检员姓名" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="submit">保存并生成 7d/28d 检测项</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, fmtDateTime } from '../../api'

const emit = defineEmits(['saved'])

const visible = ref(false)
const saving = ref(false)
const orders = ref([])
const tickets = ref([])

const form = reactive({
  cubeNo: '',
  orderId: null,
  sampledAt: '',
  curingMethod: 'STANDARD',
  ticketIds: [],
  inspector: '',
  remark: ''
})

const currentOrder = computed(() => orders.value.find((o) => o.id === form.orderId))
const eligibleTickets = computed(() =>
  (tickets.value || []).filter((t) => t.orderId === form.orderId)
)

async function open() {
  visible.value = true
  form.cubeNo = ''
  form.orderId = null
  form.sampledAt = ''
  form.curingMethod = 'STANDARD'
  form.ticketIds = []
  form.inspector = ''
  form.remark = ''
  try {
    const [od, tk] = await Promise.all([api.orders(), api.tickets()])
    orders.value = (od.data || []).filter((o) => o.status !== 'PENDING')
    tickets.value = tk.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function onOrderChange() {
  form.ticketIds = []
}

async function submit() {
  if (!form.orderId) return ElMessage.warning('请选择生产任务单')
  if (!form.cubeNo.trim()) return ElMessage.warning('请填写试块编号')
  if (!form.sampledAt) return ElMessage.warning('请选择取样时间')
  if (!form.ticketIds.length) return ElMessage.warning('至少关联一张已装车发车单')
  if (!form.inspector.trim()) return ElMessage.warning('请填写登记质检员')
  saving.value = true
  try {
    const res = await api.registerCube({
      cubeNo: form.cubeNo.trim(),
      orderId: form.orderId,
      sampledAt: form.sampledAt,
      curingMethod: form.curingMethod,
      ticketIds: form.ticketIds,
      inspector: form.inspector.trim(),
      remark: form.remark
    })
    ElMessage.success(res.message || '试块组已登记')
    visible.value = false
    emit('saved', res.data)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function onClosed() {
  // 关闭即重置由 open() 负责
}

defineExpose({ open })
</script>

<style scoped>
.qr-tickets {
  width: 100%;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 8px 12px;
  max-height: 180px;
  overflow-y: auto;
}

.qr-ticket {
  display: flex;
  width: 100%;
  margin-right: 0;
  height: auto;
  white-space: normal;
  line-height: 1.6;
}

.qr-hint {
  margin-top: 4px;
  font-size: 11px;
}
</style>
