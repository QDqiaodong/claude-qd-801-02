<template>
  <el-dialog
    v-model="visible"
    :title="`录入 ${form.ageDays}d 抗压强度 · ${form.cubeNo || ''}`"
    width="520px"
  >
    <div class="ir-meta">
      <div><span class="cp-muted">任务单</span> {{ form.orderNo }} · {{ form.projectName }}</div>
      <div>
        <span class="cp-muted">强度等级</span>
        <span class="cp-badge" :style="{ background: gradeColor(form.grade) }">{{ form.grade }}</span>
        <span class="cp-muted" style="margin-left: 8px">到期 {{ fmtDateTime(form.dueAt) }}</span>
      </div>
    </div>

    <!-- 阈值提示：全部取自服务端 /api/quality/criteria，页面禁止自行计算或修改 -->
    <div class="ir-rule">
      <template v-if="form.ageDays === 28">
        28d 正式判定（服务端计算）：三块平均 ≥ <b>{{ criterion?.avgThreshold28 ?? '—' }}</b> MPa 且
        最小 ≥ <b>{{ criterion?.minThreshold28 ?? '—' }}</b> MPa，未达门槛将自动生成唯一复检单。
      </template>
      <template v-else>
        7d 仅作早龄期趋势参考：平均达到 <b>{{ criterion?.trendLine7 ?? '—' }}</b> MPa 为趋势正常，
        <b>不改变最终质量状态</b>。
      </template>
    </div>

    <el-form label-width="120px" size="small" style="margin-top: 8px">
      <el-form-item label="试块 1 抗压 MPa">
        <el-input-number v-model="form.s1" :min="0.1" :max="200" :step="0.1" :precision="2" style="width: 180px" />
      </el-form-item>
      <el-form-item label="试块 2 抗压 MPa">
        <el-input-number v-model="form.s2" :min="0.1" :max="200" :step="0.1" :precision="2" style="width: 180px" />
      </el-form-item>
      <el-form-item label="试块 3 抗压 MPa">
        <el-input-number v-model="form.s3" :min="0.1" :max="200" :step="0.1" :precision="2" style="width: 180px" />
      </el-form-item>
      <el-form-item label="检测操作者" required>
        <el-input v-model="form.operator" maxlength="32" placeholder="质检员姓名" style="width: 180px" />
      </el-form-item>
    </el-form>

    <div class="cp-muted ir-note">
      三块读数与操作者、检测时间提交后即固化为正式结果，原始读数不可覆盖；平均值、最小值与合格判定由服务端统一生成。
    </div>

    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="submit">提交正式结果</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, fmtDateTime } from '../../api'

const emit = defineEmits(['submitted'])

const visible = ref(false)
const saving = ref(false)
const criteria = ref([])

const form = reactive({
  itemId: null,
  cubeNo: '',
  orderNo: '',
  projectName: '',
  grade: '',
  ageDays: 28,
  dueAt: null,
  s1: 30,
  s2: 30,
  s3: 30,
  operator: ''
})

const criterion = ref(null)

function gradeColor(grade) {
  return { C25: '#5b8fa8', C30: '#2f4f4f', C35: '#8a6f4f', C40: '#8c4a3a' }[grade] || '#2f4f4f'
}

async function open(item) {
  form.itemId = item.id
  form.cubeNo = item.cubeNo
  form.orderNo = item.orderNo
  form.projectName = item.projectName
  form.grade = item.strengthGrade
  form.ageDays = item.ageDays
  form.dueAt = item.dueAt
  form.s1 = 30
  form.s2 = 30
  form.s3 = 30
  form.operator = ''
  visible.value = true
  try {
    const res = await api.qualityCriteria()
    criteria.value = res.data?.grades || []
    criterion.value = criteria.value.find((g) => g.grade === item.strengthGrade)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function submit() {
  if (!form.operator.trim()) return ElMessage.warning('请填写检测操作者')
  saving.value = true
  try {
    const res = await api.submitResult(form.itemId, {
      strength1: form.s1,
      strength2: form.s2,
      strength3: form.s3,
      operatorName: form.operator.trim()
    })
    ElMessage.success(res.message || '结果已提交')
    visible.value = false
    emit('submitted', res.data)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.ir-meta {
  background: #f4f8f7;
  border: 1px solid var(--cp-border);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.9;
}

.ir-rule {
  margin-top: 10px;
  padding: 8px 12px;
  border-left: 3px solid var(--cp-primary);
  background: #eef3f2;
  font-size: 12px;
  line-height: 1.7;
}

.ir-note {
  margin-top: 4px;
  font-size: 11px;
}
</style>
