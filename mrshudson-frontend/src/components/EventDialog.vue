<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑事件' : '新建事件'"
    width="500px"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入事件标题" />
      </el-form-item>

      <el-form-item label="时间" prop="timeRange">
        <el-date-picker
          v-model="form.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>

      <el-form-item label="地点">
        <el-input v-model="form.location" placeholder="请输入地点（可选）" />
      </el-form-item>

      <el-form-item label="分类">
        <el-radio-group v-model="form.category">
          <el-radio-button label="WORK">工作</el-radio-button>
          <el-radio-button label="PERSONAL">个人</el-radio-button>
          <el-radio-button label="FAMILY">家庭</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="提醒">
        <el-select v-model="form.reminderMinutes" placeholder="提前提醒">
          <el-option label="不提醒" :value="0" />
          <el-option label="提前5分钟" :value="5" />
          <el-option label="提前15分钟" :value="15" />
          <el-option label="提前30分钟" :value="30" />
          <el-option label="提前1小时" :value="60" />
          <el-option label="提前1天" :value="1440" />
        </el-select>
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入备注（可选）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { CreateEventRequest, EventResponse } from '../api/calendar'

const props = defineProps<{
  modelValue: boolean
  initialDate?: string
  event?: EventResponse | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [data: CreateEventRequest]
  delete: [eventId: number]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.event)

const formRef = ref<FormInstance>()

const form = ref({
  title: '',
  timeRange: [] as string[],
  location: '',
  category: 'PERSONAL' as 'WORK' | 'PERSONAL' | 'FAMILY',
  reminderMinutes: 15,
  description: ''
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入事件标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度在1-200个字符之间', trigger: 'blur' }
  ],
  timeRange: [
    { required: true, message: '请选择时间范围', trigger: 'change' }
  ]
}

// 监听弹窗打开和事件变化
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    initForm()
  }
})

const initForm = () => {
  if (props.event) {
    // 编辑模式
    form.value = {
      title: props.event.title,
      timeRange: [props.event.startTime, props.event.endTime],
      location: props.event.location || '',
      category: props.event.category,
      reminderMinutes: props.event.reminderMinutes,
      description: props.event.description || ''
    }
  } else {
    // 新建模式
    const defaultDate = props.initialDate || formatDate(new Date())
    const startTime = `${defaultDate}T09:00:00`
    const endTime = `${defaultDate}T10:00:00`

    form.value = {
      title: '',
      timeRange: [startTime, endTime],
      location: '',
      category: 'PERSONAL',
      reminderMinutes: 15,
      description: ''
    }
  }
}

const formatDate = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate((valid) => {
    if (valid) {
      const data: CreateEventRequest = {
        title: form.value.title,
        startTime: form.value.timeRange[0],
        endTime: form.value.timeRange[1],
        location: form.value.location || undefined,
        category: form.value.category,
        reminderMinutes: form.value.reminderMinutes,
        description: form.value.description || undefined
      }
      emit('save', data)
    }
  })
}

const handleClose = () => {
  formRef.value?.resetFields()
}
</script>

<style scoped>
:deep(.el-date-editor--datetimerange) {
  width: 100%;
}
</style>
