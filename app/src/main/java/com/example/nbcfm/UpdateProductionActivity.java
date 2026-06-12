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
 */
public class UpdateProductionActivity extends AppCompatActivity {

    private String cfmId;

    private TextView tvTitle, tvStatus;
    private Spinner spinnerProcess;
    private EditText etQty;
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

        tvTitle        = findViewById(R.id.tvTitle);
        tvStatus       = findViewById(R.id.tvStatus);
        spinnerProcess = findViewById(R.id.spinnerProcess);
        etQty          = findViewById(R.id.etQty);
        btnSave        = findViewById(R.id.btnSave);
        progressBar    = findViewById(R.id.progressBar);

        tvTitle.setText("Cập nhật sản xuất - CFM " + cfmId + "\n"
                + (model == null ? "" : model) + " / " + (style == null ? "" : style));

        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, PROCESSES);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProcess.setAdapter(ad);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String qty = etQty.getText().toString().trim();
                if (TextUtils.isEmpty(qty)) {
                    Toast.makeText(UpdateProductionActivity.this, "Vui lòng nhập QTY.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try { Integer.parseInt(qty); }
                catch (Exception e) {
                    Toast.makeText(UpdateProductionActivity.this, "QTY phải là số.", Toast.LENGTH_SHORT).show();
                    return;
                }
                new SaveProduction().execute();
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
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
            } else {
                Toast.makeText(UpdateProductionActivity.this, "Lưu thất bại. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
                tvStatus.setText("Lưu thất bại.");
            }
        }
    }
}
