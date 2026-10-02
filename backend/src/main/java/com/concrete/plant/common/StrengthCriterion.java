package com.concrete.plant.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 抗压强度判定的<strong>唯一计算来源</strong>：数据库存储、后端落库与前端展示都不得另算阈值或改写结论。
 *
 * <p>判定口径（GB/T 50107-2010 非统计方法）：</p>
 * <ul>
 *   <li>28d 正式评定：三块平均值 ≥ 1.15·fcu,k 且最小值 ≥ 0.95·fcu,k，任一不满足即不合格，触发唯一复检；</li>
 *   <li>7d 只做早龄期趋势参考：平均值 ≥ 0.75·fcu,k 为趋势正常，否则趋势偏低，<b>不改变试块组最终质量状态</b>。</li>
 * </ul>
 * 平均值四舍五入保留 2 位（原始读数保留原样，永不覆盖）。
 */
public final class StrengthCriterion {

    /** 7 天 */
    public static final int AGE_7 = 7;
    /** 28 天 */
    public static final int AGE_28 = 28;

    private StrengthCriterion() {
    }

    /** 判定结果（不可变） */
    public record Judgment(BigDecimal avgValue, BigDecimal minValue, String verdict,
                          BigDecimal designValue, BigDecimal avgThreshold, BigDecimal minThreshold) {

        /** 28d 是否合格 */
        public boolean qualified() {
            return "QUALIFIED".equals(verdict);
        }

        /** 是否为 7d 趋势判定 */
        public boolean trend() {
            return "TREND_OK".equals(verdict) || "TREND_LOW".equals(verdict);
        }
    }

    /**
     * 按强度等级与龄期对三块试件读数做统一判定。
     *
     * @param grade     强度等级 C25/C30/C35/C40
     * @param ageDays   龄期 7 / 28
     * @param readings  三块抗压强度（MPa），必须恰好三块且都为正数
     */
    public static Judgment evaluate(String grade, int ageDays, List<BigDecimal> readings) {
        if (!Dict.isValidGrade(grade)) {
            throw new BizException("未知的强度等级：" + grade);
        }
        if (readings == null || readings.size() != Dict.CUBE_SPECIMEN_COUNT) {
            throw new BizException("每个检测项必须录入三块试件的抗压强度");
        }
        BigDecimal design = Dict.gradeDesignStrength(grade);
        BigDecimal min = readings.get(0);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal reading : readings) {
            if (reading == null || reading.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("抗压强度读数必须为正数");
            }
            sum = sum.add(reading);
            if (reading.compareTo(min) < 0) {
                min = reading;
            }
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(readings.size()), 2, RoundingMode.HALF_UP);

        if (ageDays == AGE_7) {
            BigDecimal trendLine = scale(design.multiply(Dict.R7_TREND_FACTOR));
            String verdict = avg.compareTo(trendLine) >= 0 ? "TREND_OK" : "TREND_LOW";
            return new Judgment(avg, min, verdict, design, trendLine, BigDecimal.ZERO);
        }
        if (ageDays == AGE_28) {
            BigDecimal avgLine = scale(design.multiply(Dict.R28_AVG_FACTOR));
            BigDecimal minLine = scale(design.multiply(Dict.R28_MIN_FACTOR));
            String verdict = avg.compareTo(avgLine) >= 0 && min.compareTo(minLine) >= 0
                    ? "QUALIFIED" : "UNQUALIFIED";
            return new Judgment(avg, min, verdict, design, avgLine, minLine);
        }
        throw new BizException("只支持 7 天与 28 天龄期检测，收到龄期：" + ageDays);
    }

    /** 供页面展示门槛：28d 评定门槛 */
    public static Judgment thresholdOf28(String grade) {
        BigDecimal design = Dict.gradeDesignStrength(grade);
        return new Judgment(null, null, null, design,
                scale(design.multiply(Dict.R28_AVG_FACTOR)),
                scale(design.multiply(Dict.R28_MIN_FACTOR)));
    }

    /** 供页面展示：7d 趋势参考线 */
    public static BigDecimal trendLineOf7(String grade) {
        return scale(Dict.gradeDesignStrength(grade).multiply(Dict.R7_TREND_FACTOR));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
