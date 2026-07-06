package com.chenxy.demo.sql.model;

/**
 * 条件校验结果
 */
public class ValidationResult {

    private ConditionType conditionType;
    private String message;
    /** 优化/重排后的条件表达式 */
    private ConditionNode optimizedCondition;

    public ValidationResult() {
    }

    public ValidationResult(ConditionType conditionType, String message, ConditionNode optimizedCondition) {
        this.conditionType = conditionType;
        this.message = message;
        this.optimizedCondition = optimizedCondition;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ConditionNode getOptimizedCondition() {
        return optimizedCondition;
    }

    public void setOptimizedCondition(ConditionNode optimizedCondition) {
        this.optimizedCondition = optimizedCondition;
    }

    public boolean isValid() {
        return conditionType == ConditionType.SATISFIABLE || conditionType == ConditionType.UNKNOWN;
    }
}
