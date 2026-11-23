-- ============================================================
-- 系统融合：扩展表 DDL（sys_user_ext / sys_dept_ext / sys_menu_ext）
-- 适用：cloud-auth-platform 库
-- 说明：执行前请先备份完整数据库。
--
-- 【设计原则】
--   sys_user / sys_dept / sys_menu 主表零侵入：ln_pro 独有字段全部下沉到对应扩展表
--   sys_tenant 主表新增 2 个字段（业务属性，非来源溯源）
--
-- 【版本要求】MySQL 5.7 / 8.0 / MariaDB 通用
-- 【非幂等】已存在的字段/表会报 Duplicate 错误，执行前请按需 DROP 已存在项
-- 【执行顺序】
--   1) ALTER TABLE（sys_tenant 主表新增字段）
--   2) CREATE TABLE（三个扩展表）
--   3) 数据迁移完成后：OPTIMIZE TABLE sys_tenant;
-- ============================================================

USE `security-cloud`;

-- ============================================================
-- 第 1 部分：主表 ALTER（仅 sys_tenant）
-- ============================================================

ALTER TABLE `sys_tenant`
    ADD COLUMN `system_id`   varchar(60)  DEFAULT NULL COMMENT '子系统 ID（ln_pro SYSTEMID）',
    ADD COLUMN `system_name` varchar(100) DEFAULT NULL COMMENT '子系统名称（ln_pro SYSTEMNAME）';

-- ============================================================
-- 第 2 部分：创建扩展表（承载 sys_user / sys_dept / sys_menu 的 ln_pro 字段）
-- ============================================================

