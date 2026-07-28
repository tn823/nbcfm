package com.example.nbcfm;

/**
 * Cấu hình endpoint tập trung. Đổi BASE_URL theo server thực tế của nhà máy.
 */
public class Config {

    // Đổi IP/cổng theo server API của bạn
    public static final String BASE_URL = "http://192.168.1.13/test/arduino/";

    // Kiểm tra cập nhật APK
    public static final String CHECK_UPDATE = BASE_URL + "application/public/nbcfm/apk_info.json";

    // GET danh sách cho combobox
    public static final String GET_SEASONS      = BASE_URL + "getcfmseasons";
    public static final String GET_STAGES       = BASE_URL + "getcfmstages";
    // getcfmmodels?season=...
    public static final String GET_MODELS       = BASE_URL + "getcfmmodels";
    public static final String GET_STYLES       = BASE_URL + "getcfmstyles";

    // GET danh sách CFM theo điều kiện lọc
    // getcfmlist?season=..&stage=..&model=..
    public static final String GET_CFM_LIST     = BASE_URL + "getcfmlist";

    // GET kế hoạch hiện có của 1 CFM_ID
    // getcfmplan?cfmid=...
    public static final String GET_CFM_PLAN     = BASE_URL + "getcfmplan";

    // POST cập nhật kế hoạch
    public static final String SAVE_CFM_PLAN    = BASE_URL + "savecfmplan";

    // POST cập nhật sản xuất
    public static final String SAVE_CFM_PROD    = BASE_URL + "savecfmproduction";

    // POST cập nhật tất cả (Kế hoạch & Sản xuất hàng loạt)
    public static final String SAVE_CFM_ALL     = BASE_URL + "savecfmall";
    // POST cập nhật ghi đè tất cả (Slide 1 Revision 0723)
    public static final String SAVE_CFM_ALL_OVERWRITE = BASE_URL + "savecfmall_overwrite";

    // API Lịch sử sản xuất
    public static final String GET_PROD_HISTORY    = BASE_URL + "getcfmproduction_history";
    public static final String SAVE_PROD_HISTORY   = BASE_URL + "savecfmproduction_history";

    public static final String GET_TEAMS   = BASE_URL + "getcfmteams";
    public static final String GET_DEVS    = BASE_URL + "getcfmdevs";

}
