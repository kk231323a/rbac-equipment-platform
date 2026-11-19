<script setup>
import { CircleClose, Files, Loading } from '@element-plus/icons-vue'

defineProps({
  state: { type: Object, required: true },
  emptyText: { type: String, required: true },
})
defineEmits(['retry', 'page'])
</script>

<template>
  <section class="list-panel" :aria-busy="state.loading">
    <div v-if="state.loading" class="panel-state" role="status">
      <Loading class="panel-state-icon spinning" aria-hidden="true" />
      <p>正在加载…</p>
    </div>
    <div v-else-if="state.error" class="panel-state" role="alert">
      <CircleClose class="panel-state-icon state-error" aria-hidden="true" />
      <p>{{ state.error }}</p>
      <el-button @click="$emit('retry')">重新加载</el-button>
    </div>
    <div v-else-if="state.rows.length === 0" class="panel-state">
      <Files class="panel-state-icon" aria-hidden="true" />
      <p>{{ emptyText }}</p>
    </div>
    <template v-else>
      <div class="table-scroll" tabindex="0" role="region" aria-label="表格">
        <slot />
      </div>
      <div v-if="state.pageSize" class="pager">
        <span>共 {{ state.total }} 条</span>
        <el-pagination
          :current-page="state.pageNum"
          :page-size="state.pageSize"
          :total="state.total"
          :pager-count="5"
          layout="prev, pager, next"
          @current-change="$emit('page', $event)"
        />
      </div>
    </template>
  </section>
</template>