-- sys_user 扩展表：与 sys_user 1:1，存储身份证、企业代码、CA 证书、桌面配置等
CREATE TABLE `sys_user_ext` (
    `user_id`                bigint(20)       NOT NULL           COMMENT '用户ID（关联 sys_user.user_id）',
    -- 用户身份
    `identify`               varchar(20)      DEFAULT NULL       COMMENT '身份证号',
    `identify_card`          varchar(60)      DEFAULT NULL       COMMENT '证件号',
    `real_user_name`         varchar(255)     DEFAULT NULL       COMMENT '真实姓名',
    -- 企业 / 资质
    `usci`                   varchar(100)     DEFAULT NULL       COMMENT '统一社会信用代码',
    `enterprise_code`        varchar(50)      DEFAULT NULL       COMMENT '企业代码',
    `transport_license_code` varchar(50)      DEFAULT NULL       COMMENT '道路运输证代码',
    `enterprise_name`        varchar(255)     DEFAULT NULL       COMMENT '企业名称',
    `enterprise_identity`    varchar(255)     DEFAULT NULL       COMMENT '企业身份',
    `qc`                     varchar(255)     DEFAULT NULL       COMMENT '从业资格证',
    `city_district`          varchar(255)     DEFAULT NULL       COMMENT '所在市区',
    -- 账号配置
    `pwd_reset_flag`         char(1)          DEFAULT 'N'         COMMENT '是否强制重置密码（Y=是，N=否）',
    `shared_user`            varchar(30)      DEFAULT NULL       COMMENT '共享用户标识',
    `user_type_ln`           int(11)          DEFAULT NULL       COMMENT '老系统用户类型（USERTYPE）',
    `level_id`               bigint(20)       DEFAULT NULL       COMMENT '用户级别',
    `need_send_daily_paper`  varchar(6)       DEFAULT NULL       COMMENT '是否发送日报',
    `ca_id`                  varchar(50)      DEFAULT NULL       COMMENT 'CA 证书 ID',
    -- 联系方式（源界面：固定电话/联系地址）
    `tel`                    varchar(16)      DEFAULT NULL       COMMENT '固定电话（sys_sec_user.TEL）',
    `address`                text                                DEFAULT NULL COMMENT '联系地址（sys_sec_user.ADDRESS）',
    -- 桌面 / 背景
    `desktop_config`         mediumtext                          DEFAULT NULL COMMENT '桌面配置 XML（DESKTOPCONFIG）',
    `background`             varchar(200)     DEFAULT NULL       COMMENT '背景图片',
    `user_source_type`       varchar(100)     DEFAULT NULL       COMMENT '用户来源类型',
    PRIMARY KEY (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户扩展信息（来自 ln_pro sys_sec_user）';

-- sys_dept 扩展表：与 sys_dept 1:1，存储电话、邮编、地址、URL、扩展描述等
CREATE TABLE `sys_dept_ext` (
    `dept_id`              bigint(20)       NOT NULL           COMMENT '部门ID（关联 sys_dept.dept_id）',
    -- 联系方式
    `dept_manager`         varchar(100)     DEFAULT NULL       COMMENT '部门负责人姓名',
    `dept_manager_tel`     varchar(20)      DEFAULT NULL       COMMENT '部门负责人电话',
    `fax`                  varchar(100)     DEFAULT NULL       COMMENT '传真',
    `postcode`             varchar(100)     DEFAULT NULL       COMMENT '邮编',
    `address`              text                                DEFAULT NULL COMMENT '地址',
    `on_duty_tel`          text                                DEFAULT NULL COMMENT '值班电话',
    `url`                  text                                DEFAULT NULL COMMENT '网址',
    -- 名称 / 描述
    `short_name`           varchar(200)     DEFAULT NULL       COMMENT '简称',
    `print_name`           text                                DEFAULT NULL COMMENT '打印名',
    `description`          text                                DEFAULT NULL COMMENT '描述',
    -- 区域 / 类型
    `dept_type`            varchar(32)      DEFAULT NULL       COMMENT '部门类型',
    `deployed`             bigint(20)       DEFAULT NULL       COMMENT '已部署',
    `happen_area`          bigint(20)       DEFAULT NULL       COMMENT '事发地',
    `area`                 varchar(255)     DEFAULT NULL       COMMENT '区域描述',
    `used`                 bigint(20)       DEFAULT NULL       COMMENT '是否启用（USED 10=启用）',
    PRIMARY KEY (`dept_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门扩展信息（来自 ln_pro sys_sec_department）';

-- sys_menu 扩展表：与 sys_menu 1:1，存储老系统桌面/布局/路由/日志配置等
CREATE TABLE `sys_menu_ext` (
    `menu_id`              bigint(20)       NOT NULL           COMMENT '菜单ID（关联 sys_menu.menu_id）',
    -- 路由 / 跳转
    `is_route`             int(10)          DEFAULT NULL       COMMENT '是否路由菜单（0:不是 1:是）',
    `keep_alive`           int(10)          DEFAULT NULL       COMMENT '是否缓存路由（0:否 1:是）',
    `redirect_url`         varchar(255)     DEFAULT NULL       COMMENT '默认跳转地址',
    `internal_external`    int(10)          DEFAULT NULL       COMMENT '打开方式（0:内部 1:外部）',
    `class_name`           text                                DEFAULT NULL COMMENT '类名',
    -- 日志 / 桌面
    `remember_log`         bigint(20)       DEFAULT NULL       COMMENT '是否记录日志',
    `log_string`           text                                DEFAULT NULL COMMENT '日志字符串',
    `config`               mediumtext                          DEFAULT NULL COMMENT '配置 XML（老系统桌面布局）',
    PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单扩展信息（来自 ln_pro sys_sec_operation）';

-- ============================================================
-- 第 3 部分：ln_pro 独有表（6 张，按融合方案"新建表保留"）
-- 目标库无对应模型、且暂无融合计划，原样建表 + 数据直迁（见 05_dml_公告与文件.sql）。
-- 与前两部分不同，本部分使用 DROP TABLE IF EXISTS，可重跑（表内数据仅来自迁移，无自产数据）。
-- 统一 utf8mb4 / InnoDB；字段与源库同名同义（保持小写蛇形），便于后续按需对齐。
--
-- 已剔除（无源数据 / 无消费方）：
--   sys_category              ln_pro 源库 0 行
--   sys_sec_operation_fail    03 已拦在 appid 之外，本身为空表
--   sys_sec_send_department   sys_notice 已迁，无对应目标模型
--   sys_use_scope_ref         旧用户 ID 未映射，无对应目标模型
-- ============================================================

-- 允许登录 IP 白名单
DROP TABLE IF EXISTS `sys_allow_loginip`;
CREATE TABLE `sys_allow_loginip` (
    `ip` varchar(255) NOT NULL,
    PRIMARY KEY (`ip`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='允许登录IP白名单（来自 ln_pro）';

-- 校验规则
DROP TABLE IF EXISTS `sys_check_rule`;
CREATE TABLE `sys_check_rule` (
    `id`              varchar(32)   NOT NULL COMMENT '主键id',
    `rule_name`       varchar(100)  DEFAULT NULL COMMENT '规则名称',
    `rule_code`       varchar(100)  DEFAULT NULL COMMENT '规则Code',
    `rule_json`       varchar(1024) DEFAULT NULL COMMENT '规则JSON',
    `rule_description` varchar(200) DEFAULT NULL COMMENT '规则描述',
    `update_by`       varchar(32)   DEFAULT NULL COMMENT '更新人',
    `update_time`     datetime      DEFAULT NULL COMMENT '更新时间',
    `create_by`       varchar(32)   DEFAULT NULL COMMENT '创建人',
    `create_time`     datetime      DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uni_sys_check_rule_code` (`rule_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校验规则（来自 ln_pro）';

-- 填值规则
DROP TABLE IF EXISTS `sys_fill_rule`;
CREATE TABLE `sys_fill_rule` (
    `id`          varchar(32)  NOT NULL COMMENT '主键ID',
    `rule_name`   varchar(100) DEFAULT NULL COMMENT '规则名称',
    `rule_code`   varchar(100) DEFAULT NULL COMMENT '规则Code',
    `rule_class`  varchar(100) DEFAULT NULL COMMENT '规则实现类',
    `rule_params` varchar(200) DEFAULT NULL COMMENT '规则参数',
    `update_by`   varchar(32)  DEFAULT NULL COMMENT '修改人',
    `update_time` datetime     DEFAULT NULL COMMENT '修改时间',
    `create_by`   varchar(32)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uni_sys_fill_rule_code` (`rule_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='填值规则（来自 ln_pro）';

-- 系统布局排版
DROP TABLE IF EXISTS `sys_layout`;
CREATE TABLE `sys_layout` (
    `id`         varchar(32)   DEFAULT NULL COMMENT '主键',
    `sys_name`   varchar(250)  DEFAULT NULL COMMENT '系统名称',
    `layout_name` varchar(250) DEFAULT NULL COMMENT '布局名称',
    `config`     mediumtext    COMMENT '配置XML',
    `image`      text          COMMENT '预览图',
    `icon`       text          COMMENT '图标',
    `description` text         COMMENT '描述',
    KEY `idx_slay_id` (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统布局排版（来自 ln_pro）';

-- 权限配置变更日志
DROP TABLE IF EXISTS `sys_sec_config_logs`;
CREATE TABLE `sys_sec_config_logs` (
    `id`          varchar(32)  NOT NULL,
    `config_type` int(11)      DEFAULT NULL COMMENT '配置类型',
    `create_time` varchar(32)  DEFAULT NULL COMMENT '创建时间',
    `log_name`    varchar(100) DEFAULT NULL COMMENT '日志名称',
    `config`      mediumtext   COMMENT '配置内容',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限配置变更日志（来自 ln_pro）';

-- 子系统信息
DROP TABLE IF EXISTS `sys_sec_system_info`;
CREATE TABLE `sys_sec_system_info` (
    `id`          varchar(60)  NOT NULL COMMENT '主键',
    `name`        varchar(180) DEFAULT NULL COMMENT '系统名称',
    `description` varchar(300) DEFAULT NULL COMMENT '系统描述',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子系统信息（来自 ln_pro）';

-- ============================================================
-- 第 4 部分：按钮权限/区域阶段保留表（9 张，配合 06_dml_按钮权限与区域.sql）
-- ============================================================

-- 操作动作字典（第二/三阶段 F 按钮的名称来源）
DROP TABLE IF EXISTS `sys_sec_action`;
CREATE TABLE `sys_sec_action` (
    `id`   int(11)     NOT NULL AUTO_INCREMENT COMMENT '动作ID',
    `name` varchar(100) DEFAULT NULL COMMENT '动作名称（查询/修改/删除…）',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作动作字典（来自 ln_pro）';

-- 前端组件-服务-方法映射（纯技术配置，无目标模型，直迁留档）
DROP TABLE IF EXISTS `sys_sec_frontend_opaction`;
CREATE TABLE `sys_sec_frontend_opaction` (
    `mname`      varchar(100) DEFAULT NULL COMMENT '前端组件名',
    `svcname`    varchar(100) DEFAULT NULL COMMENT '服务名',
    `methodname` varchar(100) DEFAULT NULL COMMENT '方法名',
    KEY `idx_sfo_mname` (`mname`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端操作映射（来自 ln_pro）';

-- 角色-区域关联（区域≠部门，无法并入 sys_role_dept，直迁留档）
DROP TABLE IF EXISTS `sys_sec_role_region`;
CREATE TABLE `sys_sec_role_region` (
    `role_id`  varchar(32) NOT NULL COMMENT '角色ID（ln_pro sys_sec_role.ID）',
    `region_id` varchar(255) NOT NULL COMMENT '区域编码（对应 sys_area.area_code）',
    UNIQUE KEY `uk_srr` (`role_id`, `region_id`) USING BTREE,
    KEY `idx_srr_region` (`region_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-区域关联（来自 ln_pro，旧角色ID未映射）';

-- 角色-菜单-字典值过滤（无对应模型，1 行数据，直迁留档）
DROP TABLE IF EXISTS `sys_sec_role_dicts`;
CREATE TABLE `sys_sec_role_dicts` (
    `role_id`      varchar(32) NOT NULL COMMENT '角色ID（ln_pro sys_sec_role.ID）',
    `operation_id` varchar(32) NOT NULL COMMENT '菜单ID（ln_pro sys_sec_operation.ID）',
    `code`         varchar(32) NOT NULL COMMENT '字典值',
    `name`         varchar(32) NOT NULL COMMENT '字典名称',
    UNIQUE KEY `uk_srd` (`role_id`, `operation_id`, `code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色字典值过滤（来自 ln_pro）';

-- 交通领域规范（18 行字典性质，字段多于 sys_dict，保留原模型）
DROP TABLE IF EXISTS `norm_domain`;
CREATE TABLE `norm_domain` (
    `id`          decimal(38,0) NOT NULL COMMENT '主键',
    `code`        varchar(8)   DEFAULT NULL COMMENT '编码',
    `name`        varchar(32)  DEFAULT NULL COMMENT '名称',
    `description` text         COMMENT '描述',
    `valid`       decimal(2,0) DEFAULT NULL COMMENT '有效标志',
    `short_name`  varchar(16)  DEFAULT NULL COMMENT '简称',
    `tab_ext`     varchar(8)   DEFAULT NULL COMMENT '表扩展',
    `sort`        int(11)      DEFAULT NULL COMMENT '排序',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交通领域规范（来自 ln_pro）';

-- 时间粒度规范
DROP TABLE IF EXISTS `norm_range`;
CREATE TABLE `norm_range` (
    `id`          varchar(2)   NOT NULL COMMENT '主键',
    `code`        varchar(8)   DEFAULT NULL COMMENT '编码',
    `name`        varchar(32)  DEFAULT NULL COMMENT '名称',
    `description` text         COMMENT '描述',
    `valid`       decimal(38,0) DEFAULT NULL COMMENT '有效标志',
    `pattern`     varchar(24)  DEFAULT NULL COMMENT '时间格式',
    `mins`        decimal(38,0) DEFAULT NULL COMMENT '分钟数',
    `tab_ext`     varchar(16)  DEFAULT NULL COMMENT '表扩展',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间粒度规范（来自 ln_pro）';

-- 数据来源规范
DROP TABLE IF EXISTS `norm_source`;
CREATE TABLE `norm_source` (
    `id`          varchar(4)   NOT NULL COMMENT '主键',
    `code`        varchar(8)   DEFAULT NULL COMMENT '编码',
    `name`        varchar(64)  DEFAULT NULL COMMENT '名称',
    `description` text         COMMENT '描述',
    `valid`       decimal(38,0) DEFAULT NULL COMMENT '有效标志',
    `short_name`  varchar(16)  DEFAULT NULL COMMENT '简称',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据来源规范（来自 ln_pro）';

-- 时间特征规范
DROP TABLE IF EXISTS `norm_timefeature`;
CREATE TABLE `norm_timefeature` (
    `id`          varchar(2)    NOT NULL COMMENT '主键',
    `code`        varchar(8)    DEFAULT NULL COMMENT '编码',
    `name`        varchar(32)   DEFAULT NULL COMMENT '名称',
    `description` text          COMMENT '描述',
    `valid`       decimal(38,0) DEFAULT NULL COMMENT '有效标志',
    `parent`      decimal(38,0) DEFAULT NULL COMMENT '父特征',
    `inset`       decimal(38,0) DEFAULT NULL COMMENT '内嵌标志',
    `flag`        decimal(38,0) DEFAULT NULL COMMENT '标志',
    `min_range`   decimal(38,0) DEFAULT NULL COMMENT '最小范围(分钟)',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间特征规范（来自 ln_pro）';

-- 计量单位规范
DROP TABLE IF EXISTS `norm_unit`;
CREATE TABLE `norm_unit` (
    `id`            varchar(20) NOT NULL COMMENT '主键',
    `code`          varchar(8)  DEFAULT NULL COMMENT '编码',
    `name`          varchar(32) DEFAULT NULL COMMENT '名称',
    `exchange_id`   int(11)     DEFAULT NULL COMMENT '换算基准单位',
    `exchange_rate` int(11)     DEFAULT NULL COMMENT '换算率',
    `valid`         int(11)     DEFAULT NULL COMMENT '有效标志',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计量单位规范（来自 ln_pro）';

-- ============================================================
-- 脚本结束
-- ============================================================