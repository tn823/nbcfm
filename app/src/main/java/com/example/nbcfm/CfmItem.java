package com.example.nbcfm;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * Model cho 1 CFM record, ánh xạ đúng các cột trong bảng.
 */
public class CfmItem implements Serializable {

    public String cfmId        = "";
    public String brandCode    = "";
    public String season       = "";
    public String modelName    = "";
    public String styleNo      = "";
    public String lastName     = "";
    public String moldNew      = "";
    public String moldExist    = "";
    public String gender       = "";
    public String vsDeveloper  = "";
    public String siteDeveloper= "";
    public String currentStage = "";
    public String tpDate       = "";
    public String specIssue    = "";
    public String mtlArrived   = "";
    public String qtyWorking   = "";
    public String qtyShipping  = "";
    public int hasPlan = 0;

    public static CfmItem fromJson(JSONObject c) {
        CfmItem it = new CfmItem();
        it.cfmId         = opt(c, "CFM_ID");
        it.brandCode     = opt(c, "BRAND_CODE");
        it.season        = opt(c, "SEASON");
        it.modelName     = opt(c, "MODEL_NAME");
        it.styleNo       = opt(c, "STYLE_NO");
        it.lastName      = opt(c, "LAST_NAME");
        it.moldNew       = opt(c, "MOLD_NEW");
        it.moldExist     = opt(c, "MOLD_EXIST");
        it.gender        = opt(c, "GENDER");
        it.vsDeveloper   = opt(c, "VS_DEVELOPER");
        it.siteDeveloper = opt(c, "SITE_DEVELOPER");
        it.currentStage  = opt(c, "CURRENT_STAGE");
        it.tpDate        = opt(c, "TP_DATE");
        it.specIssue     = opt(c, "SPEC_ISSUE");
        it.mtlArrived    = opt(c, "MTL_ARRIVED");
        it.qtyWorking    = opt(c, "QTY_WORKING");
        it.qtyShipping   = opt(c, "QTY_SHIPPING");
        it.hasPlan = c.optInt("HAS_PLAN",0);
        return it;
    }

    private static String opt(JSONObject c, String key) {
        String v = c.optString(key, "");
        if (v == null || v.equalsIgnoreCase("null")) return "";
        return v;
    }
}
