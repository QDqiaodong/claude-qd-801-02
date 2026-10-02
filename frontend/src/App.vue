<template>
  <div class="cp-shell">
    <!-- 左侧竖向流程步骤菜单 -->
    <aside class="cp-side">
      <div class="cp-brand">
        <div class="cp-brand-mark">HZ</div>
        <div>
          <div class="cp-brand-title">混凝土搅拌站</div>
          <div class="cp-brand-sub">生产调度与罐车管理</div>
        </div>
      </div>

      <div class="cp-side-label">生 产 流 程</div>

      <nav class="cp-steps">
        <div
          v-for="(step, index) in steps"
          :key="step.key"
          class="cp-step"
          :class="{ active: step.key === current }"
          @click="selectStep(step.key)"
        >
          <div class="cp-step-dot">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="cp-step-body">
            <div class="cp-step-title">{{ step.title }}</div>
            <div class="cp-step-sub">{{ step.sub }}</div>
          </div>
        </div>
      </nav>

      <div class="cp-side-foot">
        调度中心 · 白班<br />
        数据每 30 秒由 Redis 缓存刷新
      </div>
    </aside>

    <!-- 右侧主体 -->
    <div class="cp-main">
      <header class="cp-header">
        <div>
          <div class="cp-header-title">{{ activeStep.title }} · {{ activeStep.sub }}</div>
          <div class="cp-header-sub">{{ activeStep.desc }}</div>
        </div>
        <div class="cp-kpis">
          <div class="cp-kpi"><b>{{ dash.orderTotal ?? '-' }}</b><span>任务单</span></div>
          <div class="cp-kpi"><b>{{ dash.producingCount ?? '-' }}</b><span>生产中</span></div>
          <div class="cp-kpi"><b>{{ dash.signedTickets ?? '-' }}</b><span>已签收车次</span></div>
          <div class="cp-kpi"><b>{{ dash.signedVolume ?? '-' }}</b><span>签收方量 m³</span></div>
          <div class="cp-kpi" :class="{ danger: dash.lowStockCount > 0 }">
            <b>{{ dash.lowStockCount ?? '-' }}</b><span>低库存仓</span>
          </div>
          <el-button size="small" @click="loadDashboard">刷新</el-button>
        </div>
      </header>

      <div class="cp-body">
        <KeepAlive>
          <component :is="activeStep.comp" :key="current" />
        </KeepAlive>
      </div>    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from './api'
import { goStep, nav } from './nav'
import MixerLoadView from './views/MixerLoadView.vue'
import MixRatioView from './views/MixRatioView.vue'
import DispatchTrackView from './views/DispatchTrackView.vue'
import SiloView from './views/SiloView.vue'
import QualityView from './views/QualityView.vue'

const steps = [
  {
    key: 'task',
    title: '任务',
    sub: '主机产能负载',
    desc: '按主机看当日完成/计划方量，点竖条派发任务单',
    comp: MixerLoadView
  },
  {
    key: 'mix',
    title: '配料',
    sub: '任务单配合比卡',
    desc: '强度等级徽章 + 环形占比图，就地改每方用量并重算水胶比，可查看该任务单完整检测链',
    comp: MixRatioView
  },
  {
    key: 'gate',
    title: '出厂',
    sub: '罐车发车排队轨道',
    desc: '拖拽胶囊换排队顺序，按「装车→出厂→到工地→签收」推进',
    comp: DispatchTrackView
  },
  {
    key: 'sign',
    title: '签收',
    sub: '料仓液位与补料',
    desc: '筒仓液位柱告警、补料申请与审批',
    comp: SiloView
  },
  {
    key: 'quality',
    title: '质检',
    sub: '试块留样与复检',
    desc: '留样关联已装车发车单，7d/28d 抗压判定与唯一复检形成完整闭环',
    comp: QualityView
  }
]

const current = ref(nav.current)
const dash = ref({})

const activeStep = computed(() => steps.find((s) => s.key === current.value) || steps[0])

function selectStep(key) {
  current.value = key
  goStep(key)
}

// 从任务单/质检详情等跨视图跳转时跟随 nav 切换
watch(
  () => nav.current,
  (key) => {
    if (key !== current.value) current.value = key
  }
)

async function loadDashboard() {
  try {
    const res = await api.dashboard()
    dash.value = res.data || {}
  } catch (e) {
    // 看板失败不影响页面使用
  }
}

onMounted(loadDashboard)
</script>
