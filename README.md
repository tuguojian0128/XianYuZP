# XianYuZP

**简体中文** | [English](README.en.md)

![Version](https://img.shields.io/badge/version-0.0.1-2f6f5e)
![Java](https://img.shields.io/badge/Java-21-2f6f5e)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.5-2f6f5e)
![Vue](https://img.shields.io/badge/Vue-3-2f6f5e)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-2f6f5e)

> 面向闲鱼虚拟商品的订单、自动发货、消息和运营管理平台。

XianYuZP 支持固定内容和卡密两种交付方式，提供订单处理、自动发货、消息回复、评价跟进、商品管理、库存管理和运行诊断等能力。账号、商品、订单、库存和配置按租户隔离，任务可在服务重启后恢复。

当前版本：**0.0.1**

## 功能

- 闲鱼账号连接、Cookie 和登录状态维护
- 商品同步、商品发布和商品资料管理
- 固定内容、卡密和外部供货接口自动发货
- 发货凭证与买家私聊双通道交付
- 关键词回复、AI 知识库和人工接管
- 确认收货后的引导消息与主动评价
- 订单、库存、买家、消息和操作日志查询
- 邮件、Webhook 等通知渠道
- 运行健康检查、失败重试和人工复核

## 快速启动

复制环境变量模板并启动 Docker Compose：

```bash
cp .env.example .env
docker compose up -d --build
```

```bash
docker compose ps
docker compose logs -f app
```

启动后访问 <http://localhost:12400>。默认应用镜像为 `xianyuzp:0.0.1`，可通过 `APP_IMAGE` 覆盖。

## 配置

运行时配置通过环境变量或 `.env` 提供。`.env` 仅保存在部署主机，不要提交到仓库；可用配置见 [.env.example](.env.example) 和 [compose.yaml](compose.yaml)。

邮箱通知支持 QQ、网易、Google 等 SMTP 服务商，用户填写发件邮箱、授权码和接收邮箱即可；授权码不要写入源码或提交记录。

## 开发构建

后端需要 Java 21，前端需要 Node.js 20.19+：

```bash
./mvnw test
cd vue-code
npm ci
npm run build
```

Dockerfile 会在容器内完成 Java 和前端构建：

```bash
docker compose build app
```

## 目录

- `src/main/java`：Spring Boot 后端
- `src/main/resources`：数据库迁移和前端构建产物
- `vue-code/src`：Vue 3 管理界面
- `deploy`：Nginx 和服务部署示例
- `docs`：项目设计和实现记录

## 数据与安全

MySQL 数据保存在 Docker volume 中。删除应用容器不会删除数据库卷；执行清理前请先备份。生产环境请使用 HTTPS、强密码、防火墙和定期备份，不要直接暴露 MySQL。

闲鱼接口、Cookie 和风控策略可能变化，请遵守相关法律法规及平台规则，并及时处理后台显示的异常任务。

## 版本与许可证

版本号统一为 `0.0.1`，对应 Git 标签 `v0.0.1`。

本项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE)，使用限制和免责声明见 [DISCLAIMER.md](DISCLAIMER.md)。
