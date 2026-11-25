# Cloud Auth Platform 后端（微服务版）

本目录是 `cloud-auth-platform` 的**微服务后端工程**，基于 RuoYi-Cloud-Plus 生态构建。采用 Spring Boot 3 / Spring Cloud Alibaba / Dubbo 3 的技术栈，服务注册、配置中心使用 Nacos。

仓库根目录下另有一个独立单体工程 `backend-monolith/`，由本目录源码完整移植而来，二者互不依赖；本 README 只描述 `backend/` 微服务版本。

## 模块结构

```text
backend/
  cloud-auth-platform-auth/                         认证服务      端口 9210
  cloud-auth-platform-gateway/                      网关服务      端口 9212
  cloud-auth-platform-modules/
    cloud-auth-platform-system/                     系统服务      端口 9211
    cloud-auth-platform-resource/                   资源服务      端口 9213
  cloud-auth-platform-api/                          Feign/Dubbo API 定义
  cloud-auth-platform-common/                       公共基础模块
  script/
    sql/security-cloud.sql                         数据库初始化脚本
    config/nacos/DEFAULT_GROUP/*.yml                Nacos 配置导入文件
```

服务间通过 Dubbo 调用，网关负责路由 `/auth/**`、`/system/**`、`/resource/**`、`/monitor/**` 到对应服务。

## 环境要求

- JDK 17
- Maven 3.9+
- MySQL
- Redis
- Nacos（注册中心 + 配置中心）

## 初始化

1. 启动 MySQL 和 Redis，导入数据库脚本：

   ```bash
   mysql -u root -p < script/sql/security-cloud.sql
   ```

2. 启动 Nacos，把 `script/config/nacos/DEFAULT_GROUP/*.yml` 全部导入 `DEFAULT_GROUP`（public 命名空间）。按实际环境修改其中的 MySQL、Redis、Nacos 地址和账号密码。

3. 修改各模块 `src/main/resources/application-local.yml` 中的 Nacos 地址、账号密码，使其指向第 2 步的 Nacos。

## 构建与运行

在 `backend/` 目录执行：

```bash
mvn -DskipTests package
```

按以下顺序启动服务：

1. `cloud-auth-platform-auth`（认证服务）
2. `cloud-auth-platform-modules/cloud-auth-platform-system`（系统服务）
3. `cloud-auth-platform-modules/cloud-auth-platform-resource`（资源服务）
4. `cloud-auth-platform-gateway`（网关，最后启动）

每个模块也可使用自带的 `src/main/docker/Dockerfile` 单独构建镜像。

## 前端对接

前端开发环境的 API 前缀为 `/cloudAuthPlatform/prod-api`，由 Vite 代理到网关端口 `9212`：

```env
VITE_APP_BASE_API = '/cloudAuthPlatform/prod-api'
VITE_APP_PROXY_TARGET = 'http://127.0.0.1:9212'
```

网关会剥离 `/auth`、`/system`、`/resource`、`/monitor` 等前缀后转发到对应服务。

## 本地登录账号

导入数据库脚本并启动所有服务后，可使用以下账号登录：

- 用户名：`admin`
- 密码：`admin@123`

> 该账号仅用于本地开发和演示，生产环境请务必修改默认密码。

## 说明

- 代码基于 RuoYi-Cloud-Plus 生态，保留上游声明与 LICENSE。
- 各模块配置了独立的 `application-local.yml`、`application-dev.yml`、`application-pub.yml`，默认使用 `local`。
- 网关内置的 clientid 一致性校验、`Sa-Same-Token` 内网隔离、URL 黑名单和请求日志由 `gateway-local.yml` 及对应过滤器提供。
