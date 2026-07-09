package com.example.nbcfm;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class UpdateCfmActivity extends AppCompatActivity {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String cfmId;
    private String qtyWorking = "0";
    private JSONObject planData = null;

    private TextView tvTitle, tvStatus;
    private ProgressBar progressBar;
    private Button btnSave;
    private final ImageButton[] btnEdits = new ImageButton[4]; // Nút edit của [ASS, STT, PRSTT, CUT]
    private final android.widget.CheckBox[] cbMakeups = new android.widget.CheckBox[4]; // Checkbox làm bù


    private final String[] PROCS = {"ASS", "STT", "PRSTT", "CUT"};

    // Mảng lưu trữ tham chiếu đến các UI Component để dễ quản lý theo vòng lặp
    private final TextView[][] dateFields = new TextView[4][2]; // [proc][0=start, 1=end]
    private final TextView[] tvProgresses = new TextView[4];
    private final EditText[] etQtys = new EditText[4];

    private final String[][] initialDates = new String[4][2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_cfm);

        // Nhận dữ liệu truyền qua từ MainActivity
        cfmId = getIntent().getStringExtra("CFM_ID");
        String model = getIntent().getStringExtra("MODEL_NAME");
        String style = getIntent().getStringExtra("STYLE_NO");
        String qtyWorkingExtra = getIntent().getStringExtra("QTY_WORKING");
        if (qtyWorkingExtra != null && !qtyWorkingExtra.trim().isEmpty()) {
            qtyWorking = qtyWorkingExtra.trim();
        }

        tvTitle     = findViewById(R.id.tvTitle);
        tvStatus    = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        btnSave     = findViewById(R.id.btnSave);

        tvTitle.setText("Cập nhật tiến độ CFM - " + cfmId + "\n" + (model == null ? "" : model) + " / " + (style == null ? "" : style));

        // Ánh xạ các Views của quy trình ASS
        dateFields[0][0] = findViewById(R.id.tvAssStart);
        dateFields[0][1] = findViewById(R.id.tvAssEnd);
        tvProgresses[0]  = findViewById(R.id.tvAssProgress);
        etQtys[0]        = findViewById(R.id.etAssQty);
        cbMakeups[0]     = findViewById(R.id.cbAssMakeup);

        // Ánh xạ các Views của quy trình STT
        dateFields[1][0] = findViewById(R.id.tvSttStart);
        dateFields[1][1] = findViewById(R.id.tvSttEnd);
        tvProgresses[1]  = findViewById(R.id.tvSttProgress);
        etQtys[1]        = findViewById(R.id.etSttQty);
        cbMakeups[1]     = findViewById(R.id.cbSttMakeup);

        // Ánh xạ các Views của quy trình PRSTT
        dateFields[2][0] = findViewById(R.id.tvPrsttStart);
        dateFields[2][1] = findViewById(R.id.tvPrsttEnd);
        tvProgresses[2]  = findViewById(R.id.tvPrsttProgress);
        etQtys[2]        = findViewById(R.id.etPrsttQty);
        cbMakeups[2]     = findViewById(R.id.cbPrsttMakeup);

        // Ánh xạ các Views của quy trình CUT
        dateFields[3][0] = findViewById(R.id.tvCutStart);
        dateFields[3][1] = findViewById(R.id.tvCutEnd);
        tvProgresses[3]  = findViewById(R.id.tvCutProgress);
        etQtys[3]        = findViewById(R.id.etCutQty);
        cbMakeups[3]     = findViewById(R.id.cbCutMakeup);

        // Gắn sự kiện chọn ngày khi Click vào TextView ngày bắt đầu / kết thúc
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                final TextView tv = dateFields[i][j];
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { pickDate(tv); }
                });
            }
        }

        // Ánh xạ các nút Edit sản lượng để mở lịch sử
        btnEdits[0] = findViewById(R.id.btnAssEdit);
        btnEdits[1] = findViewById(R.id.btnSttEdit);
        btnEdits[2] = findViewById(R.id.btnPrsttEdit);
        btnEdits[3] = findViewById(R.id.btnCutEdit);

        for (int i = 0; i < 4; i++) {
            final String proc = PROCS[i];
            btnEdits[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showHistoryDialog(proc);
                }
            });
        }

        // Sự kiện khi nhấn nút lưu tất cả
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (validateAndSave()) {
                    new SaveAllData().execute();
                }
            }
        });

        // Bắt đầu tải dữ liệu ban đầu từ API
        new LoadAllData().execute();
    }

    private void pickDate(final TextView target) {
        Calendar c = Calendar.getInstance();
        String existing = target.getText().toString().trim();
        if (!existing.isEmpty()) {
            try { c.setTime(SDF.parse(existing)); } catch (Exception ignored) { }
        }
        DatePickerDialog dlg = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                Calendar sel = Calendar.getInstance();
                sel.set(year, month, day);
                target.setText(SDF.format(sel.getTime()));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    private int getAccumulatedQty(int procIndex) {
        if (planData == null) return 0;
        String proc = PROCS[procIndex];

        // Lấy số lượng thực tế đã có từ API trả về
        String actualStr = planData.optString(proc + "_ACTUAL", "");
        if (actualStr.isEmpty()) actualStr = planData.optString(proc + "_QTY", "");
        if (actualStr.isEmpty()) actualStr = planData.optString(proc + "_PROD", "");

        if (!actualStr.isEmpty() && !actualStr.equalsIgnoreCase("null")) {
            try {
                return (int) Double.parseDouble(actualStr);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private void updateProgressUI() {
        int limit = 0;
        try {
            limit = Integer.parseInt(qtyWorking);
        } catch (Exception ignored) {}

        for (int i = 0; i < 4; i++) {
            int actual = getAccumulatedQty(i);
            tvProgresses[i].setText("Tích lũy: " + actual + " / " + limit);
        }
    }

    private boolean validateAndSave() {
        int limit = 0;
        try {
            limit = Integer.parseInt(qtyWorking);
        } catch (Exception ignored) {}

        for (int i = 0; i < 4; i++) {
            String qtyStr = etQtys[i].getText().toString().trim();
            if (!qtyStr.isEmpty()) {
                int inputQty;
                try {
                    inputQty = Integer.parseInt(qtyStr);
                } catch (Exception e) {
                    Toast.makeText(this, PROCS[i] + ": Số lượng nhập thêm không hợp lệ.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (inputQty <= 0) {
                    Toast.makeText(this, PROCS[i] + ": Số lượng nhập thêm phải lớn hơn 0.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                int actual = getAccumulatedQty(i);
                if (limit > 0 && (actual + inputQty) > limit) {
                    Toast.makeText(this, PROCS[i] + ": Tổng sản lượng (" + (actual + inputQty) + ") vượt quá Qty Working (" + limit + ").", Toast.LENGTH_LONG).show();
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

    // ==================== AsyncTask Tải dữ liệu ban đầu ====================
    private class LoadAllData extends AsyncTask<Void, Void, JSONObject> {
        private String error = null;

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang tải dữ liệu hiện tại...");
        }

        @Override
        protected JSONObject doInBackground(Void... v) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_CFM_PLAN + "?cfmid=" + HttpHandler.enc(cfmId);
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
            planData = o;
            if (o != null) {
                // Đổ dữ liệu ngày kế hoạch hiện tại vào các ô và lưu ngày ban đầu
                for (int i = 0; i < 4; i++) {
                    String start = clean(o.optString(PROCS[i] + "_START", ""));
                    String end = clean(o.optString(PROCS[i] + "_END", ""));
                    
                    dateFields[i][0].setText(start);
                    dateFields[i][1].setText(end);
                    
                    // Lưu lại ngày gốc
                    initialDates[i][0] = start;
                    initialDates[i][1] = end;
                }
            } else {
                // Nếu chưa có kế hoạch nào, đặt ngày gốc là chuỗi rỗng
                for (int i = 0; i < 4; i++) {
                    initialDates[i][0] = "";
                    initialDates[i][1] = "";
                }
            }
            
            // Auto-detect trễ kế hoạch để tích Checkbox làm bù
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            for (int i = 0; i < 4; i++) {
                String end = o != null ? clean(o.optString(PROCS[i] + "_END", "")) : "";
                if (!end.isEmpty()) {
                    cbMakeups[i].setChecked(today.compareTo(end) > 0);
                } else {
                    cbMakeups[i].setChecked(false);
                }
            }

            tvStatus.setText("Đã tải dữ liệu thành công.");
            updateProgressUI();
        }


        private String clean(String s) {
            if (s == null || s.equalsIgnoreCase("null")) return "";
            return s;
        }
    }

    // ==================== AsyncTask Lưu thông tin (Batch Save) ====================
    private class SaveAllData extends AsyncTask<Void, Void, String> {
        private String error = null;

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang lưu dữ liệu tiến độ...");
        }

        @Override
        protected String doInBackground(Void... v) {
            HttpHandler sh = new HttpHandler();
            try {
                JSONObject body = new JSONObject();
                body.put("CFM_ID", cfmId);

                // 1. So sánh ngày kế hoạch hiện tại với ban đầu, có thay đổi mới gửi lên
                boolean planChanged = false;
                for (int i = 0; i < 4; i++) {
                    String currentStart = dateFields[i][0].getText().toString().trim();
                    String currentEnd   = dateFields[i][1].getText().toString().trim();
                    String initStart    = initialDates[i][0];
                    String initEnd      = initialDates[i][1];

                    // Nếu ngày bắt đầu hoặc ngày kết thúc khác ngày ban đầu
                    if (!currentStart.equals(initStart) || !currentEnd.equals(initEnd)) {
                        body.put(PROCS[i] + "_START", currentStart);
                        body.put(PROCS[i] + "_END",   currentEnd);
                        planChanged = true;
                    }
                }

                // 2. Thêm số lượng sản lượng nhập thêm nếu có
                boolean hasProd = false;
                for (int i = 0; i < 4; i++) {
                    String qtyStr = etQtys[i].getText().toString().trim();
                    if (!qtyStr.isEmpty()) {
                        body.put(PROCS[i] + "_QTY", qtyStr);
                        body.put(PROCS[i] + "_PRODUCTION_TYPE", cbMakeups[i].isChecked() ? "R" : "N");
                        hasProd = true;
                    }
                }

                // Nếu không có bất kỳ ngày nào đổi và cũng không nhập thêm số lượng
                if (!planChanged && !hasProd) {
                    return "NO_CHANGES";
                }

                // Gửi request duy nhất lên server (gọi API savecfmall)
                String resp = sh.makePostCall(Config.SAVE_CFM_ALL, body.toString());
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
            if ("NO_CHANGES".equals(result)) {
                Toast.makeText(UpdateCfmActivity.this, "Không có thay đổi nào để lưu.", Toast.LENGTH_SHORT).show();
                tvStatus.setText("Không có thay đổi.");
                return;
            }
            if ("OK".equals(result)) {
                Toast.makeText(UpdateCfmActivity.this, "Đã cập nhật Kế hoạch & Sản xuất thành công!", Toast.LENGTH_LONG).show();
                tvStatus.setText("Lưu thành công.");

                // Xóa trắng các ô nhập số lượng mới
                for (int i = 0; i < 4; i++) {
                    etQtys[i].setText("");
                }

                // Tải lại dữ liệu mới để cập nhật UI
                new LoadAllData().execute();
            } else {
                Toast.makeText(UpdateCfmActivity.this, result, Toast.LENGTH_LONG).show();
                tvStatus.setText(result);
            }
        }
    }

    private void showHistoryDialog(final String process) {
        new LoadHistoryTask(process).execute();
    }

    private class LoadHistoryTask extends AsyncTask<Void, Void, String> {
        private final String process;
        private String error = null;

        public LoadHistoryTask(String process) {
            this.process = process;
        }

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang tải lịch sử cho " + process + "...");
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpHandler sh = new HttpHandler();
            String url = Config.GET_PROD_HISTORY + "?cfmid=" + HttpHandler.enc(cfmId) + "&process=" + process;
            return sh.makeServiceCall(url);
        }

        @Override
        protected void onPostExecute(String result) {
            showLoading(false);
            if (result == null) {
                Toast.makeText(UpdateCfmActivity.this, "Lỗi kết nối server khi tải lịch sử.", Toast.LENGTH_SHORT).show();
                tvStatus.setText("Tải lịch sử thất bại.");
                return;
            }
            try {
                JSONArray arr = new JSONArray(result);
                tvStatus.setText("Đã tải dữ liệu thành công.");
                displayHistoryDialog(process, arr);
            } catch (Exception e) {
                Toast.makeText(UpdateCfmActivity.this, "Không có lịch sử nhập hoặc lỗi xử lý: " + e.toString(), Toast.LENGTH_SHORT).show();
                tvStatus.setText("Lỗi hiển thị lịch sử.");
            }
        }
    }

    private void displayHistoryDialog(final String process, final JSONArray historyArray) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cập nhật số lượng - " + process);

        ScrollView scrollView = new ScrollView(this);
        final LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 16, 32, 16);
        scrollView.addView(container);

        final ArrayList<String> deletedGathers = new ArrayList<>();
        final int len = historyArray.length();

        if (len == 0) {
            TextView tvNoData = new TextView(this);
            tvNoData.setText("Chưa có lượt nhập nào trong hệ thống.");
            tvNoData.setPadding(0, 32, 0, 32);
            tvNoData.setGravity(Gravity.CENTER);
            container.addView(tvNoData);
        }

        final ArrayList<View> itemViews = new ArrayList<>();

        for (int i = 0; i < len; i++) {
            try {
                final JSONObject item = historyArray.getJSONObject(i);
                final String gather = item.getString("G_GATHER");
                final String timeStr = item.getString("TIME_STR");
                final int qty = item.getInt("QTY");

                final View itemView = getLayoutInflater().inflate(R.layout.dialog_history_item, null);
                TextView tvTime = itemView.findViewById(R.id.tvTime);
                final EditText etQty = itemView.findViewById(R.id.etQty);
                final android.widget.CheckBox cbHistoryMakeup = itemView.findViewById(R.id.cbHistoryMakeup);
                ImageButton btnDelete = itemView.findViewById(R.id.btnDelete);

                tvTime.setText(timeStr);
                etQty.setText(String.valueOf(qty));
                cbHistoryMakeup.setChecked("R".equalsIgnoreCase(item.optString("PRODUCTION_TYPE", "")));

                itemView.setTag(item);
                itemViews.add(itemView);

                btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        container.removeView(itemView);
                        itemViews.remove(itemView);
                        deletedGathers.add(gather);
                    }
                });

                container.addView(itemView);
            } catch (Exception ignored) {}
        }

        builder.setView(scrollView);

        builder.setPositiveButton("LƯU", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                JSONArray updates = new JSONArray();
                JSONArray deletes = new JSONArray();

                for (String del : deletedGathers) {
                    deletes.put(del);
                }

                for (View v : itemViews) {
                    try {
                        JSONObject orig = (JSONObject) v.getTag();
                        String gather = orig.getString("G_GATHER");
                        int origQty = orig.getInt("QTY");

                        EditText etQty = v.findViewById(R.id.etQty);
                        android.widget.CheckBox cbHistoryMakeup = v.findViewById(R.id.cbHistoryMakeup);
                        String newQtyStr = etQty.getText().toString().trim();
                        if (newQtyStr.isEmpty()) continue;

                        int newQty = Integer.parseInt(newQtyStr);
                        boolean isChecked = cbHistoryMakeup.isChecked();
                        String origType = orig.optString("PRODUCTION_TYPE", "N");
                        boolean typeChanged = (isChecked && !"R".equals(origType)) || (!isChecked && "R".equals(origType));

                        if (newQty != origQty || typeChanged) {
                            JSONObject upd = new JSONObject();
                            upd.put("G_GATHER", gather);
                            upd.put("QTY", newQty);
                            upd.put("PRODUCTION_TYPE", isChecked ? "R" : "N");
                            updates.put(upd);
                        }
                    } catch (Exception ignored) {}
                }

                if (updates.length() == 0 && deletes.length() == 0) {
                    Toast.makeText(UpdateCfmActivity.this, "Không có thay đổi nào.", Toast.LENGTH_SHORT).show();
                    return;
                }

                new SaveHistoryTask(process, updates, deletes).execute();
            }
        });

        builder.setNegativeButton("HỦY", null);
        builder.show();
    }

    private class SaveHistoryTask extends AsyncTask<Void, Void, String> {
        private final String process;
        private final JSONArray updates;
        private final JSONArray deletes;

        public SaveHistoryTask(String process, JSONArray updates, JSONArray deletes) {
            this.process = process;
            this.updates = updates;
            this.deletes = deletes;
        }

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang hiệu chỉnh lịch sử...");
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                JSONObject body = new JSONObject();
                body.put("CFM_ID", cfmId);
                body.put("PROCESS", process);
                body.put("UPDATES", updates);
                body.put("DELETES", deletes);

                HttpHandler sh = new HttpHandler();
                String resp = sh.makePostCall(Config.SAVE_PROD_HISTORY, body.toString());
                if (resp == null) {
                    return "Lỗi kết nối server.";
                }
                JSONObject obj = new JSONObject(resp);
                if (!obj.optString("result", "").equalsIgnoreCase("OK")) {
                    return obj.optString("msg", "Sửa đổi lịch sử thất bại.");
                }
                return "OK";
            } catch (Exception e) {
                return "Lỗi hệ thống: " + e.toString();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            showLoading(false);
            if ("OK".equals(result)) {
                Toast.makeText(UpdateCfmActivity.this, "Đã hiệu chỉnh lịch sử thành công!", Toast.LENGTH_SHORT).show();
                tvStatus.setText("Hiệu chỉnh thành công.");
                new LoadAllData().execute();
            } else {
                Toast.makeText(UpdateCfmActivity.this, result, Toast.LENGTH_LONG).show();
                tvStatus.setText(result);
            }
        }
    }

}
