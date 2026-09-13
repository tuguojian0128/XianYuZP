<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getAccountList } from '@/api/account'
import { getResources, getTasks, saveResource, searchOpportunities, type MerchantResource, type OpportunityCandidate } from '@/api/merchant'
import { getMessageList, type ChatMessage } from '@/api/message'
import type { Account } from '@/types'
import { toast } from '@/utils/toast'
import '@/styles/merchant-workbench.css'

type AcquisitionTab = 'comments' | 'messages'
type SavedRule = { keywords: string; message: string; enabled: boolean }
const accounts = ref<Account[]>([])
const accountId = ref<number>()
const activeTab = ref<AcquisitionTab>('comments')
const errorMessage = ref('')

const commentKeyword = ref('')
const commentText = ref('礼貌接楼，同出同款，有需要可以看看我的主页。')
const commentResults = ref<OpportunityCandidate[]>([])
const commentSearched = ref(false)
const commentLoading = ref(false)
const commentLimit = ref(5)

const triggerKeywords = ref('怎么卖,怎么做,多少钱')
const privateMessage = ref('你好，这款商品我这边也有，方便的话可以看一下我的主页，有需要随时聊。')
const privateEnabled = ref(false)
const scanning = ref(false)
const matchedCount = ref(0)
const sentCount = ref(0)
const lastScanTime = ref('')
const ruleResource = ref<MerchantResource>()
const seenMessageIds = new Set<string>()

const keywordList = computed(() => triggerKeywords.value.split(/[,，\n]/).map(value => value.trim()).filter(Boolean).slice(0, 20))
const validMessage = computed(() => privateMessage.value.trim().length > 0 && privateMessage.value.trim().length <= 500)

const loadAccounts = async () => {
  const response = await getAccountList()
  accounts.value = response.data?.accounts || []
  accountId.value ||= accounts.value[0]?.id
}

const loadRule = async () => {
  try {
    const response = await getResources('ACQUISITION_RULE')
    const resource = response.data?.[0]
    if (!resource) return
    ruleResource.value = resource
    const data = resource.data || {}
    const saved: SavedRule = {
      keywords: String(data.triggerKeywords || ''),
      message: String(data.privateMessage || ''),
      enabled: Boolean(data.privateEnabled)
    }
    commentKeyword.value = String(data.keyword || '')
    commentLimit.value = Number(data.searchLimit || 5)
    commentText.value = String(data.commentText || commentText.value)
    if (typeof saved.keywords === 'string') triggerKeywords.value = saved.keywords
    if (typeof saved.message === 'string') privateMessage.value = saved.message
    if (typeof saved.enabled === 'boolean') privateEnabled.value = saved.enabled
  } catch {
    errorMessage.value = '获客规则读取失败，请检查后端连接'
  }
}

const loadTaskStats = async () => {
  try {
    const response = await getTasks({ taskType: 'ACQUISITION_MESSAGE', limit: 100 })
    sentCount.value = (response.data || []).filter(task => task.status === 2).length
  } catch {
    // 页面状态不影响后台任务继续运行。
  }
}

const saveRule = async () => {
  if (!keywordList.value.length) return toast.error('请至少填写一个触发关键词')
  if (!validMessage.value) return toast.error('私信内容长度应为1至500个字符')
  if (!accountId.value) return toast.error('请先添加并连接闲鱼账号')
  const rule: SavedRule = { keywords: triggerKeywords.value, message: privateMessage.value.trim(), enabled: privateEnabled.value }
  const nextRun = ruleResource.value?.scheduledTime || new Date().toISOString().slice(0, 19)
  const response = await saveResource({
    id: ruleResource.value?.id,
    resourceType: 'ACQUISITION_RULE',
    name: `获客规则 · ${commentKeyword.value.trim() || '未命名'}`,
    status: privateEnabled.value || commentKeyword.value.trim() ? 1 : 0,
    xianyuAccountId: accountId.value,
    scheduledTime: nextRun,
    data: {
      keyword: commentKeyword.value.trim(),
      searchLimit: Math.max(1, Math.min(commentLimit.value, 50)),
      commentText: commentText.value.trim(),
      commentEnabled: false,
      triggerKeywords: rule.keywords,
      privateMessage: rule.message,
      privateEnabled: rule.enabled,
      maxMessagesPerRun: 5,
      intervalMinutes: 15
    }
  })
  ruleResource.value = response.data
  toast.success('私信获客规则已保存')
}

