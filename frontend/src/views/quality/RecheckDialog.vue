<template>
  <el-dialog v-model="visible" :title="`复检录入 · ${recheck.recheckNo || ''}`" width="560px">
    <div class="rc-original">
      <div class="rc-sub">原 28d 检测（只读引用，原始读数不可覆盖）</div>
      <div class="rc-grid">
        <span>试块 1：{{ r.strength1 }} MPa</span>
        <span>试块 2：{{ r.strength2 }} MPa</span>
        <span>试块 3：{{ r.strength3 }} MPa</span>
        <span>平均：<b>{{ r.avgValue }}</b> MPa</span>
        <span>最小：<b>{{ r.minValue }}</b> MPa</span>
        <span>结论：<span class="cp-tag danger">{{ r.verdictName }}</span></span>
        <span>操作者：{{ r.operatorName }}</span>
        <span>检测时间：{{ fmtDateTime(r.testedAt) }}</span>
      </div>
      <div class="cp-muted rc-line">
        判定门槛：平均 ≥ {{ r.avgThreshold }} MPa，最小 ≥ {{ r.minThreshold }} MPa；
        复检单生成于 {{ fmtDateTime(recheck.createdAt) }}，生成人 {{ recheck.createdBy }}。
      </div>
    </div>

    <el-form label-width="120px" size="small" style="margin-top: 12px">
      <el-form-item label="复检试块 1 MPa">
        <el-input-number v-model="form.s1" :min="0.1" :max="200" :step="0.1" :precision="2" style="width: 180px" />
      </el-form-item>
      <el-form-item label="复检试块 2 MPa">
        <el-input-number v-model="form.s2" :min="0.1" :max="200" :step="0.1" :precision="2" style="width: 180px" />
      </el-form-item>
      <el-form-item label="复检试块 3 MPa">
        <el-input-number v-model="form.s3" :min="0.1" :max="200" :step="0.1" :precision="2" style="width: 180px" />
      </el-form-item>
      <el-form-item label="复检操作者" required>
        <el-input v-model="form.operator" maxlength="32" placeholder="复检质检员姓名" style="width: 180px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
      </el-form-item>
    </el-form>

    <div class="cp-muted rc-note">
      复检合格，试块组转为合格；复检仍失败，试块组锁定为不合格。复检只能提交一次。
    </div>

    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="warning" :loading="saving" @click="submit">提交复检结论</el-button>
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
const recheck = ref({ originalResult: {} })

const form = reactive({ id: null, s1: 30, s2: 30, s3: 30, operator: '', remark: '' })

const r = ref({})

async function open(recheckVO) {
  recheck.value = recheckVO
  r.value = recheckVO.originalResult || {}
  form.id = recheckVO.id
  form.s1 = Number(recheckVO.avgThreshold) || 30
  form.s2 = Number(recheckVO.avgThreshold) || 30
  form.s3 = Number(recheckVO.avgThreshold) || 30
  form.operator = ''
  form.remark = ''
  visible.value = true
}

async function submit() {
  if (!form.operator.trim()) return ElMessage.warning('请填写复检操作者')
  saving.value = true
  try {
    const res = await api.submitRecheck(form.id, {
      strength1: form.s1,
      strength2: form.s2,
      strength3: form.s3,
      operatorName: form.operator.trim(),
      remark: form.remark
    })
    ElMessage.success(res.message || '复检结果已提交')
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
.rc-original {
  background: #fbf6ee;
  border: 1px solid #e5cdae;
  border-radius: 6px;
  padding: 10px 12px;
}

.rc-sub {
  font-size: 12px;
  font-weight: 700;
  color: var(--cp-warn);
  margin-bottom: 6px;
}

.rc-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px 12px;
  font-size: 12px;
}

.rc-line {
  margin-top: 8px;
  font-size: 11px;
}

.rc-note {
  font-size: 11px;
}
</style>
