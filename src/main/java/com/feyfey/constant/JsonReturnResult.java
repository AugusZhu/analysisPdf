package com.feyfey.constant;

public class JsonReturnResult {
    private JsonReturnResultTypeEnum returnType;
    private String content;

    public JsonReturnResult(JsonReturnResultTypeEnum returnType, String content) {
        super();
        this.returnType = returnType;
        this.content = content;
    }

    public JsonReturnResultTypeEnum getReturnType() {
        return returnType;
    }

    public void setReturnType(JsonReturnResultTypeEnum returnType) {
        this.returnType = returnType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public static JsonReturnResult success(String content) {
        return new JsonReturnResult(JsonReturnResultTypeEnum.SUCCESS, content);
    }

    public static JsonReturnResult fail(String content) {
        return new JsonReturnResult(JsonReturnResultTypeEnum.FAILURE, content);
    }


}
