import { Alert, Button, Card, Form, Input, Message, Modal, Typography } from '@arco-design/web-react'
import { IconLock, IconUser } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api, getAdminOperator, setAdminOperator } from '../api'
import { errorMessage } from '../lib/presentation'

export function LoginPage({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [empNo, setEmpNo] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [mustChange, setMustChange] = useState<{ empNo: string } | null>(null)
  const [newPwd, setNewPwd] = useState('')
  const [newPwd2, setNewPwd2] = useState('')
  const [saving, setSaving] = useState(false)

  async function login() {
    if (!empNo.trim() || !password) return Message.warning('请输入工号和密码')
    setLoading(true)
    try {
      const operator = await api.adminLogin({ empNo: empNo.trim(), password })
      setAdminOperator(operator)
      if (operator.mustChangePassword) {
        setMustChange({ empNo: operator.empNo })
        Message.warning('首次登录或密码已重置，请先修改密码')
      } else {
        Message.success('登录成功')
        onLoggedIn()
      }
    } catch (error) {
      Message.error(errorMessage(error))
    } finally { setLoading(false) }
  }

  async function submitChangePassword() {
    if (!mustChange) return
    if (newPwd.length < 6) return Message.warning('新密码至少 6 位')
    if (newPwd !== newPwd2) return Message.warning('两次输入的新密码不一致')
    if (newPwd === password) return Message.warning('新密码不能与当前密码相同')
    setSaving(true)
    try {
      await api.changeMyPassword({
        empNo: mustChange.empNo,
        currentEmpNo: mustChange.empNo,
        oldPassword: password,
        newPassword: newPwd,
      })
      const op = getAdminOperator()
      if (op) setAdminOperator({ ...op, mustChangePassword: false })
      Message.success('密码已修改，欢迎使用')
      setMustChange(null)
      setNewPwd('')
      setNewPwd2('')
      onLoggedIn()
    } catch (error) {
      Message.error(errorMessage(error))
    } finally { setSaving(false) }
  }

  return (
    <main className="login-page">
      <Card className="login-card">
        <div className="login-brand">
          <div className="brand-mark">C</div>
          <div>
            <Typography.Title heading={4}>CPS Inspect</Typography.Title>
            <Typography.Text type="secondary">巡检管理平台</Typography.Text>
          </div>
        </div>
        <Typography.Title heading={5}>管理端登录</Typography.Title>
        <Typography.Text type="secondary">使用工号与密码登录。首次登录 / 密码被重置后须立即修改密码。</Typography.Text>
        <Form layout="vertical" onSubmit={() => void login()} style={{ marginTop: 26 }}>
          <Form.Item label="员工工号" required>
            <Input prefix={<IconUser />} value={empNo} onChange={setEmpNo} placeholder="请输入员工工号" />
          </Form.Item>
          <Form.Item label="登录密码" required>
            <Input.Password
              prefix={<IconLock />}
              value={password}
              onChange={setPassword}
              placeholder="请输入登录密码"
              onPressEnter={() => void login()}
            />
          </Form.Item>
          <Button type="primary" long htmlType="submit" loading={loading}>登录</Button>
        </Form>
      </Card>

      <Modal
        title="首次登录 — 请修改密码"
        visible={!!mustChange}
        onCancel={() => undefined}
        onOk={() => void submitChangePassword()}
        okText="修改密码并登录"
        cancelText="取消"
        confirmLoading={saving}
        style={{ width: 480 }}
        closable={false}
        maskClosable={false}
      >
        <Alert
          type="warning"
          showIcon
          content="系统检测到您是首次登录或密码已被管理员重置，必须先修改密码才能继续。"
          style={{ marginBottom: 16 }}
        />
        <Form layout="vertical">
          <Form.Item label="新密码" required extra="≥ 6 位">
            <Input.Password value={newPwd} onChange={setNewPwd} placeholder="请输入新密码" />
          </Form.Item>
          <Form.Item label="确认新密码" required>
            <Input.Password value={newPwd2} onChange={setNewPwd2} placeholder="再次输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </main>
  )
}
