package com.feyfey.constant;

/**
 * 接口响应类型。
 */
public enum JsonReturnResultTypeEnum {
    SUCCESS("请求成功"),
    FAILURE("请求失败");
    private String description;

    private JsonReturnResultTypeEnum(String description) {
        this.setDescription(description);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
