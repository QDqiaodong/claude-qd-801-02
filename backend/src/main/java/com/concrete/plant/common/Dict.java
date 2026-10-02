package com.concrete.plant.common;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 搅拌站词典与业务规则参数表：强度等级、材料、主机/罐车/任务单状态、配合比参考用量等。
 * 规则集中在此，便于出题时被要求调整或扩展。
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 强度等级 ----------------
    public static final List<String> GRADES = List.of("C25", "C30", "C35", "C40");

    /** 各强度等级水胶比上限 */
    private static final Map<String, BigDecimal> GRADE_MAX_WB = Map.of(
            "C25", BigDecimal.valueOf(0.60),
            "C30", BigDecimal.valueOf(0.55),
            "C35", BigDecimal.valueOf(0.50),
            "C40", BigDecimal.valueOf(0.45));

    /** 各强度等级胶凝材料（水泥+粉煤灰+矿粉）最小用量 kg/m³ */
    private static final Map<String, BigDecimal> GRADE_MIN_BINDER = Map.of(
            "C25", BigDecimal.valueOf(300),
            "C30", BigDecimal.valueOf(340),
            "C35", BigDecimal.valueOf(380),
            "C40", BigDecimal.valueOf(420));

    // ---------------- 试块抗压强度判定（数据库/后端/前端共用的唯一判定来源） ----------------
    /** 立方体抗压强度标准值 fcu,k（MPa），即各强度等级的设计值 */
    private static final Map<String, BigDecimal> GRADE_DESIGN_STRENGTH = Map.of(
            "C25", BigDecimal.valueOf(25),
            "C30", BigDecimal.valueOf(30),
            "C35", BigDecimal.valueOf(35),
            "C40", BigDecimal.valueOf(40));

    /** 28d 评定：三块平均值不低于 1.15·fcu,k（非统计方法，GB/T 50107-2010 第 5.2.2 条） */
    public static final BigDecimal R28_AVG_FACTOR = BigDecimal.valueOf(1.15);

    /** 28d 评定：三块最小值不低于 0.95·fcu,k */
    public static final BigDecimal R28_MIN_FACTOR = BigDecimal.valueOf(0.95);

    /** 7d 早龄期趋势线：平均值达到 0.75·fcu,k 视为趋势正常（仅趋势提示，不改变最终质量状态） */
    public static final BigDecimal R7_TREND_FACTOR = BigDecimal.valueOf(0.75);

    /** 一组试块的抗压试件数量 */
    public static final int CUBE_SPECIMEN_COUNT = 3;

    // ---------------- 配合比材料 ----------------
    public static final List<String> MATERIALS = List.of(
            "CEMENT", "FLYASH", "SLAG", "SAND", "STONE", "WATER", "ADMIXTURE");

    private static final Map<String, String> MATERIAL_NAME = Map.of(
            "CEMENT", "水泥 P·O42.5",
            "FLYASH", "粉煤灰 Ⅱ 级",
            "SLAG", "矿粉 S95",
            "SAND", "机制砂 中砂",
            "STONE", "碎石 5-25mm",
            "WATER", "拌合用水",
            "ADMIXTURE", "聚羧酸外加剂");

    /** 计入胶凝材料的三种（算水胶比分母） */
    private static final Set<String> BINDER_MATERIALS = Set.of("CEMENT", "FLYASH", "SLAG");

    /** 需要筒仓供货的五种材料 */
    private static final Set<String> SILO_MATERIALS = Set.of("CEMENT", "FLYASH", "SLAG", "SAND", "STONE");

    /** 各强度等级参考配合比（kg/m³） */
    private static final Map<String, Map<String, BigDecimal>> REFERENCE_MIX = Map.of(
            "C25", Map.of("CEMENT", bd(230), "FLYASH", bd(70), "SLAG", bd(50), "SAND", bd(780),
                    "STONE", bd(1050), "WATER", bd(180), "ADMIXTURE", bd(6.80)),
            "C30", Map.of("CEMENT", bd(270), "FLYASH", bd(70), "SLAG", bd(60), "SAND", bd(760),
                    "STONE", bd(1040), "WATER", bd(172), "ADMIXTURE", bd(7.80)),
            "C35", Map.of("CEMENT", bd(310), "FLYASH", bd(60), "SLAG", bd(70), "SAND", bd(730),
                    "STONE", bd(1030), "WATER", bd(165), "ADMIXTURE", bd(9.20)),
            "C40", Map.of("CEMENT", bd(350), "FLYASH", bd(50), "SLAG", bd(70), "SAND", bd(700),
                    "STONE", bd(1020), "WATER", bd(158), "ADMIXTURE", bd(10.80)));

    // ---------------- 主机状态 ----------------
    private static final Map<String, String> MIXER_STATUS = Map.of(
            "RUNNING", "运行中",
            "IDLE", "待机",
            "MAINTENANCE", "检修",
            "FAULT", "故障");

    // ---------------- 任务单状态 ----------------
    private static final Map<String, String> ORDER_STATUS = Map.of(
            "PENDING", "待排产",
            "PRODUCING", "生产中",
            "DONE", "已完成");

    // ---------------- 罐车状态 ----------------
    private static final Map<String, String> TRUCK_STATUS = Map.of(
            "STANDBY", "待命",
            "LOADING", "装料",
            "ON_ROAD", "在途",
            "UNLOADING", "卸料",
            "WASHING", "清洗");

    /** 罐车状态推进顺序 */
    public static final List<String> TRUCK_FLOW = List.of("STANDBY", "LOADING", "ON_ROAD", "UNLOADING", "WASHING");

    // ---------------- 发车单状态 ----------------
    private static final Map<String, String> TICKET_STATUS = Map.of(
            "LOADED", "已装车",
            "DEPARTED", "已出厂",
            "ARRIVED", "已到工地",
            "SIGNED", "已签收");

    // ---------------- 补料申请状态 ----------------
    private static final Map<String, String> REPLENISH_STATUS = Map.of(
            "PENDING", "待审批",
            "APPROVED", "已通过",
            "REJECTED", "已驳回");

    // ---------------- 试块养护方式 ----------------
    private static final Map<String, String> CURING_METHOD = Map.of(
            "STANDARD", "标准养护",
            "ONSITE", "同条件养护");

    // ---------------- 试块组状态 ----------------
    private static final Map<String, String> CUBE_GROUP_STATUS = Map.of(
            "TESTING", "检测中",
            "QUALIFIED", "已合格",
            "RECHECK", "待复检",
            "UNQUALIFIED", "不合格");

    // ---------------- 检测项持久状态（待检/逾期/未到期/已合格 由服务端按时钟派生） ----------------
    private static final Map<String, String> TEST_ITEM_STATUS = Map.of(
            "PENDING", "待检",
            "DONE", "已检测",
            "RECHECK", "待复检");

    // ---------------- 检测项页面派生状态 ----------------
    private static final Map<String, String> ITEM_FLOW_STATUS = Map.of(
            "NOT_DUE", "未到期",
            "DUE", "待检",
            "OVERDUE", "逾期",
            "QUALIFIED", "已合格",
            "RECHECK", "待复检",
            "UNQUALIFIED", "不合格",
            "TREND_OK", "7d 趋势正常",
            "TREND_LOW", "7d 趋势偏低");

    // ---------------- 检测结果判定 ----------------
    private static final Map<String, String> RESULT_VERDICT = Map.of(
            "QUALIFIED", "合格",
            "UNQUALIFIED", "不合格",
            "TREND_OK", "趋势正常",
            "TREND_LOW", "趋势偏低");

    // ---------------- 复检任务状态 ----------------
    private static final Map<String, String> RECHECK_STATUS = Map.of(
            "PENDING", "待复检",
            "DONE", "已复检");

    // ---------------- 筒仓材料 ----------------
    private static final Map<String, String> SILO_MATERIAL_NAME = Map.of(
            "CEMENT", "水泥",
            "FLYASH", "粉煤灰",
            "SLAG", "矿粉",
            "SAND", "砂",
            "STONE", "石");

    // ---------------- 其它业务参数 ----------------
    public static final List<String> SLUMP_OPTIONS = List.of("120±30mm", "160±30mm", "180±30mm", "200±30mm");

    /** 单张任务单计划方量上限 */
    public static final BigDecimal MAX_PLAN_VOLUME = BigDecimal.valueOf(3000);

    /** 单辆罐车一次装车方量上限（m³） */
    public static final BigDecimal MAX_LOAD_VOLUME = BigDecimal.valueOf(20);

    /** 单个筒仓单次补料上限（吨） */
    public static final BigDecimal MAX_REPLENISH_TON = BigDecimal.valueOf(200);

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    // ---------------- 取值方法 ----------------

    public static boolean isValidGrade(String grade) {
        return grade != null && GRADE_MAX_WB.containsKey(grade);
    }

    public static BigDecimal gradeMaxWaterBinderRatio(String grade) {
        return GRADE_MAX_WB.getOrDefault(grade, BigDecimal.valueOf(0.60));
    }

    public static BigDecimal gradeMinBinder(String grade) {
        return GRADE_MIN_BINDER.getOrDefault(grade, BigDecimal.valueOf(300));
    }

    /** 强度等级设计值 fcu,k（MPa）；未知等级返回 0，由调用方决定是否报错 */
    public static BigDecimal gradeDesignStrength(String grade) {
        return GRADE_DESIGN_STRENGTH.getOrDefault(grade, BigDecimal.ZERO);
    }

    public static boolean isValidCuringMethod(String method) {
        return method != null && CURING_METHOD.containsKey(method);
    }

    public static String curingMethodName(String method) {
        return method == null ? "" : CURING_METHOD.getOrDefault(method, method);
    }

    public static boolean isValidCubeGroupStatus(String status) {
        return status != null && CUBE_GROUP_STATUS.containsKey(status);
    }

    public static String cubeGroupStatusName(String status) {
        return status == null ? "" : CUBE_GROUP_STATUS.getOrDefault(status, status);
    }

    public static String testItemStatusName(String status) {
        return status == null ? "" : TEST_ITEM_STATUS.getOrDefault(status, status);
    }

    public static String itemFlowStatusName(String flowStatus) {
        return flowStatus == null ? "" : ITEM_FLOW_STATUS.getOrDefault(flowStatus, flowStatus);
    }

    public static String verdictName(String verdict) {
        return verdict == null ? "" : RESULT_VERDICT.getOrDefault(verdict, verdict);
    }

    public static String recheckStatusName(String status) {
        return status == null ? "" : RECHECK_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidMaterial(String material) {
        return material != null && MATERIAL_NAME.containsKey(material);
    }

    public static String materialName(String material) {
        return material == null ? "" : MATERIAL_NAME.getOrDefault(material, material);
    }

    public static boolean isBinder(String material) {
        return material != null && BINDER_MATERIALS.contains(material);
    }

    public static boolean isSiloMaterial(String material) {
        return material != null && SILO_MATERIALS.contains(material);
    }

    public static String siloMaterialName(String material) {
        return material == null ? "" : SILO_MATERIAL_NAME.getOrDefault(material, material);
    }

    /** 按强度等级生成一套参考配合比（有序，便于页面按固定顺序展示） */
    public static Map<String, BigDecimal> referenceMix(String grade) {
        Map<String, BigDecimal> template = REFERENCE_MIX.getOrDefault(grade, REFERENCE_MIX.get("C30"));
        Map<String, BigDecimal> mix = new LinkedHashMap<>();
        for (String material : MATERIALS) {
            mix.put(material, template.getOrDefault(material, BigDecimal.ZERO));
        }
        return mix;
    }

    public static boolean isValidMixerStatus(String status) {
        return status != null && MIXER_STATUS.containsKey(status);
    }

    public static String mixerStatusName(String status) {
        return status == null ? "" : MIXER_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidOrderStatus(String status) {
        return status != null && ORDER_STATUS.containsKey(status);
    }

    public static String orderStatusName(String status) {
        return status == null ? "" : ORDER_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidTruckStatus(String status) {
        return status != null && TRUCK_STATUS.containsKey(status);
    }

    public static String truckStatusName(String status) {
        return status == null ? "" : TRUCK_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidTicketStatus(String status) {
        return status != null && TICKET_STATUS.containsKey(status);
    }

    public static String ticketStatusName(String status) {
        return status == null ? "" : TICKET_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidReplenishStatus(String status) {
        return status != null && REPLENISH_STATUS.containsKey(status);
    }

    public static String replenishStatusName(String status) {
        return status == null ? "" : REPLENISH_STATUS.getOrDefault(status, status);
    }
}
