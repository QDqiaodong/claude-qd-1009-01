<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const bookings = ref([])
const members = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const settleId = ref('')
const paymentMethod = ref('储值余额')
const settling = ref(false)

const paymentMethods = ['现金', '银行卡', '储值余额']
const form = ref(blank())
function blank() { return { bookingId: '', itemName: '', amount: '' } }

async function load() {
  loading.value = true; error.value = ''
  try {
    const [c, b, m] = await Promise.all([
      api.listConsumptions(),
      api.listBookings(),
      api.listMembers()
    ])
    rows.value = c || []
    bookings.value = b || []
    members.value = m || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function bookingLabel(id) {
  const b = bookings.value.find(x => x.id === id)
  if (!b) return `预订#${id}`
  const member = memberName(b.memberId)
  return `预订#${b.id}（${member} / ${b.partySize}人 / ${b.status}）`
}

function memberName(id) {
  const m = members.value.find(x => x.id === id)
  return m ? m.memberName : `会员#${id}`
}

function bookingStatus(id) {
  return bookings.value.find(x => x.id === id)?.status || '-'
}

function bookingTotal(id) {
  return rows.value
    .filter(x => x.bookingId === id)
    .reduce((sum, x) => sum + Number(x.amount || 0), 0)
}

const openBookings = computed(() =>
  bookings.value.filter(x => x.status !== '已完成' && x.status !== '已取消')
)
const settledBookings = computed(() =>
  bookings.value
    .filter(x => x.status === '已完成')
    .sort((a, b) => (b.settledAt || '').localeCompare(a.settledAt || ''))
)
const selectedBooking = computed(() => bookings.value.find(x => x.id === Number(settleId.value)))
const selectedMember = computed(() => {
  const b = selectedBooking.value
  return b ? members.value.find(x => x.id === b.memberId) : null
})
const selectedTotal = computed(() => selectedBooking.value ? bookingTotal(selectedBooking.value.id) : 0)
const balanceShort = computed(() =>
  paymentMethod.value === '储值余额' &&
  selectedMember.value &&
  Number(selectedMember.value.balance) < Number(selectedTotal.value)
)

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    await api.createConsumption({
      bookingId: Number(form.value.bookingId),
      itemName: form.value.itemName,
      amount: Number(form.value.amount)
    })
    tip.value = `已记消费：${form.value.itemName}`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function settle() {
  if (!settleId.value) return
  settling.value = true; error.value = ''; tip.value = ''
  try {
    const result = await api.settleBooking(Number(settleId.value), paymentMethod.value)
    if (result.paymentMethod === '储值余额') {
      tip.value = `结账成功：消费单已完成，扣款 ${result.totalAmount} 元，流水#${result.transactionId}，余额 ${result.balanceBefore} → ${result.balanceAfter}`
    } else {
      tip.value = `结账成功：消费单已完成，${result.paymentMethod}收款 ${result.totalAmount} 元，未扣减储值余额`
    }
    settleId.value = ''
    await load()
  } catch (e) {
    error.value = `${e.message}；消费单未置为已结账，储值余额和流水未变更`
  } finally { settling.value = false }
}

function money(v) {
  return Number(v || 0).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">消费 · 挂单与结账</span>
        <span class="panel-desc">储值结账时消费单、余额、资金流水在同一事务提交</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">记一笔消费</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="消费单" min-width="240">
          <template #default="{ row }">{{ bookingLabel(row.bookingId) }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="项目" min-width="120" />
        <el-table-column label="金额(元)" width="110">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="at" label="挂单时间" min-width="170" />
        <el-table-column label="消费单状态" width="110">
          <template #default="{ row }">
            <el-tag :type="bookingStatus(row.bookingId) === '已完成' ? 'success' : 'warning'">
              {{ bookingStatus(row.bookingId) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="记一笔消费" width="560px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="消费单">
          <el-select v-model="form.bookingId" filterable placeholder="选择未结账消费单" style="width: 100%">
            <el-option
              v-for="b in openBookings"
              :key="b.id"
              :label="bookingLabel(b.id)"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="form.itemName" placeholder="如 啤酒 / 果盘 / 套餐" /></el-form-item>
        <el-form-item label="金额(元)"><el-input v-model="form.amount" type="number" :min="0" step="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">记录</el-button>
      </template>
    </el-dialog>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">结账开单</span>
        <span class="panel-desc">只有选择「储值余额」才校验并扣减余额；余额不足会整笔拒绝</span>
      </div>
      <div class="toolbar" style="align-items: flex-start">
        <el-select v-model="settleId" placeholder="选择未结账消费单" style="width: 310px">
          <el-option v-for="b in openBookings" :key="b.id" :label="bookingLabel(b.id)" :value="b.id" />
        </el-select>
        <el-radio-group v-model="paymentMethod">
          <el-radio v-for="m in paymentMethods" :key="m" :value="m">{{ m }}</el-radio>
        </el-radio-group>
        <el-button
          type="success"
          :disabled="!settleId || balanceShort"
          :loading="settling"
          @click="settle"
        >确认结账</el-button>
      </div>

      <el-alert
        v-if="selectedBooking && paymentMethod === '储值余额' && selectedMember"
        :type="balanceShort ? 'error' : 'success'"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        :title="`会员 ${selectedMember.memberName}：当前余额 ${money(selectedMember.balance)} 元；本单合计 ${money(selectedTotal)} 元${balanceShort ? '，余额不足，不能结账' : ''}`"
      />
      <el-alert
        v-if="selectedBooking && paymentMethod !== '储值余额'"
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        :title="`${paymentMethod}结算只更新消费单状态，不校验也不扣减储值余额；本单合计 ${money(selectedTotal)} 元`"
      />
    </div>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">已结账消费单</span>
        <span class="panel-desc">结账金额、方式和时间用于与会员流水核对</span>
      </div>
      <el-table :data="settledBookings" border stripe>
        <el-table-column prop="id" label="消费单" width="90">
          <template #default="{ row }">#{{ row.id }}</template>
        </el-table-column>
        <el-table-column label="会员" min-width="140">
          <template #default="{ row }">{{ memberName(row.memberId) }}</template>
        </el-table-column>
        <el-table-column label="金额(元)" width="120">
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="结账方式" width="120" />
        <el-table-column prop="settledAt" label="结账时间" min-width="170" />
        <el-table-column label="状态" width="100">
          <template #default><el-tag type="success">已完成</el-tag></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
