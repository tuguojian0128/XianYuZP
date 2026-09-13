<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import { collectOpportunity, crawlShopOpportunities, importOpportunities, polishOpportunity, searchOpportunities, type OpportunityCandidate } from '@/api/merchant'
import type { Account } from '@/types'
import { toast } from '@/utils/toast'
import '@/styles/merchant-workbench.css'

const router = useRouter()
const accounts = ref<Account[]>([])
const accountId = ref<number>()
const sourceMode = ref<'keyword' | 'shop' | 'link'>('keyword')
const keyword = ref('')
const shopUrl = ref('')
const sourceUrl = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const importing = ref(false)
const results = ref<OpportunityCandidate[]>([])
const selectedIds = ref<string[]>([])
const active = ref<OpportunityCandidate>()
const searched = ref(false)
const pageNumber = ref(1)
const hasMore = ref(false)
const total = ref(0)
const errorMessage = ref('')
const edit = reactive({ title: '', description: '' })
const selectedCandidates = computed(() => results.value.filter(item => selectedIds.value.includes(item.itemId)))

const loadAccounts = async () => {
  try {
    const response = await getAccountList()
    accounts.value = response.data?.accounts || []
    accountId.value ||= accounts.value[0]?.id
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '账号列表加载失败，请刷新重试'
  }
}

const resetResults = () => {
  results.value = []; selectedIds.value = []; active.value = undefined; searched.value = false
  pageNumber.value = 1; hasMore.value = false; total.value = 0; edit.title = ''; edit.description = ''
}

const selectActive = (item?: OpportunityCandidate) => {
  active.value = item; edit.title = item?.title || ''; edit.description = item?.description || item?.title || ''
}

const search = async (append = false) => {
  if (sourceMode.value === 'keyword' && !keyword.value.trim()) return toast.error('请输入商品关键词')
  if (sourceMode.value === 'shop' && !shopUrl.value.trim()) return toast.error('请输入闲鱼店铺链接')
  if (sourceMode.value === 'link' && !sourceUrl.value.trim()) return toast.error('请粘贴闲鱼商品链接')
  if (!accountId.value) return toast.error('请先添加并连接闲鱼账号')
  if (append) loadingMore.value = true; else loading.value = true
  errorMessage.value = ''
  try {
    const targetPage = append ? pageNumber.value + 1 : 1
    const common = { xianyuAccountId: accountId.value, pageNumber: targetPage, limit: 30 }
    const response = sourceMode.value === 'keyword'
      ? await searchOpportunities({ ...common, keyword: keyword.value })
      : sourceMode.value === 'shop'
        ? await crawlShopOpportunities({ ...common, shopUrl: shopUrl.value })
        : await collectOpportunity({ sourceUrl: sourceUrl.value.trim(), xianyuAccountId: accountId.value })
    const page = response.data; const pageItems = page?.items || []
    results.value = append ? [...results.value, ...pageItems.filter(item => !results.value.some(current => current.itemId === item.itemId))] : pageItems
    pageNumber.value = page?.pageNumber || targetPage; hasMore.value = sourceMode.value === 'link' ? false : Boolean(page?.hasMore)
    total.value = Number(page?.total || results.value.length); searched.value = true
    if (!append) { selectedIds.value = []; selectActive(results.value[0]); if (sourceMode.value === 'link' && results.value[0]) selectedIds.value = [results.value[0].itemId] }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '闲鱼商品读取失败，请检查账号状态后重试'
    if (!append) {
      results.value = []
      searched.value = true
    }
  } finally { loading.value = false; loadingMore.value = false }
}

const toggle = (item: OpportunityCandidate) => {
  selectActive(item)
  selectedIds.value = selectedIds.value.includes(item.itemId) ? selectedIds.value.filter(id => id !== item.itemId) : [...selectedIds.value, item.itemId]
}

const polish = async () => {
  if (!active.value) return toast.error('请先选择一个商品')
  if (!edit.title.trim()) return toast.error('商品标题不能为空')
  loading.value = true
  try {
    const response = await polishOpportunity({ title: edit.title, description: edit.description })
    if (response.data) {
      edit.title = response.data.title; edit.description = response.data.description
      active.value.title = edit.title; active.value.description = edit.description
      const index = results.value.findIndex(item => item.itemId === active.value?.itemId)
      if (index >= 0) results.value[index] = { ...results.value[index]!, title: edit.title, description: edit.description }
      toast.success('内置知识库优化完成，导入货源库时会保存优化后的文案')
    }
  } finally { loading.value = false }
}

const importSelected = async () => {
  if (!selectedCandidates.value.length) return toast.error('至少选择一个候选商品')
  if (!accountId.value) return toast.error('请先选择采集账号')
  importing.value = true
  try {
    const candidates = selectedCandidates.value.map(item => item.itemId === active.value?.itemId
      ? { ...item, title: edit.title.trim() || item.title, description: edit.description.trim() || item.description } : item)
    const response = await importOpportunities({ candidates, xianyuAccountId: accountId.value })
    toast.success(`已采集 ${response.data?.length || candidates.length} 个商品并写入货源库；请到运营中心转入素材库后再发布`)
  } finally { importing.value = false }
}

onMounted(loadAccounts)
</script>

