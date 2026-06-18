package com.example.nbcfm;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerSeasonFrom, spinnerSeasonTo, spinnerStage, spinnerModel, spinnerTeam, spinnerDev;
    private Button btnRetrieve, btnClearFilter;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvStatus, tvEmpty;

    private final ArrayList<String> arraySeason = new ArrayList<>();
    private final ArrayList<String> arrayStage  = new ArrayList<>();
    private final ArrayList<String> arrayModel  = new ArrayList<>();
    private final ArrayList<String> arrayTeam  = new ArrayList<>();
    private final ArrayList<String> arrayDev  = new ArrayList<>();

    private final ArrayList<CfmItem> cfmList = new ArrayList<>();
    private CfmAdapter adapter;

    // Cờ chặn lần onItemSelected tự kích hoạt ngay khi setAdapter (tránh load thừa / vòng lặp)
    private boolean ignoreSeason = false;
    private boolean ignoreStage  = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerSeasonFrom = findViewById(R.id.spinnerSeasonFrom);
        spinnerSeasonTo   = findViewById(R.id.spinnerSeasonTo);
        spinnerStage      = findViewById(R.id.spinnerStage);
        spinnerModel      = findViewById(R.id.spinnerModel);
        spinnerTeam       = findViewById(R.id.spinnerTeam);
        spinnerDev        = findViewById(R.id.spinnerDev);
        btnRetrieve   = findViewById(R.id.btnRetrieve);
        btnClearFilter = findViewById(R.id.btnClearFilter);
        recyclerView  = findViewById(R.id.recyclerView);
        progressBar   = findViewById(R.id.progressBar);
        tvStatus      = findViewById(R.id.tvStatus);
        tvEmpty       = findViewById(R.id.tvEmpty);

        adapter = new CfmAdapter(this, cfmList, new CfmAdapter.OnItemClick() {
            @Override
            public void onClick(CfmItem item) {
                showItemMenu(item);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Season From đổi -> Tự động đồng bộ Season To và nạp lại Stage
        spinnerSeasonFrom.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (ignoreSeason) { ignoreSeason = false; return; }
                if (spinnerSeasonTo.getSelectedItemPosition() != pos) {
                    spinnerSeasonTo.setSelection(pos);
                } else {
                    reloadStages();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        // Season To đổi -> nạp lại Stage
        spinnerSeasonTo.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                reloadStages();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        // Stage đổi -> nạp lại Model
        spinnerStage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (ignoreStage) { ignoreStage = false; return; }     // bo qua phat tu dong dau tien
                reloadModels();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        // Model đổi -> nạp lại Team
        spinnerModel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                reloadTeams();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        // Team đổi -> nạp lại Dev
        spinnerTeam.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                reloadDevs();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        btnRetrieve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new RetrieveCfm().execute();
            }
        });

        btnClearFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Đặt lại các Spinner về lựa chọn đầu tiên ("%")
                ignoreSeason = true;
                ignoreStage = true;
                spinnerSeasonFrom.setSelection(0);
                spinnerSeasonTo.setSelection(0);
                spinnerStage.setSelection(0);
                spinnerModel.setSelection(0);
                spinnerTeam.setSelection(0);
                spinnerDev.setSelection(0);

                cfmList.clear();
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(View.GONE);
                tvStatus.setText("Sẵn sàng.");
                // Gọi chuỗi tải lại dữ liệu từ đầu
                reloadStages();
            }
        });

        // Chỉ cần nạp Seasons ban đầu. Sau đó chuỗi cascading sẽ tự động tải các spinner còn lại.
        new LoadSeasons().execute();
    }

    private void setStatus(String msg) {
        tvStatus.setText(msg);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRetrieve.setEnabled(!show);
    }

    /** Nap lai Stage theo Season range. */
    private void reloadStages() {
        String seasonFrom = spinnerSeasonFrom.getSelectedItem() != null ? spinnerSeasonFrom.getSelectedItem().toString() : "";
        String seasonTo   = spinnerSeasonTo.getSelectedItem()   != null ? spinnerSeasonTo.getSelectedItem().toString()   : "";
        if (!seasonFrom.isEmpty() || !seasonTo.isEmpty()) {
            new LoadStages().execute(seasonFrom, seasonTo);
        }
    }

    /** Nap lai Model theo Season range + Stage. */
    private void reloadModels() {
        String seasonFrom = spinnerSeasonFrom.getSelectedItem() != null ? spinnerSeasonFrom.getSelectedItem().toString() : "";
        String seasonTo   = spinnerSeasonTo.getSelectedItem()   != null ? spinnerSeasonTo.getSelectedItem().toString()   : "";
        String stage      = spinnerStage.getSelectedItem()      != null ? spinnerStage.getSelectedItem().toString()      : "";
        if (!seasonFrom.isEmpty() || !seasonTo.isEmpty()) {
            new LoadModels().execute(seasonFrom, seasonTo, stage);
        }
    }

    /** Nap lai Team theo các bộ lọc trước đó. */
    private void reloadTeams() {
        String seasonFrom = spinnerSeasonFrom.getSelectedItem() != null ? spinnerSeasonFrom.getSelectedItem().toString() : "";
        String seasonTo   = spinnerSeasonTo.getSelectedItem()   != null ? spinnerSeasonTo.getSelectedItem().toString()   : "";
        String stage      = spinnerStage.getSelectedItem()      != null ? spinnerStage.getSelectedItem().toString()      : "";
        String model      = spinnerModel.getSelectedItem()      != null ? spinnerModel.getSelectedItem().toString()      : "";
        if (model.contains(" / ")) {
            model = model.split(" / ")[0].trim();
        }
        new LoadTeams().execute(seasonFrom, seasonTo, stage, model);
    }

    /** Nap lai Dev theo các bộ lọc trước đó. */
    private void reloadDevs() {
        String seasonFrom = spinnerSeasonFrom.getSelectedItem() != null ? spinnerSeasonFrom.getSelectedItem().toString() : "";
        String seasonTo   = spinnerSeasonTo.getSelectedItem()   != null ? spinnerSeasonTo.getSelectedItem().toString()   : "";
        String stage      = spinnerStage.getSelectedItem()      != null ? spinnerStage.getSelectedItem().toString()      : "";
        String model      = spinnerModel.getSelectedItem()      != null ? spinnerModel.getSelectedItem().toString()      : "";
        if (model.contains(" / ")) {
            model = model.split(" / ")[0].trim();
        }
        String team       = spinnerTeam.getSelectedItem()       != null ? spinnerTeam.getSelectedItem().toString()       : "";
        new LoadDevs().execute(seasonFrom, seasonTo, stage, model, team);
    }

    // ----- Menu khi chon 1 cardview -----
    private void showItemMenu(final CfmItem item) {
        final CharSequence[] options = {"Cap nhat ke hoach", "Cap nhat san xuat"};
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("CFM ID: " + item.cfmId);
        b.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent it;
                if (which == 0) {
                    it = new Intent(MainActivity.this, UpdatePlanActivity.class);
                } else {
                    it = new Intent(MainActivity.this, UpdateProductionActivity.class);
                }
                it.putExtra("CFM_ID", item.cfmId);
                it.putExtra("MODEL_NAME", item.modelName);
                it.putExtra("STYLE_NO", item.styleNo);
                it.putExtra("QTY_WORKING", item.qtyWorking);
                startActivity(it);
            }
        });
        b.setNegativeButton("Huy", null);
        b.show();
    }

    // ========================= AsyncTasks =========================

    private class LoadSeasons extends AsyncTask<Void, Void, Void> {
        private String error = null;

        @Override protected void onPreExecute() { setStatus("Dang load Season..."); }

        @Override
        protected Void doInBackground(Void... v) {
            try {
                HttpHandler sh = new HttpHandler();
                String jsonStr = sh.makeServiceCall(Config.GET_SEASONS);
                if (jsonStr == null) { error = "Khong ket noi duoc server (Season)."; return null; }
                JSONArray arr = new JSONArray(jsonStr);
                arraySeason.clear();
                arraySeason.add("%");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    arraySeason.add(c.optString("SEASON", c.optString("VALUE", "")));
                }
            } catch (Exception e) {
                error = "Loi doc du lieu Season: " + e.toString();
                Log.e("LoadSeasons", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error != null) { Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show(); setStatus(error); return; }
            ignoreSeason = true;   // chan phat onItemSelected tu dong do setAdapter
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, arraySeason);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerSeasonFrom.setAdapter(ad);
            spinnerSeasonTo.setAdapter(ad);
            setStatus("San sang.");
            // Season da co -> nap Stage cho lua chon mac dinh
            reloadStages();
        }
    }

    private class LoadStages extends AsyncTask<String, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(String... params) {
            try {
                String seasonFrom = params.length > 0 ? params[0] : "";
                String seasonTo   = params.length > 1 ? params[1] : "";
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_STAGES 
                        + "?season_from=" + HttpHandler.enc(seasonFrom)
                        + "&season_to="   + HttpHandler.enc(seasonTo);
                Log.d("Debug", "Stages URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr == null) { error = "Khong ket noi duoc server (Stage)."; return null; }
                JSONArray arr = new JSONArray(jsonStr);
                arrayStage.clear();
                arrayStage.add("%");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    arrayStage.add(c.optString("CURRENT_STAGE", c.optString("VALUE", "")));
                }
            } catch (Exception e) {
                error = "Loi doc du lieu Stage: " + e.toString();
                Log.e("LoadStages", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error != null) { Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show(); return; }
            ignoreStage = true;   // chan phat onItemSelected tu dong do setAdapter
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, arrayStage);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerStage.setAdapter(ad);
            // Stage da co -> nap Model cho lua chon mac dinh
            reloadModels();
        }
    }

    private class LoadModels extends AsyncTask<String, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(String... params) {
            try {
                String seasonFrom = params.length > 0 ? params[0] : "";
                String seasonTo   = params.length > 1 ? params[1] : "";
                String stage      = params.length > 2 ? params[2] : "";
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_MODELS
                        + "?season_from=" + HttpHandler.enc(seasonFrom)
                        + "&season_to="   + HttpHandler.enc(seasonTo)
                        + "&stage="       + HttpHandler.enc(stage);
                Log.d("Debug", "Models URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr == null) { error = "Khong ket noi duoc server (Model)."; return null; }
                JSONArray arr = new JSONArray(jsonStr);
                arrayModel.clear();
                arrayModel.add("%");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    String model = c.optString("MODEL_NAME", "");
                    String style = c.optString("STYLE_NO", "");
                    arrayModel.add(style.isEmpty() ? model : (model + " / " + style));
                }
            } catch (Exception e) {
                error = "Loi doc du lieu Model: " + e.toString();
                Log.e("LoadModels", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error != null) { Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show(); return; }
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, arrayModel);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerModel.setAdapter(ad);
            // Model da co -> nap tiep Team
            reloadTeams();
        }
    }

    private class LoadTeams extends AsyncTask<String, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(String... params) {
            try {
                String seasonFrom = params.length > 0 ? params[0] : "";
                String seasonTo   = params.length > 1 ? params[1] : "";
                String stage      = params.length > 2 ? params[2] : "";
                String model      = params.length > 3 ? params[3] : "";
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_TEAMS
                        + "?season_from=" + HttpHandler.enc(seasonFrom)
                        + "&season_to="   + HttpHandler.enc(seasonTo)
                        + "&stage="       + HttpHandler.enc(stage)
                        + "&model="       + HttpHandler.enc(model);
                Log.d("Debug", "Teams URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr == null) { error = "Khong ket noi duoc server (Team)."; return null; }
                JSONArray arr = new JSONArray(jsonStr);
                arrayTeam.clear();
                arrayTeam.add("%"); // Thêm lựa chọn Tất cả
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    arrayTeam.add(c.optString("VS_TEAM", c.optString("VALUE", "")));
                }
            } catch (Exception e) {
                error = "Loi doc du lieu Team: " + e.toString();
                Log.e("LoadTeams", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error != null) { Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show(); return; }
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, arrayTeam);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTeam.setAdapter(ad);
            // Team da co -> nap tiep Dev
            reloadDevs();
        }
    }

    private class LoadDevs extends AsyncTask<String, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(String... params) {
            try {
                String seasonFrom = params.length > 0 ? params[0] : "";
                String seasonTo   = params.length > 1 ? params[1] : "";
                String stage      = params.length > 2 ? params[2] : "";
                String model      = params.length > 3 ? params[3] : "";
                String team       = params.length > 4 ? params[4] : "";
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_DEVS
                        + "?season_from=" + HttpHandler.enc(seasonFrom)
                        + "&season_to="   + HttpHandler.enc(seasonTo)
                        + "&stage="       + HttpHandler.enc(stage)
                        + "&model="       + HttpHandler.enc(model)
                        + "&team="        + HttpHandler.enc(team);
                Log.d("Debug", "Devs URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr == null) { error = "Khong ket noi duoc server (Dev)."; return null; }
                JSONArray arr = new JSONArray(jsonStr);
                arrayDev.clear();
                arrayDev.add("%"); // Thêm lựa chọn Tất cả
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    arrayDev.add(c.optString("VS_DEVELOPER", c.optString("VALUE", "")));
                }
            } catch (Exception e) {
                error = "Loi doc du lieu Dev: " + e.toString();
                Log.e("LoadDevs", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error != null) { Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show(); return; }
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, arrayDev);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerDev.setAdapter(ad);
        }
    }


    private class RetrieveCfm extends AsyncTask<Void, Void, Void> {
        private String error = null;
        private final ArrayList<CfmItem> result = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            showLoading(true);
            setStatus("Dang truy xuat du lieu...");
            tvEmpty.setVisibility(View.GONE);
        }

        @Override
        protected Void doInBackground(Void... v) {
            try {
                String seasonFrom = spinnerSeasonFrom.getSelectedItem() != null ? spinnerSeasonFrom.getSelectedItem().toString() : "";
                String seasonTo = spinnerSeasonTo.getSelectedItem() != null ? spinnerSeasonTo.getSelectedItem().toString() : "";
                String stage  = spinnerStage.getSelectedItem()  != null ? spinnerStage.getSelectedItem().toString()  : "";
                String model  = spinnerModel.getSelectedItem()  != null ? spinnerModel.getSelectedItem().toString()  : "";
                String team  = spinnerTeam.getSelectedItem()  != null ? spinnerTeam.getSelectedItem().toString()  : "";
                String dev = spinnerDev.getSelectedItem()  != null ? spinnerDev.getSelectedItem().toString()  : "";

                HttpHandler sh = new HttpHandler();
                String url = Config.GET_CFM_LIST
                        + "?season_from=" + HttpHandler.enc(seasonFrom)
                        + "&season_to="   + HttpHandler.enc(seasonTo)
                        + "&stage="       + HttpHandler.enc(stage)
                        + "&model="       + HttpHandler.enc(model)
                        + "&team="        + HttpHandler.enc(team)
                        + "&developer="   + HttpHandler.enc(dev);
                Log.d("Debug", "CFM list URL: " + url);

                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr == null) { error = "Khong ket noi duoc server. Kiem tra mang/IP."; return null; }

                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    result.add(CfmItem.fromJson(arr.getJSONObject(i)));
                }
            } catch (Exception e) {
                error = "Loi xu ly du lieu: " + e.toString();
                Log.e("RetrieveCfm", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            showLoading(false);
            if (error != null) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                setStatus(error);
                return;
            }
            cfmList.clear();
            cfmList.addAll(result);
            adapter.notifyDataSetChanged();

            if (cfmList.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                setStatus("Khong co du lieu phu hop.");
                Toast.makeText(MainActivity.this, "Khong tim thay du lieu CFM.", Toast.LENGTH_LONG).show();
            } else {
                tvEmpty.setVisibility(View.GONE);
                setStatus("Tim thay " + cfmList.size() + " CFM.");
                Toast.makeText(MainActivity.this, "Da tai " + cfmList.size() + " CFM.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}