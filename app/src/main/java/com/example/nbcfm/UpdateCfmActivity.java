package com.example.nbcfm;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Locale;

public class UpdateCfmActivity extends AppCompatActivity {

    private static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String cfmId;
    private String modelName = "";
    private String styleNo = "";
    private String selectedWorkDate; // YYYY-MM-DD
    private int requestedQtyLimit = 0;
    private String requestedQtyText = "0prs";

    private TextView tvTitle, tvModelStyle, tvWorkDate, tvRequestedQty, tvStatus;
    private LinearLayout btnWorkDate;
    private ProgressBar progressBar;
    private Button btnSave;

    private final String[] PROCS = {"ASS", "STT", "PRSTT", "CUT"};

    private final EditText[] etPlans = new EditText[4];
    private final EditText[] etProds = new EditText[4];
    private final TextView[] tvStatuses = new TextView[4];
    private final TextView[] tvAccumulated = new TextView[4];
    private final ImageView[] btnHistoryDetails = new ImageView[4];

    private final int[] otherPlanAcc = new int[4];
    private final int[] otherProdAcc = new int[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_cfm);

        // Nhận dữ liệu truyền qua từ MainActivity
        cfmId = getIntent().getStringExtra("CFM_ID");
        String m = getIntent().getStringExtra("MODEL_NAME");
        String s = getIntent().getStringExtra("STYLE_NO");
        modelName = m != null ? m : "";
        styleNo = s != null ? s : "";

        // Ngày mặc định là ngày hôm nay
        selectedWorkDate = SDF.format(Calendar.getInstance().getTime());

        tvTitle        = findViewById(R.id.tvTitle);
        tvModelStyle   = findViewById(R.id.tvModelStyle);
        btnWorkDate    = findViewById(R.id.btnWorkDate);
        tvWorkDate     = findViewById(R.id.tvWorkDate);
        tvRequestedQty = findViewById(R.id.tvRequestedQty);
        tvStatus       = findViewById(R.id.tvStatus);
        progressBar    = findViewById(R.id.progressBar);
        btnSave        = findViewById(R.id.btnSave);

        tvTitle.setText("Cập nhật tiến độ CFM - " + cfmId);
        tvModelStyle.setText(modelName + " / " + styleNo);
        tvWorkDate.setText(selectedWorkDate);

        // Ánh xạ quy trình ASS
        etPlans[0]             = findViewById(R.id.etAssPlan);
        etProds[0]             = findViewById(R.id.etAssProd);
        tvStatuses[0]          = findViewById(R.id.tvAssStatus);
        tvAccumulated[0]       = findViewById(R.id.tvAssAccumulated);
        btnHistoryDetails[0]   = findViewById(R.id.btnAssHistoryDetail);

        // Ánh xạ quy trình STT
        etPlans[1]             = findViewById(R.id.etSttPlan);
        etProds[1]             = findViewById(R.id.etSttProd);
        tvStatuses[1]          = findViewById(R.id.tvSttStatus);
        tvAccumulated[1]       = findViewById(R.id.tvSttAccumulated);
        btnHistoryDetails[1]   = findViewById(R.id.btnSttHistoryDetail);

        // Ánh xạ quy trình PRSTT
        etPlans[2]             = findViewById(R.id.etPrsttPlan);
        etProds[2]             = findViewById(R.id.etPrsttProd);
        tvStatuses[2]          = findViewById(R.id.tvPrsttStatus);
        tvAccumulated[2]       = findViewById(R.id.tvPrsttAccumulated);
        btnHistoryDetails[2]   = findViewById(R.id.btnPrsttHistoryDetail);

        // Ánh xạ quy trình CUT
        etPlans[3]             = findViewById(R.id.etCutPlan);
        etProds[3]             = findViewById(R.id.etCutProd);
        tvStatuses[3]          = findViewById(R.id.tvCutStatus);
        tvAccumulated[3]       = findViewById(R.id.tvCutAccumulated);
        btnHistoryDetails[3]   = findViewById(R.id.btnCutHistoryDetail);