const searchComments = async () => {
  if (!accountId.value) return toast.error('请先添加并连接闲鱼账号')
  if (!commentKeyword.value.trim()) return toast.error('请输入要搜索的商品关键词')
  commentLoading.value = true
  errorMessage.value = ''
  try {
    const response = await searchOpportunities({
      xianyuAccountId: accountId.value,
      keyword: commentKeyword.value.trim(),
      pageNumber: 1,
      limit: Math.max(1, Math.min(commentLimit.value, 20))
    })
    commentResults.value = response.data?.items || []
    commentSearched.value = true
  } catch (error: any) {
    errorMessage.value = error?.message || '商品搜索失败，请检查账号连接状态'
    commentResults.value = []
    commentSearched.value = true
  } finally {
    commentLoading.value = false
  }
}

const copyComment = async () => {
  if (!commentText.value.trim()) return toast.error('请先填写评论文案')
  try {
    await navigator.clipboard.writeText(commentText.value.trim())
    toast.success('评论文案已复制，可打开商品后发布')
  } catch {
    toast.error('复制失败，请手动选择文案')
  }
}

const normalizeText = (value?: string) => (value || '').replace(/\s+/g, ' ').trim()
const messageMatches = (message: ChatMessage) => {
  const text = normalizeText(message.msgContent).toLowerCase()
  return text.length > 0 && keywordList.value.some(keyword => text.includes(keyword.toLowerCase()))
}

const scanPrivateMessages = async () => {
  if (!accountId.value || scanning.value) return
  scanning.value = true
  try {
    const response = await getMessageList({
      xianyuAccountId: accountId.value,
      pageNum: 1,
      pageSize: 100,
      filterCurrentAccount: true
    }, true)
    const messages = response.data?.list || []
    const matches = messages.filter(message => messageMatches(message))
    matchedCount.value += matches.filter(message => !seenMessageIds.has(`${accountId.value}:${message.id}`)).length
    lastScanTime.value = new Date().toLocaleTimeString()
  } catch (error: any) {
    errorMessage.value = error?.message || '消息同步失败，请检查账号连接状态'
  } finally {
    scanning.value = false
  }
}

const togglePrivateRule = async () => {
  privateEnabled.value = !privateEnabled.value
  await saveRule()
  if (privateEnabled.value) void scanPrivateMessages()
}

watch(accountId, () => {
  seenMessageIds.clear()
  matchedCount.value = 0
  sentCount.value = 0
  lastScanTime.value = ''
})

onMounted(async () => {
  await loadAccounts()
  await loadRule()
  await loadTaskStats()
})
</script>

