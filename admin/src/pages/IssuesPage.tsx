import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Message,
  Pagination,
  Select,
  Typography,
} from "@arco-design/web-react";
import {
  IconDownload,
  IconFilter,
  IconRefresh,
} from "@arco-design/web-react/icon";
import { useState } from "react";
import { api } from "../api";
import { IssueDetailDrawer } from "../components/IssueDetailDrawer";
import { IssueTable } from "../components/IssueTable";
import { PageHead, StatePanel } from "../components/PageParts";
import { useRemote } from "../hooks/useRemote";
import { errorMessage, statusLabel } from "../lib/presentation";
import type { Issue } from "../types";

export function IssuesPage() {
  const [filters, setFilters] = useState({
    keyword: "",
    status: "",
    factory: "",
    area: "",
    line: "",
    process: "",
    currentHandler: "",
    createdFrom: "",
    createdTo: "",
  });
  const [query, setQuery] = useState({ ...filters, page: 1, pageSize: 20 });
  const [exporting, setExporting] = useState(false);
  const [selectedIssue, setSelectedIssue] = useState<Issue | null>(null);
  const remote = useRemote(() => api.issues(query), [query]);
  const factories = useRemote(() => api.locationOptions("factory"), []);
  const areas = useRemote(
    () =>
      filters.factory
        ? api.locationOptions("area", { factory: filters.factory })
        : Promise.resolve([]),
    [filters.factory],
  );
  const lines = useRemote(
    () =>
      filters.factory && filters.area
        ? api.locationOptions("line", {
            factory: filters.factory,
            area: filters.area,
          })
        : Promise.resolve([]),
    [filters.factory, filters.area],
  );
  const processes = useRemote(
    () =>
      filters.factory && filters.area && filters.line
        ? api.locationOptions("process", {
            factory: filters.factory,
            area: filters.area,
            line: filters.line,
          })
        : Promise.resolve([]),
    [filters.factory, filters.area, filters.line],
  );
  const location = (
    key: "factory" | "area" | "line" | "process",
    value: string,
  ) =>
    setFilters((current) => ({
      ...current,
      [key]: value,
      ...(key === "factory" ? { area: "", line: "", process: "" } : {}),
      ...(key === "area" ? { line: "", process: "" } : {}),
      ...(key === "line" ? { process: "" } : {}),
    }));
  async function exportRows() {
    setExporting(true);
    try {
      await api.exportIssues(filters);
      Message.success("导出文件已生成");
    } catch (error) {
      Message.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }
  return (
    <>
      <PageHead
        title="问题管理"
        detail="查看问题全字段、附件和流程人员，支持位置下拉筛选与创建时间范围查询。"
      >
        <Button
          icon={<IconDownload />}
          loading={exporting}
          onClick={() => void exportRows()}
        >
          导出 Excel
        </Button>
      </PageHead>
      <Card className="filter-card">
        <Form
          className="issue-filter-form"
          layout="inline"
          onSubmit={() =>
            setQuery({ ...filters, page: 1, pageSize: query.pageSize })
          }
        >
          <Input.Search
            allowClear
            value={filters.keyword}
            onChange={(keyword) => setFilters({ ...filters, keyword })}
            placeholder="问题描述、当前处理人"
          />
          <Select
            allowClear
            value={filters.status || undefined}
            onChange={(status) =>
              setFilters({ ...filters, status: status ?? "" })
            }
            placeholder="当前节点"
          >
            {Object.entries(statusLabel).map(([value, label]) => (
              <Select.Option key={value} value={value}>
                {label}
              </Select.Option>
            ))}
          </Select>
          <Select
            allowClear
            value={filters.factory || undefined}
            options={factories.data ?? undefined}
            onChange={(value) => location("factory", value ?? "")}
            placeholder="全部工厂"
          />
          <Select
            allowClear
            value={filters.area || undefined}
            options={areas.data ?? undefined}
            onChange={(value) => location("area", value ?? "")}
            disabled={!filters.factory}
            placeholder="全部区域"
          />
          <Select
            allowClear
            value={filters.line || undefined}
            options={lines.data ?? undefined}
            onChange={(value) => location("line", value ?? "")}
            disabled={!filters.area}
            placeholder="全部拉线"
          />
          <Select
            allowClear
            value={filters.process || undefined}
            options={processes.data ?? undefined}
            onChange={(value) => location("process", value ?? "")}
            disabled={!filters.line}
            placeholder="全部工序"
          />
          <Input
            allowClear
            value={filters.currentHandler}
            onChange={(currentHandler) =>
              setFilters({ ...filters, currentHandler })
            }
            placeholder="当前处理人"
          />
          <DatePicker.RangePicker
            onChange={(_, values) =>
              setFilters({
                ...filters,
                createdFrom: values[0] ? `${values[0]} 00:00:00` : "",
                createdTo: values[1] ? `${values[1]} 23:59:59` : "",
              })
            }
            placeholder={["创建开始", "创建结束"]}
          />
          <Button type="primary" htmlType="submit" icon={<IconFilter />}>
            应用筛选
          </Button>
        </Form>
      </Card>
      <div style={{ marginTop: 20 }}>
        <StatePanel {...remote}>
          {remote.data && (
            <Card className="table-card">
              <div
                className="table-toolbar"
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <div>
                  <Typography.Text bold>问题列表</Typography.Text>
                  <div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      共 {remote.data.total} 条符合条件的记录
                    </Typography.Text>
                  </div>
                </div>
                <Button
                  type="text"
                  icon={<IconRefresh />}
                  aria-label="刷新问题列表"
                  onClick={() => void remote.refresh()}
                />
              </div>
              <IssueTable
                records={remote.data.records}
                onDetail={setSelectedIssue}
              />
              <div className="pagination-wrap">
                <Pagination
                  current={remote.data.page}
                  pageSize={remote.data.pageSize}
                  total={remote.data.total}
                  sizeCanChange
                  sizeOptions={[20, 50, 100]}
                  showTotal
                  onChange={(page, pageSize) =>
                    setQuery({ ...query, page, pageSize })
                  }
                />
              </div>
            </Card>
          )}
        </StatePanel>
      </div>
      <IssueDetailDrawer
        issue={selectedIssue}
        onClose={() => setSelectedIssue(null)}
      />
    </>
  );
}
