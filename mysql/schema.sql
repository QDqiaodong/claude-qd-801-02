-- 混凝土搅拌站 · 生产调度与罐车管理
-- ddl-auto=none，表结构与种子数据全部在这里建好（mysql/Dockerfile 在构建期执行本文件）
SET NAMES utf8mb4;

-- ========================= 建表 =========================
-- 试块质检模块（先删，外键只依赖任务单/发车单）
DROP TABLE IF EXISTS recheck_task;
DROP TABLE IF EXISTS strength_result;
DROP TABLE IF EXISTS strength_test_item;
DROP TABLE IF EXISTS cube_dispatch_ticket;
DROP TABLE IF EXISTS mix_snapshot_item;
DROP TABLE IF EXISTS cube_group;
DROP TABLE IF EXISTS fault_reassignment;
DROP TABLE IF EXISTS fault_shutdown_record;
DROP TABLE IF EXISTS replenish_request;
DROP TABLE IF EXISTS dispatch_ticket;
DROP TABLE IF EXISTS mix_ratio_item;
DROP TABLE IF EXISTS production_order;
DROP TABLE IF EXISTS silo;
DROP TABLE IF EXISTS truck;
DROP TABLE IF EXISTS mixer;
DROP TABLE IF EXISTS mixing_plant;

