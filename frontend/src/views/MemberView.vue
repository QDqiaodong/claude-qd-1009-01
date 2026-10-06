<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const levelFilter = ref('')
const dialog = ref(false)
const saving = ref(false)
const levels = ['普通', '银卡', '金卡', '钻石']

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const rechargeAmount = ref('')
const rechargeRemark = ref('')
const recharging = ref(false)

const form = ref(blank())
function blank() {
  return { memberNo: '', memberName: '', phone: '', level: '普通', balance: 0 }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listMembers(levelFilter.value ? { level: levelFilter.value } : {})
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, balance: Number(form.value.balance) }
    await api.createMember(d)
    tip.value = `会员 ${d.memberName} 已建档${d.balance > 0 ? '，初始储值已入流水' : ''}`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function changeLevel(row, level) {
  error.value = ''; tip.value = ''
  try {
    await api.updateMemberLevel(row.id, level)
    tip.value = `${row.memberName} 等级已更新为「${level}」`
    await load()
  } catch (e) { error.value = e.message }
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  rechargeAmount.value = ''
  rechargeRemark.value = ''
  error.value = ''
  try {
    detail.value = await api.getMemberDetail(row.id)
  } catch (e) {
    error.value = e.message
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function recharge() {
  if (!detail.value || !rechargeAmount.value) return
  recharging.value = true; error.value = ''; tip.value = ''
  try {
    await api.rechargeMember(detail.value.id, Number(rechargeAmount.value), rechargeRemark.value)
    tip.value = '储值成功：余额与资金流水已同时入账'
    rechargeAmount.value = ''
    rechargeRemark.value = ''
    await Promise.all([load(), refreshDetail()])
  } catch (e) { error.value = e.message } finally { recharging.value = false }
}

async function refreshDetail() {
  if (!detail.value) return
  detail.value = await api.getMemberDetail(detail.value.id)
}

function money(v) {
  return Number(v || 0).toFixed(2)
}

function signedAmount(row) {
  return `${row.type === '储值' ? '+' : '-'}${money(row.amount)}`
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">会员 · 等级与预存</span>
        <span class="panel-desc">余额、已结账消费和逐笔资金流水可在详情中对账</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">会员数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">钻石</div><div class="v">{{ rows.filter(r => r.level === '钻石').length }}</div></div>
        <div class="stat"><div class="k">金卡</div><div class="v">{{ rows.filter(r => r.level === '金卡').length }}</div></div>
        <div class="stat"><div class="k">银卡</div><div class="v">{{ rows.filter(r => r.level === '银卡').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="levelFilter" placeholder="全部等级" clearable style="width: 140px" @change="load">
          <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建会员</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="memberNo" label="会员号" width="100" />
        <el-table-column prop="memberName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="当前余额(元)" width="130">
          <template #default="{ row }">
            <strong>{{ money(row.balance) }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.level === '钻石' ? 'danger' : row.level === '金卡' ? 'warning' : 'success'">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">余额/流水详情</el-button>
          </template>
        </el-table-column>
        <el-table-column label="调整等级" width="240">
          <template #default="{ row }">
            <el-button v-for="l in levels" :key="l" size="small" :disabled="row.level === l" @click="changeLevel(row, l)">{{ l }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建会员" width="560px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="会员号"><el-input v-model="form.memberNo" placeholder="如 M005" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.memberName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="初始余额(元)"><el-input v-model="form.balance" type="number" :min="0" step="1" /></el-form-item>
        <el-form-item label="等级" class="full">
          <el-radio-group v-model="form.level">
            <el-radio v-for="l in levels" :key="l" :value="l">{{ l }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="会员余额与资金流水" size="78%">
      <div v-loading="detailLoading" style="padding: 0 20px 24px">
        <template v-if="detail">
          <el-descriptions :column="4" border>
            <el-descriptions-item label="会员号">{{ detail.memberNo }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ detail.memberName }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
            <el-descriptions-item label="等级">{{ detail.level }}</el-descriptions-item>
            <el-descriptions-item label="当前余额">
              <strong style="color: #67c23a; font-size: 18px">{{ money(detail.balance) }} 元</strong>
            </el-descriptions-item>
            <el-descriptions-item label="已结账笔数">{{ detail.settledBookings.length }}</el-descriptions-item>
            <el-descriptions-item label="流水笔数">{{ detail.transactions.length }}</el-descriptions-item>
            <el-descriptions-item label="储值扣款笔数">
              {{ detail.transactions.filter(x => x.type === '消费扣款').length }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="panel" style="margin-top: 18px">
            <div class="panel-head">
              <span class="panel-title">会员储值</span>
              <span class="panel-desc">储值成功才更新余额并追加流水</span>
            </div>
            <div class="toolbar">
              <el-input v-model="rechargeAmount" type="number" :min="0.01" step="1" placeholder="储值金额" style="width: 180px" />
              <el-input v-model="rechargeRemark" placeholder="备注（可选）" style="width: 280px" />
              <el-button type="success" :disabled="!rechargeAmount" :loading="recharging" @click="recharge">确认储值</el-button>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <span class="panel-title">已结账消费</span>
              <span class="panel-desc">消费单编号可与下方扣款流水关联</span>
            </div>
            <el-table :data="detail.settledBookings" border stripe>
              <el-table-column label="消费单" width="100">
                <template #default="{ row }">#{{ row.bookingId }}</template>
              </el-table-column>
              <el-table-column label="结账金额(元)" width="130">
                <template #default="{ row }">{{ money(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column prop="paymentMethod" label="结账方式" width="120" />
              <el-table-column prop="settledAt" label="结账时间" min-width="180" />
            </el-table>
          </div>

          <div class="panel">
            <div class="panel-head">
              <span class="panel-title">资金流水（只追加）</span>
              <span class="panel-desc">每笔记录前余、后余、发生时间和关联消费单</span>
            </div>
            <el-table :data="detail.transactions" border stripe>
              <el-table-column prop="id" label="流水#" width="80" />
              <el-table-column prop="type" label="类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.type === '储值' ? 'success' : 'danger'">{{ row.type }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="变动(元)" width="120">
                <template #default="{ row }">
                  <span :style="{ color: row.type === '储值' ? '#67c23a' : '#f56c6c' }">{{ signedAmount(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="变动前余额" width="120">
                <template #default="{ row }">{{ money(row.balanceBefore) }}</template>
              </el-table-column>
              <el-table-column label="变动后余额" width="120">
                <template #default="{ row }">{{ money(row.balanceAfter) }}</template>
              </el-table-column>
              <el-table-column label="关联消费单" width="110">
                <template #default="{ row }">{{ row.bookingId ? `#${row.bookingId}` : '-' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="220" />
              <el-table-column prop="createdAt" label="发生时间" min-width="180" />
            </el-table>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>
