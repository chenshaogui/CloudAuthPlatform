# Cloud Auth Platform 前端控制台

本目录是 `cloud-auth-platform` 的 Web 管理后台，基于 Vue 3 + TypeScript + Vite + Element Plus 构建，源自 RuoYi-Vue-Plus，保留上游声明与 LICENSE。

## 环境要求

- Node.js 20+
- npm 10+

## 环境变量

配置文件位于项目根目录：

| 文件 | 用途 |
| --- | --- |
| `.env.example` | 变量模板，说明各项含义 |
| `.env.development` | 开发模式默认配置（已提交） |
| `.env.production` | 生产构建配置（已提交） |

关键变量：

- `VITE_APP_BASE_API`：浏览器侧 API 前缀，本项目统一为 `/securitySystem/prod-api`。
- `VITE_APP_PROXY_TARGET`：开发服务器代理目标。
  - 微服务模式：`http://127.0.0.1:9212`（网关）
  - 单体模式：`http://127.0.0.1:9215`（`backend-monolith`）
- `VITE_APP_CONTEXT_PATH`：应用访问路径 `/securitySystem/`。
- `VITE_APP_PORT`：开发服务器端口，默认 `80`。
- `VITE_APP_CLIENT_ID`：客户端 ID。
- `VITE_APP_ENCRYPT`、`VITE_APP_RSA_PUBLIC_KEY`、`VITE_APP_RSA_PRIVATE_KEY`：接口加解密配置，必须与后端一致。
- `VITE_APP_SSE` / `VITE_APP_WEBSOCKET`：消息推送开关，默认使用 SSE。

> Vite 会把 `VITE_*` 变量打包进浏览器资源，请勿在公开构建中配置生产环境私钥。

## 本地开发

```bash
npm install
npm run dev
```

默认访问 `http://localhost/securitySystem/`。

切换到单体后端时，确认 `.env.development` 中：

```env
VITE_APP_PROXY_TARGET = 'http://127.0.0.1:9215'
```

切换到微服务后端时改为：

```env
VITE_APP_PROXY_TARGET = 'http://127.0.0.1:9212'
```

前端业务代码无需改动，Vite 会把 `/securitySystem/prod-api` 剥离后转发到对应后端。

## 构建

```bash
# 生产构建
npm run build:prod

# 开发模式构建
npm run build:dev
```

产物目录为 `securitySystem/`，部署时将其作为静态资源根目录访问 `/securitySystem/`。

## 部署对接

生产构建使用相对 API 前缀 `/securitySystem/prod-api`，需由 nginx 反向代理到后端：

- 单体应用：转发到 `http://127.0.0.1:9215/`，并去掉 `/securitySystem/prod-api` 前缀。
- 微服务模式：转发到网关 `http://127.0.0.1:9212/`。

示例：

```nginx
location /securitySystem/prod-api/ {
    proxy_pass http://127.0.0.1:9215/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_buffering off;
}
```

## 本地登录账号

后端导入 `backend/script/sql/security-cloud.sql` 并启动后：

- 用户名：`admin`
- 密码：`admin@123`

> 该账号仅用于本地开发和演示，生产环境请务必修改默认密码。
