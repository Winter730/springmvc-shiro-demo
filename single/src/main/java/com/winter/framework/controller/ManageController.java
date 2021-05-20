package com.winter.framework.controller;


import com.winter.framework.base.BaseController;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
 * Created by winter on 2021/3/11
 */
@Controller
@RequestMapping("/manage")
public class ManageController extends BaseController {
    @RequestMapping(value = "/index", method = RequestMethod.GET)
    public String index(ModelMap modelMap) {
        return "/manage/index";
    }

    @RequiresPermissions("sso:permission2:read")
    @RequestMapping(value = "/permission", method = RequestMethod.GET)
    public String permission(ModelMap modelMap) {
        return "/manage/permission";
    }


}
