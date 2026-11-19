<script setup>
import { computed } from 'vue'
import { statusDisplay } from '../display.js'

const props = defineProps({ status: String })
const display = computed(() => statusDisplay[props.status] ?? [props.status || '—', 'gray'])
</script>

<template>
  <span class="status-tag" :class="`status-tag--${display[1]}`">{{ display[0] }}</span>
</template>
