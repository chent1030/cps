import { Alert, Button, Card, Descriptions, Form, Grid, Input, Message, Space, Tag, Typography } from '@arco-design/web-react'
import { IconLock, IconRefresh, IconRobot, IconSettings } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api, getAdminOperator } from '../api'
import { PageHead, StatePanel } from '../components/PageParts'
import { StructuredValue } from '../components/StructuredValue'
import { useRemote } from '../hooks/useRemote'
import { errorMessage } from '../lib/presentation'

const { Row, Col } = Grid
export function SettingsPage() {
  const runtime = useRemote(() => api.agentRuntime(), [])
  const operator = getAdminOperator()
  return (
    <>
      <PageHead title="系统设置" detail="管理端依赖服务状态 + 账号安全">
        <Button icon={<IconRefresh />} loading={runtime.loading} onClick={() => void runtime.refresh()}>刷新状态</Button>
      </PageHead>
      <StatePanel {...runtime}>
        {runtime.data && (
          <Row gutter={[16, 16]}>
            <Col xs={24} lg={14}>
              <Card className="settings-card" title={<Space><IconRobot /><span>basic-project Agent 运行时</span></Space>}>
                <Descriptions
                  column={1}
                  data={[
                    { label: '接入状态', value: <Tag color={runtime.data.state === 'ONLINE' ? 'green' : runtime.data.state === 'DISABLED' ? 'orange' : 'red'}>{runtime.data.state}</Tag> },
                    { label: '租户标识', value: runtime.data.tenantId },
                    { label: '运行时地址', value: runtime.data.baseUrl },
                    { label: '检查结果', value: <StructuredValue value={runtime.data.message} textRows={6} /> },
                    ...(runtime.data.metrics ? [{ label: '运行指标', value: <StructuredValue value={runtime.data.metrics} /> }] : []),
                  ]}
                />
              </Card>
            </Col>
            <Col xs={24} lg={10}>
              <Card className="settings-card" title={<Space><IconSettings /><span>部署说明</span></Space>}>
                <Typography.Paragraph style={{ margin: 0, lineHeight: 1.8, color: '#5f6b68' }}>
                  Agent 开关由 <Typography.Text code>CPS_AGENT_FRAMEWORK_ENABLED</Typography.Text> 控制，管理端只读取状态、不保存凭证。
                </Typography.Paragraph>
              </Card>
            </Col>
            <Col xs={24}>
              <Card className="settings-card" title={<Space><IconLock /><span>账号安全 — 修改密码</span></Space>}>
                {operator ? <ChangePasswordForm empNo={operator.empNo} /> : <Typography.Text type="secondary">未登录</Typography.Text>}
              </Card>
            </Col>
          </Row>
        )}
      </StatePanel>
    </>
  )
}

function ChangePasswordForm({ empNo }: { empNo: string }) {
  const [oldPwd, setOldPwd] = useState('')
  const [newPwd, setNewPwd] = useState('')
  const [newPwd2, setNewPwd2] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    if (!oldPwd) return Message.warning('请输入当前密码')
    if (newPwd.length < 6) return Message.warning('新密码至少 6 位')
    if (newPwd !== newPwd2) return Message.warning('两次输入的新密码不一致')
    if (newPwd === oldPwd) return Message.warning('新密码不能与旧密码相同')
    setSaving(true)
    try {
      await api.changeMyPassword({ empNo, currentEmpNo: empNo, oldPassword: oldPwd, newPassword: newPwd })
      Message.success('密码已修改，下次登录请使用新密码')
      setOldPwd('')
      setNewPwd('')
      setNewPwd2('')
    } catch (error) {
      Message.error(errorMessage(error))
    } finally { setSaving(false) }
  }

  return (
    <>
      <Alert
        type="info"
        showIcon
        content="建议密码至少 6 位，包含字母与数字；修改后不会自动登出，登录态保留到您主动退出。"
        style={{ marginBottom: 16 }}
      />
      <Form layout="vertical" style={{ maxWidth: 420 }}>
        <Form.Item label="当前密码" required>
          <Input.Password value={oldPwd} onChange={setOldPwd} placeholder="请输入当前密码" />
        </Form.Item>
        <Form.Item label="新密码" required extra="≥ 6 位">
          <Input.Password value={newPwd} onChange={setNewPwd} placeholder="请输入新密码" />
        </Form.Item>
        <Form.Item label="确认新密码" required>
          <Input.Password value={newPwd2} onChange={setNewPwd2} placeholder="再次输入新密码" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" loading={saving} onClick={() => void submit()}>修改密码</Button>
        </Form.Item>
      </Form>
    </>
  )
}
