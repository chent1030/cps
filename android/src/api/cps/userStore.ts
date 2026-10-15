import { computed, ref, type ComputedRef, type Ref } from 'vue'

/**
 * 波次14 B5：移动端角色门控（v-if 路由）使用的前端 mock userStore。
 *
 * 仅供前端 v-if 切换；**不要真接后端鉴权**（详见 PRD §31）。
 * 真正鉴权仍由 cps/backend 的 CpsPermissionInterceptor 处理（admin/management/inspector）；
 * mobile 这里仅根据登录返回的 userInfo.role 控制菜单可见性。
 */

export interface CpsUserInfo {
  empNo: string
  empName: string
  role: 'admin' | 'inspector'
}

/** 默认当前用户：mock 一个 inspector，方便没有登录态时 UI 不暴露 admin-only 入口。 */
const defaultUser: CpsUserInfo = {
  empNo: 'MOCK-INSPECTOR',
  empName: '默认巡检员',
  role: 'inspector',
}

const current: Ref<CpsUserInfo> = ref({ ...defaultUser })

export function getCurrentUser(): CpsUserInfo {
  return current.value
}

export function setCurrentUser(user: CpsUserInfo): void {
  current.value = user
}

export function setCurrentRole(role: CpsUserInfo['role']): void {
  current.value = { ...current.value, role }
}

/** 当前用户对象（响应式）。 */
export const currentUser: ComputedRef<CpsUserInfo> = computed(() => current.value)

/** 当前角色（响应式）。 */
export const currentRole: ComputedRef<CpsUserInfo['role']> = computed(() => current.value.role)

/** role 工具：判断当前角色是否与期望匹配。 */
export function hasRole(role: CpsUserInfo['role']): boolean {
  return current.value.role === role
}