<template>
  <section class="workbench acquisition">
    <header class="workbench__header">
      <div>
        <h1>闲鱼获客</h1>
        <p>通过公开商品搜索发现潜在买家，并按关键词识别会话中的购买意向。</p>
      </div>
      <select v-model="accountId" class="workbench__select" aria-label="获客账号">
        <option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountNote || account.unb }}</option>
      </select>
    </header>

    <nav class="acquisition__tabs" aria-label="获客方式">
      <button :class="{ active: activeTab === 'comments' }" @click="activeTab = 'comments'">评论获客</button>
      <button :class="{ active: activeTab === 'messages' }" @click="activeTab = 'messages'">关键词私信</button>
    </nav>

    <div v-if="errorMessage" class="workbench__notice workbench__notice--warn">{{ errorMessage }}</div>

    <div v-if="activeTab === 'comments'" class="acquisition__layout">
      <div class="acquisition__main">
        <div class="workbench__card acquisition__search">
          <input v-model="commentKeyword" class="workbench__input" placeholder="搜索同行商品关键词，例如：课程资料" @keyup.enter="searchComments">
          <label class="acquisition__limit">结果数 <input v-model.number="commentLimit" type="number" min="1" max="20"></label>
          <button class="workbench__btn workbench__btn--primary" :disabled="commentLoading" @click="searchComments">{{ commentLoading ? '搜索中' : '搜索商品' }}</button>
        </div>
        <div class="acquisition__result-meta">{{ commentSearched ? `已找到 ${commentResults.length} 个商品` : '先搜索商品，再选择需要查看的同行商品' }}</div>
        <div class="acquisition__results">
          <article v-for="item in commentResults" :key="item.itemId" class="workbench__card acquisition__result">
            <img v-if="item.images?.[0]" :src="item.images[0]" :alt="item.title">
            <div class="acquisition__result-copy"><h2>{{ item.title }}</h2><span>{{ item.sellerNick || '平台卖家' }} · ¥{{ item.price || '--' }}</span><small>{{ item.matchReason }}</small></div>
            <a class="workbench__btn" :href="item.sourceUrl" target="_blank" rel="noopener noreferrer">打开商品</a>
          </article>
          <div v-if="commentSearched && !commentResults.length" class="workbench__card workbench__empty">没有找到商品，请换一个关键词或检查账号连接。</div>
        </div>
      </div>
      <aside class="workbench__card acquisition__composer">
        <h2>评论文案</h2>
        <p>Docker 后台会按规则搜索并生成候选。闲鱼没有稳定的公开评论写入接口，评论发布仍需在商品页确认。</p>
        <textarea v-model="commentText" class="workbench__textarea" maxlength="500" rows="6" placeholder="输入礼貌、真实的评论内容"></textarea>
        <div class="acquisition__composer-footer"><span>{{ commentText.trim().length }} / 500</span><button class="workbench__btn workbench__btn--primary" @click="copyComment">复制文案</button></div>
      </aside>
    </div>

    <div v-else class="acquisition__message-layout">
      <div class="workbench__card acquisition__rule-card">
        <div class="acquisition__rule-heading"><div><h2>关键词触发私信</h2><p>同步当前账号收到的会话消息，命中关键词后自动发送一次。</p></div><button class="acquisition__switch" :class="{ active: privateEnabled }" :aria-pressed="privateEnabled" @click="togglePrivateRule"><span></span>{{ privateEnabled ? '已开启' : '已关闭' }}</button></div>
        <label class="workbench__field"><span>触发关键词</span><textarea v-model="triggerKeywords" class="workbench__textarea" rows="3" placeholder="多个关键词用逗号或换行分隔"></textarea><small>最多 20 个；包含任一关键词即视为有购买意向</small></label>
        <label class="workbench__field"><span>私信内容</span><textarea v-model="privateMessage" class="workbench__textarea" maxlength="500" rows="5" placeholder="输入要发送给潜在买家的内容"></textarea><small>{{ privateMessage.trim().length }} / 500</small></label>
        <div class="acquisition__rule-actions"><button class="workbench__btn" @click="saveRule">保存规则</button><button class="workbench__btn" :disabled="scanning" @click="scanPrivateMessages">{{ scanning ? '同步中' : '立即同步一次' }}</button></div>
      </div>
      <aside class="workbench__card acquisition__stats">
        <h2>运行状态</h2>
        <dl><div><dt>命中消息</dt><dd>{{ matchedCount }}</dd></div><div><dt>已发送私信</dt><dd>{{ sentCount }}</dd></div><div><dt>最近同步</dt><dd>{{ lastScanTime || '尚未同步' }}</dd></div></dl>
        <p>规则已保存到 Docker 后台，页面关闭后仍会按 15 分钟间隔运行。单轮最多发送 5 条，失败任务会自动重试。</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.acquisition { min-width: 0; }
