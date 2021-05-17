package com.winter.framework.base;

/**
 * Created by winter on 2021/3/14
 */
public class WebResult extends BaseResult {

    public WebResult(WebResultConstant webResultConstant, Object data) {
        super(webResultConstant.getCode(), webResultConstant.getMessage(), data);
    }
}
