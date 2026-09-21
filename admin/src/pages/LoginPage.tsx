import { Button, Card, Form, Input, Message, Typography } from '@arco-design/web-react'
import { IconLock, IconUser } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api, setAdminOperator } from '../api'
import { errorMessage } from '../lib/presentation'

export function LoginPage({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [empNo, setEmpNo] = useState('')
  const [empName, setEmpName] = useState('')
  const [loading, setLoading] = useState(false)
  async function login() {
    if (!empNo.trim() || !empName.trim()) return Message.warning('请输入工号和姓名')
    setLoading(true)
    try { setAdminOperator(await api.adminLogin({ empNo: empNo.trim(), empName: empName.trim() })); Message.success('登录成功'); onLoggedIn() }
    catch (error) { Message.error(errorMessage(error)) }
    finally { setLoading(false) }
  }
  return <main className="login-page"><Card className="login-card"><div className="login-brand"><div className="brand-mark">C</div><div><Typography.Title heading={4}>CPS Inspect</Typography.Title><Typography.Text type="secondary">巡检管理平台</Typography.Text></div></div><Typography.Title heading={5}>管理端登录</Typography.Title><Typography.Text type="secondary">使用已配置的员工工号和姓名登录</Typography.Text><Form layout="vertical" onSubmit={() => void login()} style={{ marginTop: 26 }}><Form.Item label="员工工号" required><Input prefix={<IconUser />} value={empNo} onChange={setEmpNo} placeholder="请输入员工工号" /></Form.Item><Form.Item label="员工姓名" required><Input prefix={<IconLock />} value={empName} onChange={setEmpName} placeholder="请输入员工姓名" /></Form.Item><Button type="primary" long htmlType="submit" loading={loading}>登录</Button></Form></Card></main>
}