.acquisition__tabs { display: flex; gap: 4px; margin-bottom: 16px; border-bottom: 1px solid #e4e7ec; }
.acquisition__tabs button { border: 0; background: transparent; color: #667085; padding: 10px 14px; cursor: pointer; border-bottom: 2px solid transparent; font: inherit; }
.acquisition__tabs button.active { color: #155eef; border-bottom-color: #155eef; font-weight: 600; }
.acquisition__layout, .acquisition__message-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
.acquisition__main { min-width: 0; }
.acquisition__search { display: flex; gap: 10px; align-items: center; }
.acquisition__search .workbench__input { min-width: 0; flex: 1; }
.acquisition__limit { display: flex; align-items: center; gap: 6px; color: #667085; font-size: 13px; white-space: nowrap; }
.acquisition__limit input { width: 64px; padding: 8px; border: 1px solid #d0d5dd; border-radius: 6px; }
.acquisition__result-meta { margin: 14px 0 8px; color: #667085; font-size: 12px; }
.acquisition__results { display: flex; flex-direction: column; gap: 10px; }
.acquisition__result { display: grid; grid-template-columns: 64px minmax(0, 1fr) auto; gap: 12px; align-items: center; padding: 12px; }
.acquisition__result img { width: 64px; height: 64px; object-fit: cover; border-radius: 6px; background: #f2f4f7; }
.acquisition__result-copy { min-width: 0; }
.acquisition__result-copy h2 { margin: 0 0 4px; font-size: 14px; line-height: 1.45; overflow-wrap: anywhere; }
.acquisition__result-copy span, .acquisition__result-copy small { display: block; color: #667085; font-size: 12px; }
.acquisition__result-copy small { margin-top: 4px; }
.acquisition__composer, .acquisition__stats { position: sticky; top: 16px; }
.acquisition__composer h2, .acquisition__stats h2, .acquisition__rule-card h2 { margin: 0 0 8px; font-size: 16px; }
.acquisition__composer p, .acquisition__stats p, .acquisition__rule-heading p { margin: 0 0 14px; color: #667085; font-size: 12px; line-height: 1.6; }
.acquisition__composer-footer, .acquisition__rule-actions, .acquisition__rule-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.acquisition__composer-footer { margin-top: 8px; color: #98a2b3; font-size: 12px; }
.acquisition__rule-card { padding: 20px; }
.acquisition__rule-heading { align-items: flex-start; margin-bottom: 18px; }
.acquisition__rule-heading p { margin-bottom: 0; }
.acquisition__switch { border: 1px solid #d0d5dd; border-radius: 999px; background: #fff; color: #667085; padding: 5px 10px 5px 6px; display: inline-flex; align-items: center; gap: 6px; cursor: pointer; white-space: nowrap; }
.acquisition__switch span { width: 16px; height: 16px; border-radius: 50%; background: #98a2b3; transition: transform 160ms ease, background 160ms ease; }
.acquisition__switch.active { border-color: #12b76a; color: #027a48; }
.acquisition__switch.active span { background: #12b76a; transform: translateX(2px); }
.acquisition__rule-actions { justify-content: flex-start; margin-top: 16px; }
.acquisition__stats dl { margin: 0; }
.acquisition__stats dl div { display: flex; align-items: baseline; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid #eaecf0; }
.acquisition__stats dt { color: #667085; font-size: 13px; }
.acquisition__stats dd { margin: 0; color: #101828; font-size: 18px; font-weight: 600; }
@media (max-width: 900px) { .acquisition__layout, .acquisition__message-layout { grid-template-columns: 1fr; } .acquisition__composer, .acquisition__stats { position: static; } }
@media (max-width: 640px) { .acquisition__search { align-items: stretch; flex-direction: column; } .acquisition__limit { justify-content: space-between; } .acquisition__result { grid-template-columns: 52px minmax(0, 1fr); } .acquisition__result img { width: 52px; height: 52px; } .acquisition__result > a { grid-column: 2; justify-self: start; } }
</style>
