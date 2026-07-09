package com.example.nbcfm;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/**
 * Form cập nhật sản xuất: chọn Process (ASS/STT/CUT/PRSTT) và nhập QTY (số).
 * Hiển thị số lượng đã tồn tại trong tracking so với QTY_WORKING và validate.
 */
public class UpdateProductionActivity extends AppCompatActivity {

    private String cfmId;
    private String qtyWorking = "0";
    private JSONObject planData = null;

    private TextView tvTitle, tvStatus, tvProgress;
    private Spinner spinnerProcess;
    private EditText etQty;
    private android.widget.CheckBox cbMakeup;
    private Button btnSave;
    private ProgressBar progressBar;

    private final String[] PROCESSES = {"ASS", "STT", "CUT", "PRSTT"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_production);

        cfmId = getIntent().getStringExtra("CFM_ID");
        String model = getIntent().getStringExtra("MODEL_NAME");
        String style = getIntent().getStringExtra("STYLE_NO");
        
        String qtyWorkingExtra = getIntent().getStringExtra("QTY_WORKING");
        if (qtyWorkingExtra != null && !qtyWorkingExtra.trim().isEmpty()) {
            qtyWorking = qtyWorkingExtra.trim();
        }

        tvTitle        = findViewById(R.id.tvTitle);
        tvStatus       = findViewById(R.id.tvStatus);
        tvProgress     = findViewById(R.id.tvProgress);
        spinnerProcess = findViewById(R.id.spinnerProcess);
        etQty          = findViewById(R.id.etQty);
        cbMakeup       = findViewById(R.id.cbMakeup);
        btnSave        = findViewById(R.id.btnSave);
        progressBar    = findViewById(R.id.progressBar);

        tvTitle.setText("Cập nhật sản xuất - CFM " + cfmId + "\n"
                + (model == null ? "" : model) + " / " + (style == null ? "" : style));

        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, PROCESSES);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProcess.setAdapter(ad);

        spinnerProcess.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateProgressUI();
                checkAndSetMakeupCheckbox();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String qty = etQty.getText().toString().trim();
                if (TextUtils.isEmpty(qty)) {
                    Toast.makeText(UpdateProductionActivity.this, "Vui lòng nhập QTY.", Toast.LENGTH_SHORT).show();
                    return;
                }
                int inputQty = 0;
                try {
                    inputQty = Integer.parseInt(qty);
                } catch (Exception e) {
                    Toast.makeText(UpdateProductionActivity.this, "QTY phải là số.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (inputQty <= 0) {
                    Toast.makeText(UpdateProductionActivity.this, "QTY phải lớn hơn 0.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Validate against qtyWorking
                int actual = getAccumulatedQty();
                int targetLimit = 0;
                try {
                    targetLimit = Integer.parseInt(qtyWorking);
                } catch (Exception ignored) {
                }
                
                if (targetLimit > 0 && (actual + inputQty) > targetLimit) {
                    Toast.makeText(UpdateProductionActivity.this, 
                        "Lỗi: Tổng sản lượng (" + (actual + inputQty) + ") vượt quá Qty Working (" + targetLimit + ").", 
                        Toast.LENGTH_LONG).show();
                    return;
                }
                
                new SaveProduction().execute();
            }
        });

        // Load existing tracking quantities on startup
        new LoadPlanData().execute();
    }

    private int getAccumulatedQty() {
        String proc = spinnerProcess.getSelectedItem() != null ? spinnerProcess.getSelectedItem().toString() : "";
        if (proc.isEmpty() || planData == null) return 0;
        
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

    private void checkAndSetMakeupCheckbox() {
        String proc = spinnerProcess.getSelectedItem() != null ? spinnerProcess.getSelectedItem().toString() : "";
        if (proc.isEmpty() || planData == null || cbMakeup == null) {
            if (cbMakeup != null) cbMakeup.setChecked(false);
            return;
        }
        String endLimit = planData.optString(proc + "_END", "");
        if (!endLimit.isEmpty() && !endLimit.equalsIgnoreCase("null")) {
            try {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
                cbMakeup.setChecked(today.compareTo(endLimit) > 0);
            } catch (Exception e) {
                cbMakeup.setChecked(false);
            }
        } else {
            cbMakeup.setChecked(false);
        }
    }

    private void updateProgressUI() {
        if (tvProgress == null) return;
        int actual = getAccumulatedQty();
        tvProgress.setText("QTY WORKING: " + actual + " / " + qtyWorking);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
    }

    // ==================== AsyncTasks ====================

    private class LoadPlanData extends AsyncTask<Void, Void, JSONObject> {
        private String error = null;

        @Override
        protected void onPreExecute() {
            showLoading(true);
            tvStatus.setText("Đang tải dữ liệu tích lũy...");
        }

        @Override
        protected JSONObject doInBackground(Void... v) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_CFM_PLAN + "?cfmid=" + HttpHandler.enc(cfmId);
                Log.d("Debug", "LoadPlanData URL: " + url);
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
                Log.e("LoadPlanData", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject o) {
            showLoading(false);
            if (error != null) {
                tvStatus.setText(error);
                updateProgressUI();
                return;
            }
            planData = o;
            tvStatus.setText("Đã tải dữ liệu tích lũy.");
            updateProgressUI();
            checkAndSetMakeupCheckbox();
        }
    }

    private class SaveProduction extends AsyncTask<Void, Void, Boolean> {
        private String error = null;

        @Override
        protected void onPreExecute() { showLoading(true); tvStatus.setText("Đang lưu sản xuất..."); }

        @Override
        protected Boolean doInBackground(Void... v) {
            try {
                JSONObject body = new JSONObject();
                body.put("CFM_ID", cfmId);
                body.put("PROCESS", spinnerProcess.getSelectedItem().toString());
                body.put("QTY", etQty.getText().toString().trim());
                body.put("PRODUCTION_TYPE", cbMakeup.isChecked() ? "R" : "N");

                HttpHandler sh = new HttpHandler();
                String resp = sh.makePostCall(Config.SAVE_CFM_PROD, body.toString());
                if (resp == null) { error = "Không kết nối được server."; return false; }
                Log.d("Debug", "SaveProd resp: " + resp);
                String r = resp.toUpperCase();
                return r.contains("OK") || r.contains("SUCCESS") || r.contains("\"RESULT\":1");
            } catch (Exception e) {
                error = "Lỗi lưu sản xuất: " + e.toString();
                Log.e("SaveProduction", error);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean ok) {
            showLoading(false);
            if (error != null) { Toast.makeText(UpdateProductionActivity.this, error, Toast.LENGTH_LONG).show(); tvStatus.setText(error); return; }
            if (ok) {
                Toast.makeText(UpdateProductionActivity.this, "Lưu sản xuất thành công.", Toast.LENGTH_LONG).show();
                tvStatus.setText("Đã lưu thành công.");
                etQty.setText("");
                new LoadPlanData().execute(); // Reload progress after saving
            } else {
                Toast.makeText(UpdateProductionActivity.this, "Lưu thất bại. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
                tvStatus.setText("Lưu thất bại.");
            }
        }
    }
}
