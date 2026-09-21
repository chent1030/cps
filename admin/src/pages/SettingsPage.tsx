import { Button, Card, Descriptions, Grid, Space, Tag, Typography } from '@arco-design/web-react'
import { IconRefresh, IconRobot, IconSettings } from '@arco-design/web-react/icon'
import { api } from '../api'
import { PageHead, StatePanel } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'

const { Row, Col } = Grid
export function SettingsPage() { const runtime = useRemote(() => api.agentRuntime(), []); return <><PageHead title="系统设置" detail="查看当前管理端依赖服务状态。部署参数由环境变量或配置中心统一管理，避免在浏览器暴露基础设施凭证。"><Button icon={<IconRefresh />} loading={runtime.loading} onClick={() => void runtime.refresh()}>刷新状态</Button></PageHead><StatePanel {...runtime}>{runtime.data && <Row gutter={[16, 16]}><Col xs={24} lg={14}><Card className="settings-card" title={<Space><IconRobot /><span>basic-project Agent 运行时</span></Space>}><Descriptions column={1} data={[{ label: '接入状态', value: <Tag color={runtime.data.state === 'ONLINE' ? 'green' : runtime.data.state === 'DISABLED' ? 'orange' : 'red'}>{runtime.data.state}</Tag> }, { label: '租户标识', value: runtime.data.tenantId }, { label: '运行时地址', value: runtime.data.baseUrl }, { label: '检查结果', value: runtime.data.message }]} /></Card></Col><Col xs={24} lg={10}><Card className="settings-card" title={<Space><IconSettings /><span>部署说明</span></Space>}><Typography.Paragraph style={{ margin: 0, lineHeight: 1.8, color: '#5f6b68' }}>Agent 开关由 <Typography.Text code>CPS_AGENT_FRAMEWORK_ENABLED</Typography.Text> 控制。生产环境应通过环境变量或配置中心管理服务地址、租户和网络信任关系；管理端只读取状态，不保存密码或 API 密钥。</Typography.Paragraph></Card></Col></Row>}</StatePanel></> }
