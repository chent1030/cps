import { Button, Card, Empty, Result, Spin, Tag, Typography } from '@arco-design/web-react'
import { IconRefresh } from '@arco-design/web-react/icon'
import type { ReactNode } from 'react'

export function PageHead({ title, detail, children }: { title: string; detail: string; children?: ReactNode }) { return <div className="page-head" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}><div><Typography.Title heading={4}>{title}</Typography.Title><Typography.Paragraph className="page-description">{detail}</Typography.Paragraph></div>{children}</div> }
export function StatePanel({ loading, error, onRetry = () => undefined, children }: { loading: boolean; error: string | null; onRetry?: () => void; children: ReactNode }) { if (loading) return <Card className="table-card"><div style={{ minHeight: 300, display: 'grid', placeItems: 'center' }}><Spin tip="正在加载数据" /></div></Card>; if (error) return <Result status="error" title="数据加载失败" subTitle={error} extra={<Button type="primary" icon={<IconRefresh />} onClick={onRetry}>重新加载</Button>} />; return <>{children}</> }
export function DataCard({ children }: { children: ReactNode }) { return <Card className="table-card">{children}</Card> }
export function EmptyTable({ title = '暂无数据' }: { title?: string }) { return <Empty description={title} /> }
export function Enabled({ value }: { value: boolean }) { return <Tag color={value ? 'green' : 'gray'}>{value ? '已启用' : '已停用'}</Tag> }
export function Toggle({ value, onClick }: { value: boolean; onClick: () => void }) { return <Button type="text" size="small" status={value ? 'warning' : 'success'} onClick={onClick}>{value ? '停用' : '启用'}</Button> }