        // Lắng nghe thay đổi trên các ô nhập để cập nhật live text
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateStatusText(index);
                }
                @Override public void afterTextChanged(Editable s) {}
            };
            etPlans[i].addTextChangedListener(watcher);
            etProds[i].addTextChangedListener(watcher);

            // Click vào nút Info [i] để xem chi tiết tích lũy theo ngày
            btnHistoryDetails[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showDailyHistoryDialog(PROCS[index]);
                }
            });
        }

        // Chọn ngày làm việc
        btnWorkDate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickWorkDate(); }
        });

        // Nút Save All
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (validateAndSave()) {
                    new SaveAllData().execute();
                }
            }
        });

        // Tải dữ liệu theo ngày
        new LoadAllData().execute();
    }

    private void updateStatusText(int index) {
        String planStr = etPlans[index].getText().toString().trim();
        String prodStr = etProds[index].getText().toString().trim();

        int plan = 0;
        int prod = 0;

        if (!planStr.isEmpty()) {
            try { plan = Integer.parseInt(planStr); } catch (Exception ignored) {}
        }
        if (!prodStr.isEmpty()) {
            try { prod = Integer.parseInt(prodStr); } catch (Exception ignored) {}
        }

        tvStatuses[index].setText("Hôm nay (KH/SX): " + plan + " / " + prod);

        int totalPlan = otherPlanAcc[index] + plan;
        int totalProd = otherProdAcc[index] + prod;
        tvAccumulated[index].setText("Tích lũy (KH/SX): " + totalPlan + " / " + totalProd);
    }

    private void pickWorkDate() {
        Calendar c = Calendar.getInstance();
        try { c.setTime(SDF.parse(selectedWorkDate)); } catch (Exception ignored) {}

        DatePickerDialog dlg = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                Calendar sel = Calendar.getInstance();
                sel.set(year, month, dayOfMonth);
                selectedWorkDate = SDF.format(sel.getTime());
                tvWorkDate.setText(selectedWorkDate);

                // Tải lại dữ liệu theo ngày được chọn
                new LoadAllData().execute();
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    private boolean validateAndSave() {
        for (int i = 0; i < 4; i++) {
            String planStr = etPlans[i].getText().toString().trim();
            String prodStr = etProds[i].getText().toString().trim();

            int p = 0;
            int pr = 0;

            if (!planStr.isEmpty()) {
                try {
                    p = Integer.parseInt(planStr);
                    if (p < 0) {
                        Toast.makeText(this, PROCS[i] + ": Kế hoạch không thể âm.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                } catch (Exception e) {
                    Toast.makeText(this, PROCS[i] + ": Kế hoạch không hợp lệ.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            if (!prodStr.isEmpty()) {
                try {
                    pr = Integer.parseInt(prodStr);
                    if (pr < 0) {
                        Toast.makeText(this, PROCS[i] + ": Sản lượng không thể âm.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                } catch (Exception e) {
                    Toast.makeText(this, PROCS[i] + ": Sản lượng không hợp lệ.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            int newTotalPlan = otherPlanAcc[i] + p;
            int newTotalProd = otherProdAcc[i] + pr;

            if (requestedQtyLimit > 0) {
                if (newTotalPlan > requestedQtyLimit) {
                    Toast.makeText(this, PROCS[i] + ": Tổng Kế hoạch tích lũy (" + newTotalPlan + ") vượt quá Requested QTY (" + requestedQtyLimit + ").", Toast.LENGTH_LONG).show();
                    return false;
                }
                if (newTotalProd > requestedQtyLimit) {
                    Toast.makeText(this, PROCS[i] + ": Tổng Sản lượng tích lũy (" + newTotalProd + ") vượt quá Requested QTY (" + requestedQtyLimit + ").", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        }
        return true;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
    }

    // ==================== Hộp thoại xem chi tiết Tích lũy theo ngày ====================
    private void showDailyHistoryDialog(final String process) {
        new LoadDailyHistoryTask(process).execute();
    }

    private class LoadDailyHistoryTask extends AsyncTask<Void, Void, String> {
        private final String process;

        public LoadDailyHistoryTask(String process) {
            this.process = process;
        }

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang tải chi tiết tích lũy cho " + process + "...");
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpHandler sh = new HttpHandler();
            String url = Config.GET_CFM_DAILY_HISTORY + "?cfmid=" + HttpHandler.enc(cfmId) + "&process=" + process;
            return sh.makeServiceCall(url);
        }

        @Override
        protected void onPostExecute(String result) {
            showLoading(false);
            if (result == null) {
                Toast.makeText(UpdateCfmActivity.this, "Lỗi kết nối server khi tải chi tiết.", Toast.LENGTH_SHORT).show();
                tvStatus.setText("Tải chi tiết thất bại.");
                return;
            }
            try {
                JSONArray arr = new JSONArray(result);
                tvStatus.setText("Đã tải chi tiết thành công.");
                displayDailyHistoryPopup(process, arr);
            } catch (Exception e) {
                Toast.makeText(UpdateCfmActivity.this, "Chưa có dữ liệu tích lũy.", Toast.LENGTH_SHORT).show();
                tvStatus.setText("Không có dữ liệu.");
            }
        }
    }

    private void displayDailyHistoryPopup(String process, JSONArray array) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        // 1. Header Banner
        LinearLayout headerCard = new LinearLayout(this);
        headerCard.setOrientation(LinearLayout.VERTICAL);
        headerCard.setPadding(24, 20, 24, 20);
        headerCard.setBackgroundColor(0xFF1E293B);

        TextView tvHeaderTitle = new TextView(this);
        tvHeaderTitle.setText("Chi tiết tích lũy - " + process);
        tvHeaderTitle.setTextColor(0xFFFFFFFF);
        tvHeaderTitle.setTextSize(16);
        tvHeaderTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvHeaderSub = new TextView(this);
        tvHeaderSub.setText("Model: " + modelName + "  |  Style: " + styleNo + "  |  Requested QTY: " + requestedQtyText);
        tvHeaderSub.setTextColor(0xFF94A3B8);
        tvHeaderSub.setTextSize(12);
        tvHeaderSub.setPadding(0, 4, 0, 0);

        headerCard.addView(tvHeaderTitle);
        headerCard.addView(tvHeaderSub);
        mainLayout.addView(headerCard);

        // 2. Stat Summary Cards
        int totalPlan = 0;
        int totalProd = 0;
        int len = array.length();

        for (int i = 0; i < len; i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                totalPlan += (int) obj.optDouble("PLAN_QTY", 0);
                totalProd += (int) obj.optDouble("PROD_QTY", 0);
            } catch (Exception ignored) {}
        }

        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.setPadding(20, 14, 20, 14);
        statRow.setBackgroundColor(0xFFF1F5F9);

        // Stat 1: Plan
        LinearLayout boxPlan = new LinearLayout(this);
        boxPlan.setOrientation(LinearLayout.VERTICAL);
        boxPlan.setPadding(14, 10, 14, 10);
        boxPlan.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lp1.setMargins(0, 0, 6, 0);
        boxPlan.setLayoutParams(lp1);

        TextView lblP = new TextView(this);
        lblP.setText("TỔNG KẾ HOẠCH");
        lblP.setTextSize(11);
        lblP.setTextColor(0xFF64748B);
        lblP.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView valP = new TextView(this);
        valP.setText(String.valueOf(totalPlan));
        valP.setTextSize(16);
        valP.setTextColor(0xFF0F172A);
        valP.setTypeface(null, android.graphics.Typeface.BOLD);

        boxPlan.addView(lblP);
        boxPlan.addView(valP);

        // Stat 2: Prod
        LinearLayout boxProd = new LinearLayout(this);
        boxProd.setOrientation(LinearLayout.VERTICAL);
        boxProd.setPadding(14, 10, 14, 10);
        boxProd.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lp2.setMargins(6, 0, 0, 0);
        boxProd.setLayoutParams(lp2);

        TextView lblPr = new TextView(this);
        lblPr.setText("TỔNG SẢN XUẤT");
        lblPr.setTextSize(11);
        lblPr.setTextColor(0xFF64748B);
        lblPr.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView valPr = new TextView(this);
        valPr.setText(totalProd + " / " + requestedQtyText);
        valPr.setTextSize(16);
        valPr.setTextColor(0xFF0284C7);
        valPr.setTypeface(null, android.graphics.Typeface.BOLD);

        boxProd.addView(lblPr);
        boxProd.addView(valPr);

        statRow.addView(boxPlan);
        statRow.addView(boxProd);
        mainLayout.addView(statRow);

        // 3. Scrollable Content List
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 12, 16, 16);
        scrollView.addView(container);

        if (len == 0) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Chưa có lượt nhập kế hoạch / sản lượng nào.");
            tvEmpty.setPadding(0, 32, 0, 32);
            tvEmpty.setTextColor(0xFF64748B);
            tvEmpty.setGravity(Gravity.CENTER);
            container.addView(tvEmpty);
        } else {
            // Header Row
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setPadding(12, 10, 12, 10);
            headerRow.setBackgroundColor(0xFFE2E8F0);

            TextView hDate = new TextView(this);
            hDate.setText("Ngày");
            hDate.setTypeface(null, android.graphics.Typeface.BOLD);
            hDate.setTextSize(12);
            hDate.setTextColor(0xFF334155);
            hDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

            TextView hTime = new TextView(this);
            hTime.setText("Thời gian");
            hTime.setTypeface(null, android.graphics.Typeface.BOLD);
            hTime.setTextSize(12);
            hTime.setTextColor(0xFF334155);
            hTime.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f));

            TextView hPlan = new TextView(this);
            hPlan.setText("Kế hoạch");
            hPlan.setTypeface(null, android.graphics.Typeface.BOLD);
            hPlan.setTextSize(12);
            hPlan.setTextColor(0xFF334155);
            hPlan.setGravity(Gravity.CENTER);
            hPlan.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));

            TextView hProd = new TextView(this);
            hProd.setText("Sản xuất");
            hProd.setTypeface(null, android.graphics.Typeface.BOLD);
            hProd.setTextSize(12);
            hProd.setTextColor(0xFF334155);
            hProd.setGravity(Gravity.END);
            hProd.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));

            headerRow.addView(hDate);
            headerRow.addView(hTime);
            headerRow.addView(hPlan);
            headerRow.addView(hProd);
            container.addView(headerRow);

            for (int i = 0; i < len; i++) {
                try {
                    JSONObject obj = array.getJSONObject(i);
                    String workDate = obj.optString("WORK_DATE", "");
                    String timeStr = obj.optString("TIME_STR", "--:--");
                    int plan = (int) obj.optDouble("PLAN_QTY", 0);
                    int prod = (int) obj.optDouble("PROD_QTY", 0);

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(12, 12, 12, 12);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    if (i % 2 == 1) {
                        row.setBackgroundColor(0xFFF8FAFC);
                    } else {
                        row.setBackgroundColor(0xFFFFFFFF);
                    }

                    TextView rDate = new TextView(this);
                    rDate.setText(workDate);
                    rDate.setTextSize(13);
                    rDate.setTextColor(0xFF1E293B);
                    rDate.setTypeface(null, android.graphics.Typeface.BOLD);
                    rDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

                    TextView rTime = new TextView(this);
                    rTime.setText(timeStr.length() > 8 ? timeStr.substring(timeStr.indexOf(" ") + 1) : timeStr);
                    rTime.setTextSize(12);
                    rTime.setTextColor(0xFF64748B);
                    rTime.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f));

                    TextView rPlan = new TextView(this);
                    rPlan.setText(String.valueOf(plan));
                    rPlan.setTextSize(13);
                    rPlan.setTextColor(0xFF475569);
                    rPlan.setGravity(Gravity.CENTER);
                    rPlan.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));

                    TextView rProd = new TextView(this);
                    rProd.setText(String.valueOf(prod));
                    rProd.setTextSize(13);
                    rProd.setTextColor(prod > 0 ? 0xFF0284C7 : 0xFF94A3B8);
                    rProd.setTypeface(null, prod > 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                    rProd.setGravity(Gravity.END);
                    rProd.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));

                    row.addView(rDate);
                    row.addView(rTime);
                    row.addView(rPlan);
                    row.addView(rProd);
                    container.addView(row);
                } catch (Exception ignored) {}
            }
        }

        mainLayout.addView(scrollView);

        builder.setView(mainLayout);
        builder.setPositiveButton("ĐÓNG", null);
        builder.show();
    }

    // ==================== AsyncTask Tải dữ liệu tiến độ ngày chọn ====================
    private class LoadAllData extends AsyncTask<Void, Void, JSONObject> {
        private String error = null;

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang tải dữ liệu tiến độ cho ngày " + selectedWorkDate + "...");
        }

        @Override
        protected JSONObject doInBackground(Void... v) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_CFM_DAILY_PROGRESS + "?cfmid=" + HttpHandler.enc(cfmId) + "&workdate=" + HttpHandler.enc(selectedWorkDate);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr == null) {
                    error = "Không kết nối được server.";
                    return null;
                }
                jsonStr = jsonStr.trim();
                if (jsonStr.isEmpty() || jsonStr.equals("[]")) return null;
                if (jsonStr.startsWith("[")) {
                    org.json.JSONArray a = new org.json.JSONArray(jsonStr);
                    if (a.length() == 0) return null;
                    return a.getJSONObject(0);
                }
                return new JSONObject(jsonStr);
            } catch (Exception e) {
                error = "Lỗi tải dữ liệu: " + e.toString();
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject o) {
            showLoading(false);
            if (error != null) {
                tvStatus.setText(error);
                return;
            }

            if (o != null) {
                requestedQtyText = o.optString("REQUESTED_QTY", "0");
                tvRequestedQty.setText("Requested QTY: " + requestedQtyText);
                try {
                    requestedQtyLimit = Integer.parseInt(requestedQtyText.replaceAll("[^0-9]", ""));
                } catch (Exception e) {
                    requestedQtyLimit = 0;
                }

                for (int i = 0; i < 4; i++) {
                    String p = PROCS[i];
                    double planVal     = o.optDouble(p + "_PLAN", 0);
                    double prodVal     = o.optDouble(p + "_PROD", 0);
                    double planAccVal  = o.optDouble(p + "_PLAN_ACC", 0);
                    double prodAccVal  = o.optDouble(p + "_ACCUMULATED", 0);

                    otherPlanAcc[i] = (int) Math.max(0, planAccVal - planVal);
                    otherProdAcc[i] = (int) Math.max(0, prodAccVal - prodVal);

                    etPlans[i].setText(planVal > 0 ? String.valueOf((int) planVal) : "");
                    etProds[i].setText(prodVal > 0 ? String.valueOf((int) prodVal) : "");
                    updateStatusText(i);
                }
            } else {
                tvRequestedQty.setText("Requested QTY: 0prs");
                requestedQtyLimit = 0;
                for (int i = 0; i < 4; i++) {
                    otherPlanAcc[i] = 0;
                    otherProdAcc[i] = 0;
                    etPlans[i].setText("");
                    etProds[i].setText("");
                    updateStatusText(i);
                }
            }

            tvStatus.setText("Đã tải dữ liệu thành công cho ngày " + selectedWorkDate);
        }
    }

    // ==================== AsyncTask Lưu thông tin ====================
    private class SaveAllData extends AsyncTask<Void, Void, String> {
        private String error = null;

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang lưu tiến độ ngày " + selectedWorkDate + "...");
        }

        @Override
        protected String doInBackground(Void... v) {
            HttpHandler sh = new HttpHandler();
            try {
                JSONObject body = new JSONObject();
                body.put("CFM_ID", cfmId);
                body.put("WORK_DATE", selectedWorkDate.replace("-", "")); // YYYYMMDD
                String localIp = getLocalIpAddress();
                if (localIp != null) {
                    body.put("IP", localIp);
                }

                for (int i = 0; i < 4; i++) {
                    String planStr = etPlans[i].getText().toString().trim();
                    String prodStr = etProds[i].getText().toString().trim();

                    body.put(PROCS[i] + "_PLAN", planStr.isEmpty() ? 0 : Integer.parseInt(planStr));
                    body.put(PROCS[i] + "_PROD", prodStr.isEmpty() ? 0 : Integer.parseInt(prodStr));
                }

                String resp = sh.makePostCall(Config.SAVE_CFM_DAILY_PROGRESS, body.toString());
                if (resp == null) {
                    return "Không kết nối được server.";
                }

                JSONObject respObj = new JSONObject(resp);
                if (!respObj.optString("result", "").equalsIgnoreCase("OK")) {
                    return respObj.optString("msg", "Lưu thất bại.");
                }

                return "OK";
            } catch (Exception e) {
                error = "Lỗi hệ thống khi lưu: " + e.toString();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            showLoading(false);
            if (error != null) {
                Toast.makeText(UpdateCfmActivity.this, error, Toast.LENGTH_LONG).show();
                tvStatus.setText(error);
                return;
            }

            if ("OK".equals(result)) {
                Toast.makeText(UpdateCfmActivity.this, "Đã lưu thành công tiến độ ngày " + selectedWorkDate + "!", Toast.LENGTH_LONG).show();
                tvStatus.setText("Lưu thành công.");
                setResult(RESULT_OK);
                new LoadAllData().execute();
            } else {
                Toast.makeText(UpdateCfmActivity.this, result, Toast.LENGTH_LONG).show();
                tvStatus.setText(result);
            }
        }
    }
}
