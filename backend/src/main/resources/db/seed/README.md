# CPS 测试数据

该目录不是 Flyway 自动迁移目录，不会在每次启动时执行。需要手工导入：

```bash
mysql -h127.0.0.1 -P3306 -ucps -p cps \
  < src/main/resources/db/seed/V999999__cps_demo_data.sql
```

页面测试请求头使用：

```text
X-Emp-No: DEMO_EMP
```

mobile 开发环境通过 `.env.development` 自动发送该请求头。默认有一条待上传整改凭证的问题单分配给 `DEMO_EMP`，打开“待我处理”即可看到。

演示数据编号均以 `DEMO-` 开头，不会覆盖真实问题单；脚本可重复执行。
