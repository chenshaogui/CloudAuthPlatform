# Cloud Auth Platform 单体应用

`backend-monolith` 是一个**完全独立的单体后端项目**，由 `backend` 的源码完整移植而来。原 `auth`、`system`、`resource` 三个服务已合并到同一个 Spring Boot 应用中，以单个 JVM 运行。

本项目与 `backend/` 微服务版本**没有代码或构建依赖**，二者独立演进；数据库初始化脚本共用 `../backend/script/sql/security-cloud.sql`，本项目不再复制或维护额外的 SQL/Nacos 配置。

## 主要变化

- 单一 Maven 模块：根 `pom.xml` 即为唯一构建文件，源码统一放在 `src/main/java`。
- 无 Nacos：配置从 `src/main/resources/config/*.yml` 本地文件读取，不做配置中心和注册中心连接。
- 无 Dubbo：原 `@DubboReference` / `@DubboService` 已改为同一 Spring 上下文内的本地 Bean 注入。
- 无网关模块：原网关过滤器不再使用；前端 API 由部署侧 nginx 反向代理到本应用。
- 合并路径：保留 `/auth/**`、`/system/**`、`/resource/**` 前端 URL，通过 MVC 路径前缀消除原 `/tenant/list` 映射冲突。
- 低内存运行：Undertow worker 线程调整为 48，实测 `-Xms128m -Xmx256m` 可正常启动。

## 环境要求

- JDK 17
- Maven 3.9+
- MySQL（初始化脚本：`../backend/script/sql/security-cloud.sql`）
- Redis

## 配置

配置按 Spring Profile 拆分，位于 `src/main/resources/`：

```text
application.yml         公共配置（端口、Undertow、Sa-Token、MyBatis、加解密、租户、SSE 等）
application-local.yml   本地环境：写死本机 MySQL/Redis 地址，默认激活
application-dev.yml     开发环境：通过环境变量提供连接信息，带本地默认值
application-prod.yml    生产环境：密钥类参数必须通过环境变量提供
```

默认激活 `local`，可通过启动参数或环境变量切换：

```bash
java -jar target/cloud-auth-platform-monolith-2.5.1.jar --spring.profiles.active=dev
# 或
SPRING_PROFILES_ACTIVE=prod java -jar target/cloud-auth-platform-monolith-2.5.1.jar
```

各环境支持的主要变量：

| 变量 | 说明 | 适用 |
| --- | --- | --- |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 地址与密码 | dev / prod |
| `MYSQL_HOST` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 地址（含端口和库名）、账号、密码 | dev / prod |
| `FRONTEND_URL` | 前端回调地址 | dev / prod |
| `MAIL_*` / `SMS_*` | 邮件与短信厂商参数 | dev / prod |

不再使用 Nacos，也没有 `config/` 子目录。本地开发只需修改 `application-local.yml` 中的连接地址。

## 构建与运行

```bash
# 构建
mvn -DskipTests package

# 运行
java -Xms128m -Xmx256m -jar target/cloud-auth-platform-monolith-2.5.1.jar
```

启动成功日志示例：

```text
Started CloudAuthMonolithApplication in 25 seconds
```

## 前端对接

前端打包后仍使用原有相对 API 前缀 `/cloudAuthPlatform/prod-api`。由于本版本没有 Spring Cloud Gateway，需要由 nginx 将该前缀转发到单体应用并去掉前缀：

```nginx
location /cloudAuthPlatform/prod-api/ {
    proxy_pass http://127.0.0.1:9215/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_buffering off;
}
```

这样 `/cloudAuthPlatform/prod-api/auth/login` 会转发到本应用的 `/auth/login`，前端无需修改业务代码。

## 本地登录账号

导入数据库脚本并启动应用后，可使用以下账号登录：

- 用户名：`admin`
- 密码：`admin@123`

> 该账号仅用于本地开发和演示，生产环境请务必修改默认密码。

## 说明

- Java 包名仍保留 `io.gitee.chenshaogui.cloudauth.auth`、`system`、`resource`，这是 MVC 路径前缀与 MyBatis Mapper 扫描所依赖的，不代表微服务拆分。
- 项目不依赖 Nacos、Dubbo、Spring Cloud Gateway、Seata 或 Spring Cloud Bus。
- 代码基于 RuoYi 生态，保留上游声明与 LICENSE。
