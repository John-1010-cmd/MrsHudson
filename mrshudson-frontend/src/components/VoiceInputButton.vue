<template>
  <div class="voice-input-button">
    <!-- 录音按钮 -->
    <el-button
      :type="isRecording ? 'danger' : 'info'"
      :class="['voice-btn', { 'recording': isRecording }]"
      @mousedown="startRecording"
      @mouseup="stopRecording"
      @mouseleave="handleMouseLeave"
      @touchstart.prevent="startRecording"
      @touchend.prevent="stopRecording"
      :disabled="disabled || loading"
      circle
    >
      <el-icon :size="20">
        <Microphone v-if="!isRecording" />
        <CircleCheck v-else />
      </el-icon>
    </el-button>

    <!-- 录音中弹窗 -->
    <el-dialog
      v-model="showRecordingDialog"
      title="正在录音..."
      width="300px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      align-center
    >
      <div class="recording-content">
        <!-- 录音动画 -->
        <div class="recording-animation">
          <div
            v-for="i in 5"
            :key="i"
            class="sound-bar"
            :style="{ animationDelay: `${i * 0.1}s`, height: `${getBarHeight(i)}px` }"
          ></div>
        </div>

        <!-- 录音时长 -->
        <div class="recording-time">{{ formatTime(recordingTime) }}</div>

        <!-- 提示文字 -->
        <div class="recording-tip">松开结束录音，最长60秒</div>

        <!-- 取消按钮 -->
        <el-button type="danger" @click="cancelRecording" size="small">
          取消录音
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Microphone, CircleCheck } from '@element-plus/icons-vue'

interface Props {
  disabled?: boolean
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  loading: false
})

const emit = defineEmits<{
  (e: 'record', audioBlob: Blob, duration: number): void
  (e: 'error', message: string): void
}>()

// 录音状态
const isRecording = ref(false)
const showRecordingDialog = ref(false)
const recordingTime = ref(0)
const recordingTimer = ref<number | null>(null)
const mediaRecorder = ref<MediaRecorder | null>(null)
const audioChunks = ref<Blob[]>([])
const audioContext = ref<AudioContext | null>(null)
const analyser = ref<AnalyserNode | null>(null)
const animationId = ref<number | null>(null)
const barHeights = ref<number[]>([20, 30, 40, 30, 20])

// 最大录音时长（秒）
const MAX_RECORDING_TIME = 60

// 开始录音
const startRecording = async () => {
  if (props.disabled || props.loading || isRecording.value) return

  try {
    // 请求麦克风权限
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })

    // 创建MediaRecorder
    const mimeType = MediaRecorder.isTypeSupported('audio/webm')
      ? 'audio/webm'
      : MediaRecorder.isTypeSupported('audio/mp4')
        ? 'audio/mp4'
        : 'audio/ogg'

    mediaRecorder.value = new MediaRecorder(stream, { mimeType })
    audioChunks.value = []

    // 收集音频数据
    mediaRecorder.value.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.value.push(event.data)
      }
    }

    // 录音停止处理
    mediaRecorder.value.onstop = () => {
      const audioBlob = new Blob(audioChunks.value, { type: mimeType })
      const duration = recordingTime.value

      // 清理资源
      stream.getTracks().forEach(track => track.stop())

      // 发送录音结果
      if (duration > 1) {
        emit('record', audioBlob, duration)
      } else {
        ElMessage.warning('录音时间太短')
      }

      resetRecording()
    }

    // 开始录音
    mediaRecorder.value.start(100) // 每100ms收集一次数据
    isRecording.value = true
    showRecordingDialog.value = true
    recordingTime.value = 0

    // 启动计时器
    recordingTimer.value = window.setInterval(() => {
      recordingTime.value++
      if (recordingTime.value >= MAX_RECORDING_TIME) {
        stopRecording()
      }
    }, 1000)

    // 启动音频可视化
    setupAudioVisualization(stream)

  } catch (error) {
    console.error('录音失败:', error)
    emit('error', '无法访问麦克风，请检查权限设置')
    ElMessage.error('无法访问麦克风，请检查权限设置')
  }
}