<template>
  <section class="workbench opportunity">
    <header class="workbench__header"><div><h1>商品采集</h1><p>从闲鱼搜索或读取商品，采集后写入货源库；编辑、库存和发布统一在运营中心完成。</p></div></header>
    <div class="opportunity__layout workbench__section">
      <div class="opportunity__search-pane">
        <div class="workbench__card workbench__toolbar">
          <select v-model="accountId" class="workbench__select opportunity__account"><option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountNote || account.unb }}</option></select>
          <select v-model="sourceMode" class="workbench__select opportunity__mode" @change="resetResults"><option value="keyword">商品搜索</option><option value="shop">店铺采集</option><option value="link">商品链接</option></select>
          <input v-if="sourceMode === 'keyword'" v-model="keyword" class="workbench__input" placeholder="输入商品关键词，例如：华为 Mate 80" @keyup.enter="search(false)">
          <input v-else-if="sourceMode === 'shop'" v-model="shopUrl" class="workbench__input" placeholder="粘贴闲鱼网页版店铺主页完整链接" @keyup.enter="search(false)">
          <input v-else v-model="sourceUrl" class="workbench__input" placeholder="粘贴闲鱼商品详情页链接" @keyup.enter="search(false)">
          <button class="workbench__btn workbench__btn--primary" :disabled="loading || !accounts.length" @click="search(false)">{{ loading ? '读取中' : sourceMode === 'link' ? '读取商品' : '查找商品' }}</button>
        </div>
        <div v-if="!accounts.length" class="workbench__notice workbench__notice--warn">还没有连接闲鱼账号，请先到“闲鱼账号”完成登录；读取商品需要使用自己的账号。</div>
        <div v-if="errorMessage" class="workbench__notice workbench__notice--warn">{{ errorMessage }}</div>
        <div v-if="searched" class="opportunity__result-meta">{{ total > 0 ? `平台共匹配 ${total} 件，` : '' }}当前已加载 {{ results.length }} 件</div>
        <div class="workbench__list workbench__section">
          <article v-for="item in results" :key="item.itemId" class="workbench__item opportunity__result" :class="{ 'opportunity__result--active': active?.itemId === item.itemId }" tabindex="0" @click="toggle(item)" @keydown.enter="toggle(item)">
            <input type="checkbox" :checked="selectedIds.includes(item.itemId)" @click.stop="toggle(item)"><img :src="item.images?.[0]" alt=""><div class="opportunity__result-copy"><h3>{{ item.title }}</h3><div class="workbench__tags"><span class="workbench__tag workbench__tag--good">资料分 {{ item.opportunityScore }}</span><span class="workbench__tag" :class="{ 'workbench__tag--warn': item.riskLevel !== 'LOW' }">{{ item.riskLevel === 'LOW' ? '可采集' : '采集前复核' }}</span><span class="workbench__tag">{{ item.matchReason }}</span></div></div><strong>¥ {{ item.price || '--' }}</strong>
          </article>
          <div v-if="!results.length" class="workbench__empty">{{ searched ? '没有读取到可用商品，请检查链接、关键词、账号状态或闲鱼验证。' : '选择搜索、店铺采集或商品链接，读取闲鱼商品。' }}</div>
          <button v-if="hasMore" class="workbench__btn opportunity__more" :disabled="loadingMore" @click="search(true)">{{ loadingMore ? '加载中' : '加载更多商品' }}</button>
        </div>
      </div>
      <aside class="workbench__card opportunity__preview">
        <template v-if="active">
          <img :src="active.images?.[0]" alt=""><h2>{{ active.title }}</h2><strong>¥ {{ active.price || '--' }}</strong><p>{{ active.matchReason }}</p>
          <label class="workbench__field">采集后的标题<input v-model="edit.title" class="workbench__input" maxlength="120"></label>
          <label class="workbench__field">采集后的详情<textarea v-model="edit.description" class="workbench__textarea" maxlength="3000"></textarea></label>
          <div class="workbench__actions opportunity__preview-actions"><button class="workbench__btn" :disabled="loading" @click="polish">知识库优化文案（免费）</button><button class="workbench__btn workbench__btn--primary" :disabled="importing || !selectedIds.length" @click="importSelected">{{ importing ? '写入中' : '采集到货源库' }}</button></div>
          <button class="workbench__btn opportunity__operations-link" @click="router.push('/operations')">去运营中心管理货源和素材</button>
        </template>
        <div v-else class="workbench__empty">读取商品后查看预览</div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.opportunity__layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 14px; }.opportunity__account { max-width: 180px; }.opportunity__mode { max-width: 130px; }.opportunity__search-pane { min-width: 0; }.opportunity__result-meta { margin: 12px 0 -4px; color: #667085; font-size: 12px; }.opportunity__result { width: 100%; min-width: 0; grid-template-columns: auto 56px minmax(0, 1fr) auto; color: inherit; text-align: left; cursor: pointer; }.opportunity__result-copy { min-width: 0; }.opportunity__result > strong { white-space: nowrap; }.opportunity__result--active { border-color: #84adff; background: #f5f8ff; }.opportunity__preview { position: sticky; top: 16px; align-self: start; }.opportunity__preview > img { width: 100%; aspect-ratio: 4 / 3; border-radius: 8px; object-fit: cover; }.opportunity__preview h2 { display: -webkit-box; overflow: hidden; font-size: 15px; line-height: 1.5; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }.opportunity__preview > strong { color: #d92d20; font-size: 22px; }.opportunity__preview > p { color: #667085; font-size: 12px; }.opportunity__preview .workbench__field { margin-top: 12px; }.opportunity__preview-actions { flex-wrap: wrap; margin-top: 14px; }.opportunity__operations-link { width: 100%; margin-top: 10px; justify-content: center; }.opportunity__more { width: 100%; justify-content: center; }@media (max-width: 900px) { .opportunity__layout { grid-template-columns: 1fr; }.opportunity__preview { position: static; } }@media (max-width: 767px) { .opportunity__result { grid-template-columns: auto 52px minmax(0, 1fr); align-items: start; }.opportunity__result img { width: 52px; height: 52px; }.opportunity__result > strong { grid-column: 3; }.opportunity__preview { padding-bottom: max(16px, env(safe-area-inset-bottom)); } }
</style>
