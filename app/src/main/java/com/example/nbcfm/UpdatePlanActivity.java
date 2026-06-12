package com.example.nbcfm;

import android.app.DatePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Form cập nhật kế hoạch. 4 process: ASS, STT, PRSTT, CUT, mỗi process có
 * ngày bắt đầu và ngày kết thúc. Nếu đã có data thì hiển thị, chưa có thì để trống.
 */
public class UpdatePlanActivity extends AppCompatActivity {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String cfmId;

    private TextView tvTitle, tvStatus;
    private ProgressBar progressBar;
    private Button btnSave;

    // [process][0=start,1=end]
    private final String[] PROCS = {"ASS", "STT", "PRSTT", "CUT"};
    private final TextView[][] dateFields = new TextView[4][2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_plan);

        cfmId = getIntent().getStringExtra("CFM_ID");
        String model = getIntent().getStringExtra("MODEL_NAME");
        String style = getIntent().getStringExtra("STYLE_NO");

        tvTitle     = findViewById(R.id.tvTitle);
        tvStatus    = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        btnSave     = findViewById(R.id.btnSave);

        tvTitle.setText("Cập nhật kế hoạch - CFM " + cfmId + "\n" + safe(model) + " / " + safe(style));

        dateFields[0][0] = findViewById(R.id.tvAssStart);
        dateFields[0][1] = findViewById(R.id.tvAssEnd);
        dateFields[1][0] = findViewById(R.id.tvSttStart);
        dateFields[1][1] = findViewById(R.id.tvSttEnd);
        dateFields[2][0] = findViewById(R.id.tvPrsttStart);
        dateFields[2][1] = findViewById(R.id.tvPrsttEnd);
        dateFields[3][0] = findViewById(R.id.tvCutStart);
        dateFields[3][1] = findViewById(R.id.tvCutEnd);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                final TextView tv = dateFields[i][j];
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { pickDate(tv); }
                });
            }
        }

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { new SavePlan().execute(); }
        });

        new LoadPlan().execute();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private void pickDate(final TextView target) {
        Calendar c = Calendar.getInstance();
        // Nếu ô đã có ngày thì mở đúng ngày đó, chưa có thì mở ngày hiện tại
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

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
    }

    // ==================== AsyncTasks ====================

    private class LoadPlan extends AsyncTask<Void, Void, JSONObject> {
        private String error = null;

        @Override
        protected void onPreExecute() { showLoading(true); tvStatus.setText("Đang tải kế hoạch hiện có..."); }

        @Override
        protected JSONObject doInBackground(Void... v) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_CFM_PLAN + "?cfmid=" + HttpHandler.enc(cfmId);
                Log.d("Debug", "Plan URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr == null) { error = "Không kết nối được server."; return null; }
                jsonStr = jsonStr.trim();
                if (jsonStr.isEmpty() || jsonStr.equals("[]")) return null;
                if (jsonStr.startsWith("[")) {
                    org.json.JSONArray a = new org.json.JSONArray(jsonStr);
                    if (a.length() == 0) return null;
                    return a.getJSONObject(0);
                }
                return new JSONObject(jsonStr);
            } catch (Exception e) {
                error = "Lỗi đọc kế hoạch: " + e.toString();
                Log.e("LoadPlan", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject o) {
            showLoading(false);
            if (error != null) { Toast.makeText(UpdatePlanActivity.this, error, Toast.LENGTH_LONG).show(); tvStatus.setText(error); return; }
            if (o == null) {
                tvStatus.setText("Chưa có kế hoạch. Vui lòng nhập mới.");
                return;
            }
            // Map: ASS_START, ASS_END, STT_START ...
            for (int i = 0; i < PROCS.length; i++) {
                dateFields[i][0].setText(clean(o.optString(PROCS[i] + "_START", "")));
                dateFields[i][1].setText(clean(o.optString(PROCS[i] + "_END", "")));
            }
            tvStatus.setText("Đã tải kế hoạch hiện có.");
        }

        private String clean(String s) {
            if (s == null || s.equalsIgnoreCase("null")) return "";
            return s;
        }
    }

    private class SavePlan extends AsyncTask<Void, Void, Boolean> {
        private String error = null;
        private String serverMsg = "";   // thông báo từ server (msg)

        @Override
        protected void onPreExecute() { showLoading(true); tvStatus.setText("Đang lưu kế hoạch..."); }

        @Override
        protected Boolean doInBackground(Void... v) {
            try {
                JSONObject body = new JSONObject();
                body.put("CFM_ID", cfmId);
                for (int i = 0; i < PROCS.length; i++) {
                    body.put(PROCS[i] + "_START", dateFields[i][0].getText().toString().trim());
                    body.put(PROCS[i] + "_END",   dateFields[i][1].getText().toString().trim());
                }
                HttpHandler sh = new HttpHandler();
                String resp = sh.makePostCall(Config.SAVE_CFM_PLAN, body.toString());
                if (resp == null) { error = "Không kết nối được server."; return false; }
                Log.d("Debug", "SavePlan resp: " + resp);

                // Server trả {"result":"OK"|"FAIL", "msg":"..."}
                try {
                    JSONObject o = new JSONObject(resp);
                    serverMsg = o.optString("msg", "");
                    return o.optString("result", "").equalsIgnoreCase("OK");
                } catch (Exception parseEx) {
                    // Phòng khi server trả chuỗi không phải JSON
                    String r = resp.toUpperCase();
                    return r.contains("OK") || r.contains("SUCCESS") || r.contains("\"RESULT\":1");
                }
            } catch (Exception e) {
                error = "Lỗi lưu kế hoạch: " + e.toString();
                Log.e("SavePlan", error);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean ok) {
            showLoading(false);
            if (error != null) {
                Toast.makeText(UpdatePlanActivity.this, error, Toast.LENGTH_LONG).show();
                tvStatus.setText(error);
                return;
            }
            if (ok) {
                String msg = serverMsg.isEmpty() ? "Lưu kế hoạch thành công." : serverMsg;
                Toast.makeText(UpdatePlanActivity.this, msg, Toast.LENGTH_LONG).show();
                tvStatus.setText("Đã lưu thành công.");
            } else {
                // Hiển thị đúng thông báo từ server: null -> "chưa nhập số lượng...",
                // text -> "sửa lại số lượng QTY..."
                String msg = serverMsg.isEmpty() ? "Lưu thất bại. Vui lòng thử lại." : serverMsg;
                Toast.makeText(UpdatePlanActivity.this, msg, Toast.LENGTH_LONG).show();
                tvStatus.setText(msg);
            }
        }
    }
}