// 停止录音
const stopRecording = () => {
  if (!isRecording.value || !mediaRecorder.value) return

  // 停止计时器
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  // 停止可视化
  if (animationId.value) {
    cancelAnimationFrame(animationId.value)
    animationId.value = null
  }

  // 停止录音
  if (mediaRecorder.value.state !== 'inactive') {
    mediaRecorder.value.stop()
  }

  showRecordingDialog.value = false
  isRecording.value = false
}

// 取消录音
const cancelRecording = () => {
  if (!isRecording.value || !mediaRecorder.value) return

  // 停止计时器
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  // 停止可视化
  if (animationId.value) {
    cancelAnimationFrame(animationId.value)
    animationId.value = null
  }

  // 停止录音但不发送
  if (mediaRecorder.value.state !== 'inactive') {
    mediaRecorder.value.stop()
    // 清空数据，不触发onstop中的发送逻辑
    audioChunks.value = []
  }

  // 清理资源
  if (mediaRecorder.value.stream) {
    mediaRecorder.value.stream.getTracks().forEach(track => track.stop())
  }

  showRecordingDialog.value = false
  isRecording.value = false
  recordingTime.value = 0

  ElMessage.info('已取消录音')
}

// 处理鼠标离开按钮
const handleMouseLeave = () => {
  if (isRecording.value) {
    stopRecording()
  }
}

// 重置录音状态
const resetRecording = () => {
  isRecording.value = false
  recordingTime.value = 0
  audioChunks.value = []
  mediaRecorder.value = null

  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  if (animationId.value) {
    cancelAnimationFrame(animationId.value)
    animationId.value = null
  }

  if (audioContext.value) {
    audioContext.value.close()
    audioContext.value = null
  }
}

// 设置音频可视化
const setupAudioVisualization = (stream: MediaStream) => {
  audioContext.value = new (window.AudioContext || (window as any).webkitAudioContext)()
  analyser.value = audioContext.value.createAnalyser()
  const source = audioContext.value.createMediaStreamSource(stream)
  source.connect(analyser.value)

  analyser.value.fftSize = 64
  const bufferLength = analyser.value.frequencyBinCount
  const dataArray = new Uint8Array(bufferLength)

  const updateVisualization = () => {
    if (!analyser.value) return

    analyser.value.getByteFrequencyData(dataArray)

    // 更新柱状图高度
    for (let i = 0; i < 5; i++) {
      const index = Math.floor(i * bufferLength / 5)
      const value = dataArray[index] || 0
      barHeights.value[i] = Math.max(10, Math.min(60, value / 2))
    }

    animationId.value = requestAnimationFrame(updateVisualization)
  }

  updateVisualization()
}

// 获取柱状图高度
const getBarHeight = (index: number): number => {
  return barHeights.value[index - 1] || 20
}

// 格式化时间
const formatTime = (seconds: number): string => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 组件卸载时清理资源
onUnmounted(() => {
  resetRecording()
})
</script>

<style scoped>
.voice-input-button {
  display: inline-flex;
}

.voice-btn {
  width: 40px;
  height: 40px;
  transition: all 0.3s ease;
}

.voice-btn.recording {
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(245, 108, 108, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0);
  }
}

.recording-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
}

.recording-animation {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 80px;
  margin-bottom: 16px;
}

.sound-bar {
  width: 8px;
  background: linear-gradient(to top, #409eff, #67c23a);
  border-radius: 4px;
  transition: height 0.1s ease;
  animation: sound-wave 0.5s ease-in-out infinite alternate;
}

@keyframes sound-wave {
  0% {
    transform: scaleY(0.5);
  }
  100% {
    transform: scaleY(1);
  }
}

.recording-time {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 8px;
  font-family: 'Courier New', monospace;
}

.recording-tip {
  font-size: 14px;
  color: #909399;
  margin-bottom: 16px;
}
</style>
