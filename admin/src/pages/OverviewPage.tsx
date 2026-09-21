import { Button, Card, Grid, Statistic, Typography } from '@arco-design/web-react'
import { IconApps, IconClockCircle, IconExclamationCircle, IconFile, IconRefresh, IconSafe } from '@arco-design/web-react/icon'
import { api } from '../api'
import { IssueTable } from '../components/IssueTable'
import { PageHead, StatePanel } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'

const { Row, Col } = Grid
const metrics = [
  ['处理中问题', 'openIssueCount', IconFile, ''], ['待反馈', 'pendingFeedbackCount', IconClockCircle, 'pending'], ['待整改', 'pendingRectifyCount', IconExclamationCircle, 'risk'], ['待审核', 'pendingReviewCount', IconApps, 'info'], ['超期处理', 'overdueCount', IconExclamationCircle, 'risk'], ['本月已闭环', 'closedThisMonthCount', IconSafe, ''],
] as const
export function OverviewPage() { const remote = useRemote(() => api.overview(), []); const data = remote.data; return <><PageHead title="巡检概览" detail="实时汇总巡检闭环进度与需要优先处理的风险事项。"><Button icon={<IconRefresh />} onClick={() => void remote.refresh()}>刷新数据</Button></PageHead><StatePanel {...remote}>{data && <><Row gutter={[14, 14]}>{metrics.map(([label, key, Icon, tone]) => <Col key={key} xs={12} sm={8} lg={4}><Card className={`metric-card ${tone}`}><div className="metric-icon"><Icon /></div><Statistic title={label} value={data[key]} groupSeparator /></Card></Col>)}</Row><Card className="table-card" style={{ marginTop: 22 }}><div className="table-toolbar"><Typography.Text bold>最新问题</Typography.Text><div><Typography.Text type="secondary" style={{ fontSize: 12 }}>按提交时间倒序显示</Typography.Text></div></div><IssueTable records={data.recentIssues} /></Card></>}</StatePanel></> }
