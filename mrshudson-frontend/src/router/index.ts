import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../views/ChatView.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/ChatRoom.vue')
        },
        {
          path: 'chat',
          name: 'chat',
          component: () => import('../views/ChatRoom.vue')
        },
        {
          path: 'calendar',
          name: 'calendar',
          component: () => import('../views/CalendarView.vue')
        },
        {
          path: 'todo',
          name: 'todo',
          component: () => import('../views/TodoView.vue')
        },
        {
          path: 'weather',
          name: 'weather',
          component: () => import('../views/WeatherView.vue')
        }
      ]
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { guest: true }
    }
  ]
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  // 检查登录状态
  if (!userStore.isLoggedIn) {
    await userStore.fetchCurrentUser()
  }

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next('/login')
  } else if (to.meta.guest && userStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router