-- 搅拌楼
CREATE TABLE mixing_plant (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    plant_code  VARCHAR(32)  NOT NULL COMMENT '楼号编码',
    name        VARCHAR(64)  NOT NULL COMMENT '搅拌楼名称',
    line_count  INT          NULL COMMENT '生产线数量',
    location    VARCHAR(128) NULL COMMENT '所在区域',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plant_code (plant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搅拌楼';

-- 搅拌主机
CREATE TABLE mixer (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    mixer_code           VARCHAR(32)   NOT NULL COMMENT '主机编码',
    name                 VARCHAR(64)   NOT NULL COMMENT '主机名称',
    model                VARCHAR(32)   NULL COMMENT '型号 HZS120/HZS180',
    theoretical_capacity DECIMAL(8,2)  NULL COMMENT '理论产能 m³/h',
    daily_plan_volume    DECIMAL(10,2) NULL COMMENT '当日计划方量 m³',
    status               VARCHAR(16)   NOT NULL COMMENT 'RUNNING/IDLE/MAINTENANCE/FAULT',
    plant_id             BIGINT        NULL COMMENT '所属搅拌楼',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mixer_code (mixer_code),
    KEY idx_mixer_plant (plant_id),
    CONSTRAINT fk_mixer_plant FOREIGN KEY (plant_id) REFERENCES mixing_plant (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搅拌主机';

-- 生产任务单（聚合根）
CREATE TABLE production_order (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    order_no           VARCHAR(32)   NOT NULL COMMENT '任务单号',
    project_name       VARCHAR(128)  NOT NULL COMMENT '工程名称',
    contractor         VARCHAR(128)  NULL COMMENT '施工单位',
    pour_part          VARCHAR(128)  NULL COMMENT '浇筑部位',
    strength_grade     VARCHAR(8)    NOT NULL COMMENT '强度等级 C25/C30/C35/C40',
    planned_volume     DECIMAL(10,2) NOT NULL COMMENT '计划方量 m³',
    slump              VARCHAR(32)   NULL COMMENT '坍落度要求',
    status             VARCHAR(16)   NOT NULL COMMENT 'PENDING/PRODUCING/DONE',
    planned_start      DATETIME      NULL COMMENT '计划开始',
    planned_end        DATETIME      NULL COMMENT '计划结束',
    water_binder_ratio DECIMAL(5,3)  NULL COMMENT '水胶比',
    remark             VARCHAR(255)  NULL,
    mixer_id           BIGINT        NULL COMMENT '排产主机',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_mixer (mixer_id),
    KEY idx_order_status (status),
    CONSTRAINT fk_order_mixer FOREIGN KEY (mixer_id) REFERENCES mixer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产任务单';

-- 配合比明细（任务单子行，只能通过任务单维护）
CREATE TABLE mix_ratio_item (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    order_id      BIGINT        NOT NULL COMMENT '所属任务单',
    material_code VARCHAR(16)   NOT NULL COMMENT '材料编码',
    material_name VARCHAR(64)   NULL COMMENT '材料名称',
    dosage        DECIMAL(10,2) NOT NULL COMMENT '每方用量 kg/m³',
    ratio_percent DECIMAL(6,2)  NULL COMMENT '占每方总用量百分比',
    seq           INT           NULL COMMENT '展示顺序',
    PRIMARY KEY (id),
    KEY idx_item_order (order_id),
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES production_order (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配合比明细';

-- 罐车与司机
CREATE TABLE truck (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    plate_no     VARCHAR(32)  NOT NULL COMMENT '车牌号',
    capacity     DECIMAL(6,2) NULL COMMENT '罐体容积 m³',
    driver_name  VARCHAR(32)  NULL COMMENT '司机',
    driver_phone VARCHAR(32)  NULL COMMENT '司机电话',
    status       VARCHAR(16)  NOT NULL COMMENT 'STANDBY/LOADING/ON_ROAD/UNLOADING/WASHING',
    queue_seq    INT          NULL COMMENT '发车排队序号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_truck_plate (plate_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='罐车';

-- 发车单
CREATE TABLE dispatch_ticket (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_no   VARCHAR(32)  NOT NULL COMMENT '发车单号',
    order_id    BIGINT       NOT NULL COMMENT '任务单',
    truck_id    BIGINT       NOT NULL COMMENT '罐车',
    load_volume DECIMAL(8,2) NULL COMMENT '装车方量 m³',
    load_time   DATETIME     NULL COMMENT '装车时间',
    depart_time DATETIME     NULL COMMENT '出厂时间',
    arrive_time DATETIME     NULL COMMENT '到工地时间',
    sign_volume DECIMAL(8,2) NULL COMMENT '签收方量 m³',
    status      VARCHAR(16)  NOT NULL COMMENT 'LOADED/DEPARTED/ARRIVED/SIGNED',
    remark      VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_ticket_order (order_id),
    KEY idx_ticket_truck (truck_id),
    CONSTRAINT fk_ticket_order FOREIGN KEY (order_id) REFERENCES production_order (id),
    CONSTRAINT fk_ticket_truck FOREIGN KEY (truck_id) REFERENCES truck (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发车单';

-- 原材料筒仓
CREATE TABLE silo (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    silo_code        VARCHAR(32)   NOT NULL COMMENT '仓号',
    name             VARCHAR(64)   NOT NULL COMMENT '仓名称',
    material_code    VARCHAR(16)   NOT NULL COMMENT 'CEMENT/FLYASH/SLAG/SAND/STONE',
    capacity_ton     DECIMAL(10,2) NULL COMMENT '仓容 吨',
    stock_ton        DECIMAL(10,2) NULL COMMENT '当前库存 吨',
    safety_stock_ton DECIMAL(10,2) NULL COMMENT '安全库存 吨',
    PRIMARY KEY (id),
    UNIQUE KEY uk_silo_code (silo_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原材料筒仓';

-- 补料申请
CREATE TABLE replenish_request (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    request_no   VARCHAR(32)   NOT NULL COMMENT '申请单号',
    silo_id      BIGINT        NOT NULL COMMENT '筒仓',
    quantity_ton DECIMAL(10,2) NULL COMMENT '补料量 吨',
    applicant    VARCHAR(32)   NULL COMMENT '申请人',
    status       VARCHAR(16)   NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
    created_at   DATETIME      NULL COMMENT '申请时间',
    decided_by   VARCHAR(32)   NULL COMMENT '审批人',
    decided_at   DATETIME      NULL COMMENT '审批时间',
    remark       VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_request_silo (silo_id),
    CONSTRAINT fk_request_silo FOREIGN KEY (silo_id) REFERENCES silo (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='补料申请';

-- 主机临时故障停机处置记录
CREATE TABLE fault_shutdown_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    record_no     VARCHAR(32)  NOT NULL COMMENT '处置单号',
    mixer_id      BIGINT       NOT NULL COMMENT '故障主机',
    reason        VARCHAR(255) NOT NULL COMMENT '故障原因',
    occurred_at   DATETIME     NOT NULL COMMENT '故障发生时间',
    confirmed_at  DATETIME     NOT NULL COMMENT '确认处置时间',
    operator_name VARCHAR(32)  NULL COMMENT '调度员',
    status        VARCHAR(16)  NOT NULL COMMENT 'CONFIRMED',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fault_record_no (record_no),
    KEY idx_fault_mixer (mixer_id),
    CONSTRAINT fk_fault_mixer FOREIGN KEY (mixer_id) REFERENCES mixer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主机临时故障停机处置记录';

-- 故障任务改派明细
CREATE TABLE fault_reassignment (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    record_id       BIGINT       NOT NULL COMMENT '故障处置单',
    order_id        BIGINT       NOT NULL COMMENT '改派任务单',
    source_mixer_id BIGINT       NOT NULL COMMENT '原主机',
    target_mixer_id BIGINT       NOT NULL COMMENT '接替主机',
    planned_start   DATETIME     NOT NULL COMMENT '原计划开始',
    planned_end     DATETIME     NOT NULL COMMENT '原计划结束',
    PRIMARY KEY (id),
    KEY idx_fault_reassign_order (order_id),
    KEY idx_fault_reassign_record (record_id),
    KEY idx_fault_reassign_target (target_mixer_id),
    CONSTRAINT fk_fault_reassign_record FOREIGN KEY (record_id) REFERENCES fault_shutdown_record (id),
    CONSTRAINT fk_fault_reassign_order FOREIGN KEY (order_id) REFERENCES production_order (id),
    CONSTRAINT fk_fault_reassign_source FOREIGN KEY (source_mixer_id) REFERENCES mixer (id),
    CONSTRAINT fk_fault_reassign_target FOREIGN KEY (target_mixer_id) REFERENCES mixer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故障任务改派明细';

-- ========================= 试块质检模块 =========================

-- 试块组（留样登记，聚合根）：登记时快照工程/强度等级/配合比
CREATE TABLE cube_group (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    cube_no         VARCHAR(32)  NOT NULL COMMENT '试块编号（全系统唯一，并发只成功一次）',
    order_id        BIGINT       NOT NULL COMMENT '所属生产任务单',
    sampled_at      DATETIME     NOT NULL COMMENT '取样时间（7d/28d 到期日据此计算）',
    curing_method   VARCHAR(16)  NOT NULL COMMENT '养护方式 STANDARD/ONSITE',
    inspector       VARCHAR(32)  NOT NULL COMMENT '登记质检员',
    status          VARCHAR(16)  NOT NULL COMMENT 'TESTING/QUALIFIED/RECHECK/UNQUALIFIED',
    project_name    VARCHAR(128) NOT NULL COMMENT '工程名称快照',
    strength_grade  VARCHAR(8)   NOT NULL COMMENT '强度等级快照 C25/C30/C35/C40',
    pour_part       VARCHAR(128) NULL COMMENT '浇筑部位快照',
    contractor      VARCHAR(128) NULL COMMENT '施工单位快照',
    remark          VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL COMMENT '登记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cube_no (cube_no),
    KEY idx_cube_order (order_id),
    KEY idx_cube_status (status),
    KEY idx_cube_sampled (sampled_at),
    CONSTRAINT fk_cube_order FOREIGN KEY (order_id) REFERENCES production_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试块组（留样登记）';

-- 试块组配合比快照（登记时按任务单复制，之后任务单改配比不影响质检判定来源）
CREATE TABLE mix_snapshot_item (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    group_id      BIGINT        NOT NULL COMMENT '所属试块组',
    material_code VARCHAR(16)   NOT NULL COMMENT '材料编码',
    material_name VARCHAR(64)   NULL COMMENT '材料名称',
    dosage        DECIMAL(10,2) NOT NULL COMMENT '每方用量 kg/m³ 快照',
    ratio_percent DECIMAL(6,2)  NULL COMMENT '占比快照',
    seq           INT           NULL COMMENT '展示顺序',
    PRIMARY KEY (id),
    KEY idx_snapshot_group (group_id),
    CONSTRAINT fk_snapshot_group FOREIGN KEY (group_id) REFERENCES cube_group (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试块组配合比快照';

-- 试块组关联的已装车发车单（登记时至少关联一张同任务单已装车发车单）
CREATE TABLE cube_dispatch_ticket (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    group_id   BIGINT NOT NULL COMMENT '所属试块组',
    ticket_id  BIGINT NOT NULL COMMENT '关联发车单（须同任务单且已装车）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_ticket (group_id, ticket_id),
    KEY idx_cdt_ticket (ticket_id),
    CONSTRAINT fk_cdt_group FOREIGN KEY (group_id) REFERENCES cube_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_cdt_ticket FOREIGN KEY (ticket_id) REFERENCES dispatch_ticket (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试块组关联发车单';

-- 检测项：登记试块组时按取样时间生成 7d/28d 两条
CREATE TABLE strength_test_item (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    group_id    BIGINT      NOT NULL COMMENT '所属试块组',
    age_days    INT         NOT NULL COMMENT '龄期天数 7/28',
    due_at      DATETIME    NOT NULL COMMENT '到期时间 = 取样时间 + 龄期',
    status      VARCHAR(16) NOT NULL COMMENT 'PENDING/DONE/RECHECK（持久态；待检/逾期/未到期/合格/不合格由服务端按时钟派生）',
    result_id   BIGINT      NULL COMMENT '正式结果（7d 趋势结果与 28d 判定结果均唯一，不可覆盖）',
    created_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_group_age (group_id, age_days),
    UNIQUE KEY uk_item_result (result_id),
    KEY idx_item_due (due_at),
    KEY idx_item_status (status),
    CONSTRAINT fk_item_group FOREIGN KEY (group_id) REFERENCES cube_group (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抗压强度检测项';

-- 抗压强度读数与判定：三块读数 + 服务端统一计算的平均/最小/判定
CREATE TABLE strength_result (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    item_id         BIGINT       NOT NULL COMMENT '所属检测项',
    group_id        BIGINT       NOT NULL COMMENT '所属试块组（冗余便于追溯）',
    age_days        INT          NOT NULL COMMENT '龄期 7/28',
    grade           VARCHAR(8)   NOT NULL COMMENT '判定时使用的强度等级快照',
    strength_1      DECIMAL(6,2) NOT NULL COMMENT '试块 1 抗压强度 MPa',
    strength_2      DECIMAL(6,2) NOT NULL COMMENT '试块 2 抗压强度 MPa',
    strength_3      DECIMAL(6,2) NOT NULL COMMENT '试块 3 抗压强度 MPa',
    avg_value       DECIMAL(6,2) NOT NULL COMMENT '平均值（服务端计算）',
    min_value       DECIMAL(6,2) NOT NULL COMMENT '最小值（服务端计算）',
    verdict         VARCHAR(16)  NOT NULL COMMENT '28d: QUALIFIED/UNQUALIFIED；7d: TREND_OK/TREND_LOW',
    design_value    DECIMAL(6,2) NOT NULL COMMENT '设计强度 fcu,k 快照',
    avg_threshold   DECIMAL(6,2) NOT NULL COMMENT '平均门槛（28d 1.15fcu,k / 7d 0.75fcu,k）快照',
    min_threshold   DECIMAL(6,2) NOT NULL COMMENT '最小门槛（28d 0.95fcu,k）快照',
    tested_at       DATETIME     NOT NULL COMMENT '检测时间',
    operator_name   VARCHAR(32)  NOT NULL COMMENT '检测操作者',
    PRIMARY KEY (id),
    UNIQUE KEY uk_result_item (item_id),
    KEY idx_result_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抗压强度检测结果（原始读数不可覆盖）';

-- 复检任务：28d 未达门槛时在同一事务内生成，每组至多一条
CREATE TABLE recheck_task (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    recheck_no         VARCHAR(32)   NOT NULL COMMENT '复检单号',
    group_id           BIGINT        NOT NULL COMMENT '所属试块组',
    original_item_id   BIGINT        NOT NULL COMMENT '原 28d 检测项（复检必须引用原检测）',
    original_result_id BIGINT        NOT NULL COMMENT '原不合格结果（不可覆盖，仅引用）',
    avg_1              DECIMAL(6,2)  NULL COMMENT '复检读数 1',
    avg_2              DECIMAL(6,2)  NULL COMMENT '复检读数 2',
    avg_3              DECIMAL(6,2)  NULL COMMENT '复检读数 3',
    avg_value          DECIMAL(6,2)  NULL COMMENT '复检平均值',
    min_value          DECIMAL(6,2)  NULL COMMENT '复检最小值',
    verdict            VARCHAR(16)   NULL COMMENT '复检判定 QUALIFIED/UNQUALIFIED',
    design_value       DECIMAL(6,2)  NULL COMMENT '设计强度快照',
    avg_threshold      DECIMAL(6,2)  NULL COMMENT '平均门槛快照',
    min_threshold      DECIMAL(6,2)  NULL COMMENT '最小门槛快照',
    status             VARCHAR(16)   NOT NULL COMMENT 'PENDING/DONE',
    created_at         DATETIME      NOT NULL COMMENT '生成时间（原结果提交时间）',
    created_by         VARCHAR(32)   NOT NULL COMMENT '复检单生成人（原检测操作者）',
    tested_at          DATETIME      NULL COMMENT '复检时间',
    operator_name      VARCHAR(32)   NULL COMMENT '复检操作者',
    remark             VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recheck_no (recheck_no),
    UNIQUE KEY uk_recheck_original_item (original_item_id),
    KEY idx_recheck_group (group_id),
    KEY idx_recheck_status (status),
    CONSTRAINT fk_recheck_group FOREIGN KEY (group_id) REFERENCES cube_group (id),
    CONSTRAINT fk_recheck_item FOREIGN KEY (original_item_id) REFERENCES strength_test_item (id),
    CONSTRAINT fk_recheck_result FOREIGN KEY (original_result_id) REFERENCES strength_result (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='28d 不合格复检任务（唯一）';

-- ========================= 种子数据 =========================

INSERT INTO mixing_plant (plant_code, name, line_count, location) VALUES
('JL-01', '1 号搅拌楼', 2, '厂区东侧 A 区'),
('JL-02', '2 号搅拌楼', 2, '厂区东侧 B 区'),
('JL-03', '3 号搅拌楼', 1, '厂区西侧 C 区');

INSERT INTO mixer (mixer_code, name, model, theoretical_capacity, daily_plan_volume, status, plant_id) VALUES
('HZS120-1', '1# 主机', 'HZS120', 120.00,  72.00, 'RUNNING',     1),
('HZS120-2', '2# 主机', 'HZS120', 120.00,  96.00, 'RUNNING',     1),
('HZS180-3', '3# 主机', 'HZS180', 180.00,  90.00, 'RUNNING',     2),
('HZS180-4', '4# 主机', 'HZS180', 180.00, 130.00, 'RUNNING',     2),
('HZS120-5', '5# 主机', 'HZS120', 120.00,  58.00, 'RUNNING',     3),
('HZS090-6', '6# 主机', 'HZS90',   90.00,  55.00, 'MAINTENANCE', 3),
('HZS180-7', '7# 主机', 'HZS180', 180.00,  88.00, 'FAULT',       1),
('HZS180-8', '8# 主机', 'HZS180', 180.00,  74.00, 'IDLE',        2);

INSERT INTO production_order (order_no, project_name, contractor, pour_part, strength_grade, planned_volume,
                              slump, status, planned_start, planned_end, water_binder_ratio, remark, mixer_id) VALUES
('SC-2601', '滨海国际中心 A 座', '中建三局集团有限公司', '地下室底板 C 区', 'C35', 860.00, '180±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '16:00:00'),
   0.375, '夜间连续浇筑，需 3 台泵车配合', 3),
('SC-2602', '云顶天境 3 号楼', '中天建设集团有限公司', '三层剪力墙', 'C30', 620.00, '160±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '15:00:00'),
   0.430, '墙体现浇，坍落度严格控制', 1),
('SC-2603', '高新产业园标准厂房', '中铁十四局集团有限公司', '一层柱梁板', 'C30', 740.00, '180±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '17:00:00'),
   0.430, '泵送施工，罐车到场间隔不超过 25 分钟', 5),
('SC-2604', '江滨路综合管廊', '上海隧道工程有限公司', '管廊底板 K2+300', 'C40', 480.00, '160±30mm', 'PRODUCING',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '21:00:00'),
   0.336, '抗渗 P8，掺膨胀剂，同条件试块 3 组', 3),
('SC-2605', '星海城 7 号楼', '中建八局集团有限公司', '二层顶板', 'C25', 560.00, '180±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:00:00'),
   0.514, '板面收面要求高，控制初凝时间', 2),
('SC-2606', '城东污水处理厂二期', '北京城建集团有限责任公司', '沉淀池壁板', 'C35', 690.00, '140±30mm', 'PRODUCING',
   TIMESTAMP(CURDATE(), '07:00:00'), TIMESTAMP(CURDATE(), '15:00:00'),
   0.375, '抗渗混凝土，分层浇筑每层不超过 50cm', 1),
('SC-2607', '云顶天境 5 号楼', '中天建设集团有限公司', '四层梁板', 'C30', 820.00, '180±30mm', 'PRODUCING',
   TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '18:00:00'),
   0.430, '大方量连续供应，备用罐车不少于 2 台', 4),
('SC-2608', '滨海国际中心 B 座', '中建三局集团有限公司', '二层柱', 'C40', 540.00, '160±30mm', 'PENDING',
   TIMESTAMP(CURDATE(), '13:00:00'), TIMESTAMP(CURDATE(), '22:00:00'),
   0.336, '高强柱，需延长搅拌时间 30s', 3),
('SC-2609', '高铁站前广场配套', '中铁四局集团有限公司', '站台层铺装', 'C25', 430.00, '120±30mm', 'PENDING',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '08:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00'),
   0.514, '干硬性混凝土，罐车卸料注意离析', 5),
('SC-2610', '江滨路综合管廊', '上海隧道工程有限公司', '管廊侧墙 K3+100', 'C40', 610.00, '160±30mm', 'PENDING',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '19:00:00'),
   0.336, '侧墙对称浇筑，两侧高差不超过 50cm', 8),
('SC-2611', '星海城地下车库', '中建八局集团有限公司', '车库顶板后浇带', 'C30', 380.00, '180±30mm', 'PENDING',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '08:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '12:00:00'),
   0.430, '微膨胀混凝土，待主体沉降稳定后施工', 2),
('SC-2612', '高新产业园配套楼', '中铁十四局集团有限公司', '屋面刚性保护层', 'C25', 260.00, '120±30mm', 'PENDING',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '17:00:00'),
   0.514, '保护层 40mm，随捣随抹', 5);

-- 试块质检演示用：更早的已完成任务单（配合比由下方统一批量 INSERT 按强度等级生成）
INSERT INTO production_order (order_no, project_name, contractor, pour_part, strength_grade, planned_volume,
                              slump, status, planned_start, planned_end, water_binder_ratio, remark, mixer_id) VALUES
('SC-2531', '滨海国际中心 A 座', '中建三局集团有限公司', '一期承台 A 区', 'C35', 520.00, '180±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '15:00:00'),
   0.375, '28d 试块合格样本', 3),
('SC-2532', '云顶天境 2 号楼', '中天建设集团有限公司', '地下室外墙', 'C30', 460.00, '160±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '16:00:00'),
   0.430, '28d 不合格，待复检样本', 1),
('SC-2533', '高新产业园研发楼', '中铁十四局集团有限公司', '基础筏板', 'C40', 720.00, '160±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 31 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 31 DAY), '17:00:00'),
   0.336, '复检仍失败，锁定不合格样本', 3),
('SC-2534', '星海城 6 号楼', '中建八局集团有限公司', '一层柱', 'C25', 380.00, '180±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 29 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 29 DAY), '15:00:00'),
   0.514, '初检不合格、复检合格样本', 2),
('SC-2535', '江滨路综合管廊', '上海隧道工程有限公司', '管廊底板 K0+800', 'C40', 400.00, '160±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '16:00:00'),
   0.336, '抗渗 P8，今日到 7d 龄期样本', 4),
('SC-2536', '城东污水处理厂二期', '北京城建集团有限责任公司', '沉淀池顶板 A 段', 'C30', 360.00, '140±30mm', 'DONE',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '15:00:00'),
   0.430, '7d 逾期未录样本', 5);

-- 配合比明细：按任务单强度等级套用参考配合比（kg/m³）
INSERT INTO mix_ratio_item (order_id, material_code, material_name, dosage, ratio_percent, seq)
SELECT o.id, d.code, d.name, d.dosage,
       ROUND(d.dosage / d.total * 100, 2), d.seq
FROM production_order o
JOIN (
    SELECT 'C25' AS grade, 'CEMENT'    AS code, '水泥 P·O42.5'    AS name, 230.00 AS dosage, 1 AS seq, 2366.80 AS total UNION ALL
    SELECT 'C25', 'FLYASH',   '粉煤灰 Ⅱ 级',   70.00, 2, 2366.80 UNION ALL
    SELECT 'C25', 'SLAG',     '矿粉 S95',      50.00, 3, 2366.80 UNION ALL
    SELECT 'C25', 'SAND',     '机制砂 中砂',  780.00, 4, 2366.80 UNION ALL
    SELECT 'C25', 'STONE',    '碎石 5-25mm', 1050.00, 5, 2366.80 UNION ALL
    SELECT 'C25', 'WATER',    '拌合用水',      180.00, 6, 2366.80 UNION ALL
    SELECT 'C25', 'ADMIXTURE','聚羧酸外加剂',    6.80, 7, 2366.80 UNION ALL
    SELECT 'C30', 'CEMENT',   '水泥 P·O42.5',  270.00, 1, 2379.80 UNION ALL
    SELECT 'C30', 'FLYASH',   '粉煤灰 Ⅱ 级',   70.00, 2, 2379.80 UNION ALL
    SELECT 'C30', 'SLAG',     '矿粉 S95',      60.00, 3, 2379.80 UNION ALL
    SELECT 'C30', 'SAND',     '机制砂 中砂',  760.00, 4, 2379.80 UNION ALL
    SELECT 'C30', 'STONE',    '碎石 5-25mm', 1040.00, 5, 2379.80 UNION ALL
    SELECT 'C30', 'WATER',    '拌合用水',      172.00, 6, 2379.80 UNION ALL
    SELECT 'C30', 'ADMIXTURE','聚羧酸外加剂',    7.80, 7, 2379.80 UNION ALL
    SELECT 'C35', 'CEMENT',   '水泥 P·O42.5',  310.00, 1, 2374.20 UNION ALL
    SELECT 'C35', 'FLYASH',   '粉煤灰 Ⅱ 级',   60.00, 2, 2374.20 UNION ALL
    SELECT 'C35', 'SLAG',     '矿粉 S95',      70.00, 3, 2374.20 UNION ALL
    SELECT 'C35', 'SAND',     '机制砂 中砂',  730.00, 4, 2374.20 UNION ALL
    SELECT 'C35', 'STONE',    '碎石 5-25mm', 1030.00, 5, 2374.20 UNION ALL
    SELECT 'C35', 'WATER',    '拌合用水',      165.00, 6, 2374.20 UNION ALL
    SELECT 'C35', 'ADMIXTURE','聚羧酸外加剂',    9.20, 7, 2374.20 UNION ALL
    SELECT 'C40', 'CEMENT',   '水泥 P·O42.5',  350.00, 1, 2358.80 UNION ALL
    SELECT 'C40', 'FLYASH',   '粉煤灰 Ⅱ 级',   50.00, 2, 2358.80 UNION ALL
    SELECT 'C40', 'SLAG',     '矿粉 S95',      70.00, 3, 2358.80 UNION ALL
    SELECT 'C40', 'SAND',     '机制砂 中砂',  700.00, 4, 2358.80 UNION ALL
    SELECT 'C40', 'STONE',    '碎石 5-25mm', 1020.00, 5, 2358.80 UNION ALL
    SELECT 'C40', 'WATER',    '拌合用水',      158.00, 6, 2358.80 UNION ALL
    SELECT 'C40', 'ADMIXTURE','聚羧酸外加剂',   10.80, 7, 2358.80
) d ON d.grade = o.strength_grade;

INSERT INTO truck (plate_no, capacity, driver_name, driver_phone, status, queue_seq) VALUES
('鲁A·T1201', 12.00, '王建国', '13805310001', 'STANDBY',   1),
('鲁A·T1202', 12.00, '李海涛', '13805310002', 'STANDBY',   2),
('鲁A·T1203', 10.00, '张 伟',  '13805310003', 'STANDBY',   3),
('鲁A·T1204', 10.00, '刘志强', '13805310004', 'LOADING',   4),
('鲁A·T1205', 15.00, '陈立明', '13805310005', 'WASHING',   5),
('鲁A·T1206', 15.00, '赵永刚', '13805310006', 'ON_ROAD',   6),
('鲁A·T1207', 12.00, '孙 磊',  '13805310007', 'UNLOADING', 7),
('鲁A·T1208', 10.00, '周文斌', '13805310008', 'ON_ROAD',   8),
('鲁A·T1209', 12.00, '吴国庆', '13805310009', 'ON_ROAD',   9),
('鲁A·T1210',  8.00, '郑小川', '13805310010', 'LOADING',  10);

INSERT INTO dispatch_ticket (ticket_no, order_id, truck_id, load_volume, load_time, depart_time, arrive_time,
                             sign_volume, status, remark) VALUES
-- SC-2601（前天）
('FC-0001', 1, 1, 12.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:12:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:55:00'), 11.80, 'SIGNED', '首车，工地签收回单齐全'),
('FC-0002', 1, 3, 12.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:42:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:25:00'), 11.90, 'SIGNED', NULL),
('FC-0003', 1, 5, 11.50, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:13:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:58:00'), 11.50, 'SIGNED', NULL),
('FC-0004', 1, 7, 11.50, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:44:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:30:00'), 11.30, 'SIGNED', '途中堵车 12 分钟'),
('FC-0005', 1, 8, 11.50, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:15:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:02:00'), 11.40, 'SIGNED', NULL),
('FC-0006', 1, 9, 12.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:41:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:20:00'), 12.00, 'SIGNED', NULL),
-- SC-2602（前天）
('FC-0007', 2, 2, 10.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:52:00'),  9.80, 'SIGNED', NULL),
('FC-0008', 2, 4, 10.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:51:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:35:00'), 10.00, 'SIGNED', NULL),
('FC-0009', 2, 6, 10.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:33:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:18:00'),  9.90, 'SIGNED', NULL),
('FC-0010', 2, 10, 9.50, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:12:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:55:00'),  9.40, 'SIGNED', NULL),
('FC-0011', 2, 1, 9.50, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:52:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '12:35:00'),  9.50, 'SIGNED', '午间收车'),
-- SC-2603（昨天）
('FC-0012', 3, 3, 12.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:21:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:05:00'), 11.80, 'SIGNED', NULL),
('FC-0013', 3, 5, 12.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:50:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:02:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:47:00'), 12.00, 'SIGNED', NULL),
('FC-0014', 3, 7, 12.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:42:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:28:00'), 11.70, 'SIGNED', NULL),
('FC-0015', 3, 8, 12.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:23:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:08:00'), 11.90, 'SIGNED', NULL),
('FC-0016', 3, 9, 11.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:50:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:01:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:45:00'), 11.00, 'SIGNED', NULL),
-- SC-2604（昨天下午，生产中）
('FC-0017', 4, 1, 11.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '13:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '13:31:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:16:00'), 10.90, 'SIGNED', NULL),
('FC-0018', 4, 2, 11.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:22:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:05:00'), 11.00, 'SIGNED', NULL),
('FC-0019', 4, 4, 11.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:11:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:58:00'), NULL, 'ARRIVED', '工地泵车排队中'),
('FC-0020', 4, 6, 10.50, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:50:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:02:00'), NULL, NULL, 'DEPARTED', NULL),
-- SC-2605（昨天）
('FC-0021', 5, 10, 9.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:31:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:15:00'), 8.90, 'SIGNED', NULL),
('FC-0022', 5, 1, 9.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:12:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:56:00'), 9.00, 'SIGNED', NULL),
('FC-0023', 5, 2, 9.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:52:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:36:00'), 8.80, 'SIGNED', NULL),
('FC-0024', 5, 3, 8.50, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:33:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '12:18:00'), 8.50, 'SIGNED', NULL),
-- SC-2606（今天，生产中）
('FC-0025', 6, 5, 10.50, TIMESTAMP(CURDATE(), '07:30:00'), TIMESTAMP(CURDATE(), '07:41:00'), TIMESTAMP(CURDATE(), '08:26:00'), 10.40, 'SIGNED', NULL),
('FC-0026', 6, 7, 10.50, TIMESTAMP(CURDATE(), '08:20:00'), TIMESTAMP(CURDATE(), '08:33:00'), TIMESTAMP(CURDATE(), '09:18:00'), NULL, 'ARRIVED', '等待泵送'),
('FC-0027', 6, 8, 10.00, TIMESTAMP(CURDATE(), '09:10:00'), TIMESTAMP(CURDATE(), '09:22:00'), NULL, NULL, 'DEPARTED', NULL),
-- SC-2607（今天，生产中）
('FC-0028', 7, 9, 12.00, TIMESTAMP(CURDATE(), '08:20:00'), TIMESTAMP(CURDATE(), '08:31:00'), NULL, NULL, 'DEPARTED', NULL),
('FC-0029', 7, 10, 12.00, TIMESTAMP(CURDATE(), '09:00:00'), NULL, NULL, NULL, 'LOADED', '等待出厂检验'),
('FC-0030', 7, 4, 12.00, TIMESTAMP(CURDATE(), '09:40:00'), NULL, NULL, NULL, 'LOADED', NULL);

-- 老任务单的已签收发车单（试块登记须关联同任务单已装车发车单）
INSERT INTO dispatch_ticket (ticket_no, order_id, truck_id, load_volume, load_time, depart_time, arrive_time,
                             sign_volume, status, remark) VALUES
('FC-1001', (SELECT id FROM production_order WHERE order_no = 'SC-2531'), 1, 12.00,
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '08:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '08:32:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:15:00'), 12.00, 'SIGNED', NULL),
('FC-1002', (SELECT id FROM production_order WHERE order_no = 'SC-2531'), 3, 11.50,
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:22:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '10:05:00'), 11.40, 'SIGNED', NULL),
('FC-1003', (SELECT id FROM production_order WHERE order_no = 'SC-2532'), 2, 10.00,
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:15:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:26:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '10:10:00'), 10.00, 'SIGNED', NULL),
('FC-1004', (SELECT id FROM production_order WHERE order_no = 'SC-2533'), 5, 12.00,
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 31 DAY), '08:15:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 31 DAY), '08:27:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 31 DAY), '09:10:00'), 11.90, 'SIGNED', NULL),
('FC-1005', (SELECT id FROM production_order WHERE order_no = 'SC-2534'), 7, 11.00,
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 29 DAY), '09:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 29 DAY), '09:22:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 29 DAY), '10:06:00'), 11.00, 'SIGNED', NULL),
('FC-1006', (SELECT id FROM production_order WHERE order_no = 'SC-2535'), 9, 12.00,
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '08:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '08:52:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '09:35:00'), 12.00, 'SIGNED', '同条件养护试块取样车次'),
('FC-1007', (SELECT id FROM production_order WHERE order_no = 'SC-2536'), 8, 10.50,
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '08:42:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '09:25:00'), 10.50, 'SIGNED', NULL);

INSERT INTO silo (silo_code, name, material_code, capacity_ton, stock_ton, safety_stock_ton) VALUES
('SIL-C01', '1# 水泥仓',  'CEMENT', 200.00, 132.50, 40.00),
('SIL-C02', '2# 水泥仓',  'CEMENT', 200.00,  88.00, 40.00),
('SIL-F01', '1# 粉煤灰仓', 'FLYASH', 120.00,  26.40, 30.00),
('SIL-F02', '2# 粉煤灰仓', 'FLYASH', 120.00,  74.60, 30.00),
('SIL-K01', '1# 矿粉仓',  'SLAG',   120.00,  52.30, 25.00),
('SIL-K02', '2# 矿粉仓',  'SLAG',   120.00,  18.90, 25.00),
('SIL-S01', '1# 砂仓',    'SAND',   600.00, 412.00, 120.00),
('SIL-G01', '1# 碎石仓',  'STONE',  800.00, 505.00, 150.00);

INSERT INTO replenish_request (request_no, silo_id, quantity_ton, applicant, status, created_at, decided_by,
                               decided_at, remark) VALUES
('BL-2601', 3,  60.00, '王建国', 'PENDING',  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:20:00'), NULL, NULL,
   '粉煤灰库存逼近安全线，明日 C30 大方量需备料'),
('BL-2602', 6,  45.00, '李海涛', 'PENDING',  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:05:00'), NULL, NULL,
   '矿粉仓低于安全库存，影响 C35/C40 配比'),
('BL-2603', 8, 120.00, '张 伟',  'PENDING',  TIMESTAMP(CURDATE(), '07:40:00'), NULL, NULL,
   '碎石储备不足，江滨路管廊连续浇筑需提前备料'),
('BL-2604', 1,  80.00, '王建国', 'APPROVED', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:15:00'), '调度主管 周炯',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:40:00'), '供应商晨间已送达'),
('BL-2605', 2,  60.00, '刘志强', 'APPROVED', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '16:20:00'), '调度主管 周炯',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '16:50:00'), NULL),
('BL-2606', 7,  90.00, '陈立明', 'APPROVED', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:30:00'), '生产经理 何斌',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00'), '机制砂含水率偏高，已复检'),
('BL-2607', 4,  30.00, '赵永刚', 'REJECTED', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:00:00'), '调度主管 周炯',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:30:00'), '供应商批次未定，暂缓采购'),
('BL-2608', 5,  25.00, '孙 磊',  'REJECTED', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:40:00'), '生产经理 何斌',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:10:00'), '仓位尚可，先消化现有库存');

-- ========================= 试块质检种子 =========================
-- 8 组试块覆盖：已合格 / 待复检 / 复检仍失败锁定 / 复检合格 / 待检 / 未到期 / 逾期
INSERT INTO cube_group (cube_no, order_id, sampled_at, curing_method, inspector, status,
                        project_name, strength_grade, pour_part, contractor, remark, created_at) VALUES
('SK-2601', (SELECT id FROM production_order WHERE order_no = 'SC-2531'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:00:00'), 'STANDARD', '陈立明', 'QUALIFIED',
   '滨海国际中心 A 座', 'C35', '一期承台 A 区', '中建三局集团有限公司', '28d 一次合格',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:05:00')),
('SK-2602', (SELECT id FROM production_order WHERE order_no = 'SC-2532'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:30:00'), 'STANDARD', '赵永刚', 'RECHECK',
   '云顶天境 2 号楼', 'C30', '地下室外墙', '中天建设集团有限公司', '28d 平均强度不足，待复检',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '09:35:00')),
('SK-2603', (SELECT id FROM production_order WHERE order_no = 'SC-2533'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 31 DAY), '08:30:00'), 'STANDARD', '王建国', 'UNQUALIFIED',
   '高新产业园研发楼', 'C40', '基础筏板', '中铁十四局集团有限公司', '复检仍不合格，已锁定并上报',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 31 DAY), '08:35:00')),
('SK-2604', (SELECT id FROM production_order WHERE order_no = 'SC-2534'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 29 DAY), '09:30:00'), 'STANDARD', '孙 磊', 'QUALIFIED',
   '星海城 6 号楼', 'C25', '一层柱', '中建八局集团有限公司', '初检不合格，复检合格',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 29 DAY), '09:35:00')),
('SK-2605', (SELECT id FROM production_order WHERE order_no = 'SC-2601'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:00:00'), 'STANDARD', '李海涛', 'TESTING',
   '滨海国际中心 A 座', 'C35', '地下室底板 C 区', '中建三局集团有限公司', '7d/28d 均未到期',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:05:00')),
('SK-2606', (SELECT id FROM production_order WHERE order_no = 'SC-2605'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:00:00'), 'STANDARD', '周文斌', 'TESTING',
   '星海城 7 号楼', 'C25', '二层顶板', '中建八局集团有限公司', '7d 未到期',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:05:00')),
('SK-2607', (SELECT id FROM production_order WHERE order_no = 'SC-2535'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '09:00:00'), 'ONSITE', '吴国庆', 'TESTING',
   '江滨路综合管廊', 'C40', '管廊底板 K0+800', '上海隧道工程有限公司', '同条件养护，今日到 7d 龄期',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '09:05:00')),
('SK-2608', (SELECT id FROM production_order WHERE order_no = 'SC-2536'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '09:00:00'), 'STANDARD', '郑小川', 'TESTING',
   '城东污水处理厂二期', 'C30', '沉淀池顶板 A 段', '北京城建集团有限责任公司', '7d 已逾期 3 天未录',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '09:05:00'));

-- 关联已装车发车单（每组至少一张）
INSERT INTO cube_dispatch_ticket (group_id, ticket_id)
SELECT g.id, t.id FROM cube_group g
JOIN production_order o ON o.id = g.order_id
JOIN dispatch_ticket t ON t.order_id = o.id
WHERE g.cube_no IN ('SK-2601','SK-2602','SK-2603','SK-2604','SK-2605','SK-2606','SK-2607','SK-2608')
  AND t.ticket_no IN ('FC-1001','FC-1003','FC-1004','FC-1005','FC-0002','FC-0021','FC-1006','FC-0007');
INSERT INTO cube_dispatch_ticket (group_id, ticket_id)
SELECT g.id, t.id FROM cube_group g
JOIN production_order o ON o.id = g.order_id
JOIN dispatch_ticket t ON t.order_id = o.id
WHERE g.cube_no = 'SK-2601' AND t.ticket_no = 'FC-1002';

-- 配合比快照（按登记时任务单配合比复制）
INSERT INTO mix_snapshot_item (group_id, material_code, material_name, dosage, ratio_percent, seq)
SELECT g.id, m.material_code, m.material_name, m.dosage, m.ratio_percent, m.seq
FROM cube_group g
JOIN mix_ratio_item m ON m.order_id = g.order_id;

-- 每组两条检测项：7d / 28d，到期 = 取样 + 龄期
INSERT INTO strength_test_item (group_id, age_days, due_at, status, result_id, created_at)
SELECT g.id, a.age, DATE_ADD(g.sampled_at, INTERVAL a.age DAY),
       CASE
           WHEN g.cube_no IN ('SK-2601','SK-2602','SK-2603','SK-2604') AND a.age = 7  THEN 'DONE'
           WHEN g.cube_no IN ('SK-2601','SK-2604') AND a.age = 28 THEN 'DONE'
           WHEN g.cube_no IN ('SK-2602','SK-2603') AND a.age = 28 THEN 'RECHECK'
           ELSE 'PENDING'
       END,
       NULL, g.created_at
FROM cube_group g
JOIN (SELECT 7 AS age UNION ALL SELECT 28) a;

-- 结果：7d 趋势（4 组老样本 + 今天到期的 SK-2607 暂无结果以演示待检）
INSERT INTO strength_result (item_id, group_id, age_days, grade, strength_1, strength_2, strength_3,
                             avg_value, min_value, verdict, design_value, avg_threshold, min_threshold,
                             tested_at, operator_name)
SELECT i.id, g.id, 7, g.strength_grade,
       CASE g.cube_no
           WHEN 'SK-2601' THEN 28.60 WHEN 'SK-2602' THEN 21.20 WHEN 'SK-2603' THEN 32.40 WHEN 'SK-2604' THEN 18.20 END,
       CASE g.cube_no
           WHEN 'SK-2601' THEN 29.40 WHEN 'SK-2602' THEN 22.80 WHEN 'SK-2603' THEN 33.10 WHEN 'SK-2604' THEN 19.10 END,
       CASE g.cube_no
           WHEN 'SK-2601' THEN 30.10 WHEN 'SK-2602' THEN 23.90 WHEN 'SK-2603' THEN 31.80 WHEN 'SK-2604' THEN 18.70 END,
       CASE g.cube_no
           WHEN 'SK-2601' THEN 29.37 WHEN 'SK-2602' THEN 22.63 WHEN 'SK-2603' THEN 32.43 WHEN 'SK-2604' THEN 18.67 END,
       CASE g.cube_no
           WHEN 'SK-2601' THEN 28.60 WHEN 'SK-2602' THEN 21.20 WHEN 'SK-2603' THEN 31.80 WHEN 'SK-2604' THEN 18.20 END,
       CASE g.cube_no
           WHEN 'SK-2601' THEN 'TREND_OK' WHEN 'SK-2602' THEN 'TREND_OK'
           WHEN 'SK-2603' THEN 'TREND_OK' WHEN 'SK-2604' THEN 'TREND_LOW' END,
       CASE g.strength_grade WHEN 'C25' THEN 25.00 WHEN 'C30' THEN 30.00 WHEN 'C35' THEN 35.00 ELSE 40.00 END,
       ROUND(CASE g.strength_grade WHEN 'C25' THEN 25.00 WHEN 'C30' THEN 30.00 WHEN 'C35' THEN 35.00 ELSE 40.00 END * 0.75, 2),
       0.00,
       DATE_ADD(g.sampled_at, INTERVAL 7 DAY),
       CASE g.cube_no WHEN 'SK-2601' THEN '陈立明' WHEN 'SK-2602' THEN '赵永刚'
                      WHEN 'SK-2603' THEN '王建国' WHEN 'SK-2604' THEN '孙 磊' END
FROM cube_group g
JOIN strength_test_item i ON i.group_id = g.id AND i.age_days = 7
WHERE g.cube_no IN ('SK-2601','SK-2602','SK-2603','SK-2604');

-- 28d 正式结果：SK-2601 合格；SK-2602/SK-2603 不合格并触发复检
INSERT INTO strength_result (item_id, group_id, age_days, grade, strength_1, strength_2, strength_3,
                             avg_value, min_value, verdict, design_value, avg_threshold, min_threshold,
                             tested_at, operator_name)
SELECT i.id, g.id, 28, g.strength_grade,
       CASE g.cube_no WHEN 'SK-2601' THEN 41.20 WHEN 'SK-2602' THEN 32.10 WHEN 'SK-2603' THEN 41.80 END,
       CASE g.cube_no WHEN 'SK-2601' THEN 42.60 WHEN 'SK-2602' THEN 33.40 WHEN 'SK-2603' THEN 42.50 END,
       CASE g.cube_no WHEN 'SK-2601' THEN 43.10 WHEN 'SK-2602' THEN 31.60 WHEN 'SK-2603' THEN 40.90 END,
       CASE g.cube_no WHEN 'SK-2601' THEN 42.30 WHEN 'SK-2602' THEN 32.37 WHEN 'SK-2603' THEN 41.73 END,
       CASE g.cube_no WHEN 'SK-2601' THEN 41.20 WHEN 'SK-2602' THEN 31.60 WHEN 'SK-2603' THEN 40.90 END,
       CASE g.cube_no WHEN 'SK-2601' THEN 'QUALIFIED' ELSE 'UNQUALIFIED' END,
       CASE g.strength_grade WHEN 'C30' THEN 30.00 WHEN 'C35' THEN 35.00 ELSE 40.00 END,
       ROUND(CASE g.strength_grade WHEN 'C30' THEN 30.00 WHEN 'C35' THEN 35.00 ELSE 40.00 END * 1.15, 2),
       ROUND(CASE g.strength_grade WHEN 'C30' THEN 30.00 WHEN 'C35' THEN 35.00 ELSE 40.00 END * 0.95, 2),
       DATE_ADD(g.sampled_at, INTERVAL 28 DAY),
       CASE g.cube_no WHEN 'SK-2601' THEN '陈立明' WHEN 'SK-2602' THEN '赵永刚' WHEN 'SK-2603' THEN '王建国' END
FROM cube_group g
JOIN strength_test_item i ON i.group_id = g.id AND i.age_days = 28
WHERE g.cube_no IN ('SK-2601','SK-2602','SK-2603');

-- SK-2604：原 28d 不合格（平均不足），复检合格（终态由复检判定为合格）
INSERT INTO strength_result (item_id, group_id, age_days, grade, strength_1, strength_2, strength_3,
                             avg_value, min_value, verdict, design_value, avg_threshold, min_threshold,
                             tested_at, operator_name)
SELECT i.id, g.id, 28, 'C25', 26.20, 27.10, 25.80, 26.37, 25.80, 'UNQUALIFIED',
       25.00, 28.75, 23.75, DATE_ADD(g.sampled_at, INTERVAL 28 DAY), '孙 磊'
FROM cube_group g
JOIN strength_test_item i ON i.group_id = g.id AND i.age_days = 28
WHERE g.cube_no = 'SK-2604';

-- 回填检测项 result_id
UPDATE strength_test_item i
JOIN strength_result r ON r.item_id = i.id
SET i.result_id = r.id;

-- 复检任务：每组至多一条，引用原检测项与原结果；SK-2603 复检仍失败、SK-2604 复检合格
INSERT INTO recheck_task (recheck_no, group_id, original_item_id, original_result_id,
                          avg_1, avg_2, avg_3, avg_value, min_value, verdict,
                          design_value, avg_threshold, min_threshold,
                          status, created_at, created_by, tested_at, operator_name, remark)
SELECT 'FJ-2601', g.id, i28.id, r28.id,
       NULL, NULL, NULL, NULL, NULL, NULL, 30.00, 34.50, 28.50,
       'PENDING', r28.tested_at, r28.operator_name, NULL, NULL, '等待双倍取样复检'
FROM cube_group g
JOIN strength_test_item i28 ON i28.group_id = g.id AND i28.age_days = 28
JOIN strength_result r28 ON r28.id = i28.result_id
WHERE g.cube_no = 'SK-2602';

INSERT INTO recheck_task (recheck_no, group_id, original_item_id, original_result_id,
                          avg_1, avg_2, avg_3, avg_value, min_value, verdict,
                          design_value, avg_threshold, min_threshold,
                          status, created_at, created_by, tested_at, operator_name, remark)
SELECT 'FJ-2602', g.id, i28.id, r28.id,
       42.60, 43.40, 41.90, 42.63, 41.90, 'UNQUALIFIED', 40.00, 46.00, 38.00,
       'DONE', r28.tested_at, r28.operator_name, TIMESTAMP(CURDATE(), '09:30:00'), '李海涛',
       '双倍复检平均仍低于 46.00 MPa，按不合格处理并上报监理'
FROM cube_group g
JOIN strength_test_item i28 ON i28.group_id = g.id AND i28.age_days = 28
JOIN strength_result r28 ON r28.id = i28.result_id
WHERE g.cube_no = 'SK-2603';

INSERT INTO recheck_task (recheck_no, group_id, original_item_id, original_result_id,
                          avg_1, avg_2, avg_3, avg_value, min_value, verdict,
                          design_value, avg_threshold, min_threshold,
                          status, created_at, created_by, tested_at, operator_name, remark)
SELECT 'FJ-2603', g.id, i28.id, r28.id,
       29.60, 30.20, 29.10, 29.63, 29.10, 'QUALIFIED', 25.00, 28.75, 23.75,
       'DONE', r28.tested_at, r28.operator_name, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:00:00'),
       '周文斌', '双倍复检合格，试块组恢复合格'
FROM cube_group g
JOIN strength_test_item i28 ON i28.group_id = g.id AND i28.age_days = 28
JOIN strength_result r28 ON r28.id = i28.result_id
WHERE g.cube_no = 'SK-2604';
