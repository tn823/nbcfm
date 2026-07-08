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
import android.widget.AutoCompleteTextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.facebook.shimmer.ShimmerFrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private AutoCompleteTextView acSeasonFrom, acSeasonTo, acStage, acModel, acTeam, acDev;
    private Spinner spinnerPlanFilter;
    private Button btnRetrieve, btnClearFilter;
    private RecyclerView recyclerView;
    private ShimmerFrameLayout shimmerViewContainer;
    private ProgressBar progressBar;
    private TextView tvStatus, tvEmpty;

    private final ArrayList<String> arraySeason = new ArrayList<>();
    private final ArrayList<String> arrayStage  = new ArrayList<>();
    private final ArrayList<String> arrayModel  = new ArrayList<>();
    private final ArrayList<String> arrayTeam  = new ArrayList<>();
    private final ArrayList<String> arrayDev  = new ArrayList<>();

    private final ArrayList<CfmItem> cfmList = new ArrayList<>();
    private final ArrayList<CfmItem> fullCfmList = new ArrayList<>();
    private CfmAdapter adapter;

    // Cờ chặn lần onItemSelected tự kích hoạt ngay khi setAdapter (tránh load thừa / vòng lặp)
    private boolean ignoreSeason = false;
    private boolean ignoreStage  = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        acSeasonFrom = findViewById(R.id.acSeasonFrom);
        acSeasonTo   = findViewById(R.id.acSeasonTo);
        acStage      = findViewById(R.id.acStage);
        acModel      = findViewById(R.id.acModel);
        acTeam       = findViewById(R.id.acTeam);
        acDev        = findViewById(R.id.acDev);
        btnRetrieve   = findViewById(R.id.btnRetrieve);
        btnClearFilter = findViewById(R.id.btnClearFilter);
        recyclerView  = findViewById(R.id.recyclerView);
        shimmerViewContainer = findViewById(R.id.shimmerViewContainer);
        progressBar   = findViewById(R.id.progressBar);
        tvStatus      = findViewById(R.id.tvStatus);
        tvEmpty       = findViewById(R.id.tvEmpty);

        spinnerPlanFilter = findViewById(R.id.spinnerPlanFilter);
        String[] planFilterOptions = {"Tất cả", "Có Plan", "Chưa có Plan"};
        ArrayAdapter<String> planFilterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, planFilterOptions);
        planFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPlanFilter.setAdapter(planFilterAdapter);
        spinnerPlanFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                applyPlanFilter();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        adapter = new CfmAdapter(this, cfmList, new CfmAdapter.OnItemClick() {
            @Override
            public void onClick(CfmItem item) {
                showItemMenu(item);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Khởi tạo logic tự động gợi ý và kích hoạt chuỗi liên kết các bộ lọc
        setupAutoComplete(acSeasonFrom, new Runnable() {
            @Override
            public void run() {
                if (ignoreSeason) { ignoreSeason = false; return; }
                String val = acSeasonFrom.getText().toString().trim();
                // Đồng bộ Season To với Season From
                if (!acSeasonTo.getText().toString().trim().equals(val)) {
                    acSeasonTo.setText(val, false);
                }
                reloadStages();
            }
        });

        setupAutoComplete(acSeasonTo, new Runnable() {
            @Override
            public void run() {
                reloadStages();
            }
        });

        setupAutoComplete(acStage, new Runnable() {
            @Override
            public void run() {
                if (ignoreStage) { ignoreStage = false; return; }
                reloadModels();
            }
        });

        setupAutoComplete(acModel, new Runnable() {
            @Override
            public void run() {
                reloadTeams();
            }
        });

        setupAutoComplete(acTeam, new Runnable() {
            @Override
            public void run() {
                reloadDevs();
            }
        });

        setupAutoComplete(acDev, new Runnable() {
            @Override
            public void run() {
                // Ô cuối cùng, không cần reload các ô sau
            }
        });

        btnRetrieve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                new RetrieveCfm().execute();
            }
        });

        btnClearFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ignoreSeason = true;
                ignoreStage = true;
                acSeasonFrom.setText("", false);
                acSeasonTo.setText("", false);
                acStage.setText("", false);
                acModel.setText("", false);
                acTeam.setText("", false);
                acDev.setText("", false);

                cfmList.clear();
                fullCfmList.clear();
                spinnerPlanFilter.setSelection(0);
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
        if (msg != null && msg.startsWith("Total:")) {
            tvStatus.setText(msg);
            tvStatus.setTextColor(getResources().getColor(R.color.nb_red));
            tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
            tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        } else {
            tvStatus.setText(msg);
            tvStatus.setTextColor(getResources().getColor(R.color.textSecondary));
            tvStatus.setTypeface(null, android.graphics.Typeface.NORMAL);
            tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRetrieve.setEnabled(!show);
        if (shimmerViewContainer != null) {
            if (show) {
                shimmerViewContainer.setVisibility(View.VISIBLE);
                shimmerViewContainer.startShimmer();
                recyclerView.setVisibility(View.GONE);
            } else {
                shimmerViewContainer.stopShimmer();
                shimmerViewContainer.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void reloadStages() {
        String seasonFrom = acSeasonFrom.getText().toString().trim();
        String seasonTo   = acSeasonTo.getText().toString().trim();
        if (seasonFrom.isEmpty()) seasonFrom = "%";
        if (seasonTo.isEmpty()) seasonTo = "%";
        new LoadStages().execute(seasonFrom, seasonTo);
    }

    private void reloadModels() {
        String seasonFrom = acSeasonFrom.getText().toString().trim();
        String seasonTo   = acSeasonTo.getText().toString().trim();
        String stage      = acStage.getText().toString().trim();
        if (seasonFrom.isEmpty()) seasonFrom = "%";
        if (seasonTo.isEmpty()) seasonTo = "%";
        if (stage.isEmpty()) stage = "%";
        new LoadModels().execute(seasonFrom, seasonTo, stage);
    }

    private void reloadTeams() {
        String seasonFrom = acSeasonFrom.getText().toString().trim();
        String seasonTo   = acSeasonTo.getText().toString().trim();
        String stage      = acStage.getText().toString().trim();
        String model      = acModel.getText().toString().trim();
        if (seasonFrom.isEmpty()) seasonFrom = "%";
        if (seasonTo.isEmpty()) seasonTo = "%";
        if (stage.isEmpty()) stage = "%";
        if (model.isEmpty()) model = "%";
        if (model.contains(" / ")) {
            model = model.split(" / ")[0].trim();
        }
        new LoadTeams().execute(seasonFrom, seasonTo, stage, model);
    }

    private void reloadDevs() {
        String seasonFrom = acSeasonFrom.getText().toString().trim();
        String seasonTo   = acSeasonTo.getText().toString().trim();
        String stage      = acStage.getText().toString().trim();
        String model      = acModel.getText().toString().trim();
        if (seasonFrom.isEmpty()) seasonFrom = "%";
        if (seasonTo.isEmpty()) seasonTo = "%";
        if (stage.isEmpty()) stage = "%";
        if (model.isEmpty()) model = "%";
        if (model.contains(" / ")) {
            model = model.split(" / ")[0].trim();
        }
        String team       = acTeam.getText().toString().trim();
        if (team.isEmpty()) team = "%";
        new LoadDevs().execute(seasonFrom, seasonTo, stage, model, team);
    }

    // ----- Menu khi chon 1 cardview -----
    private void showItemMenu(final CfmItem item) {
        Intent it = new Intent(MainActivity.this, UpdateCfmActivity.class);
        it.putExtra("CFM_ID", item.cfmId);
        it.putExtra("MODEL_NAME", item.modelName);
        it.putExtra("STYLE_NO", item.styleNo);
        it.putExtra("QTY_WORKING", item.qtyWorking);
        startActivity(it);
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
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arraySeason);
            acSeasonFrom.setAdapter(ad);
            acSeasonTo.setAdapter(ad);
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
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayStage);
            acStage.setAdapter(ad);
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
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayModel);
            acModel.setAdapter(ad);
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
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayTeam);
            acTeam.setAdapter(ad);
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
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayDev);
            acDev.setAdapter(ad);
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
                String seasonFrom = acSeasonFrom.getText().toString().trim();
                String seasonTo   = acSeasonTo.getText().toString().trim();
                String stage      = acStage.getText().toString().trim();
                String model      = acModel.getText().toString().trim();
                String team       = acTeam.getText().toString().trim();
                String dev        = acDev.getText().toString().trim();

                if (seasonFrom.isEmpty()) seasonFrom = "%";
                if (seasonTo.isEmpty()) seasonTo = "%";
                if (stage.isEmpty()) stage = "%";
                if (model.isEmpty()) model = "%";
                if (model.contains(" / ")) {
                    model = model.split(" / ")[0].trim();
                }
                if (team.isEmpty()) team = "%";
                if (dev.isEmpty()) dev = "%";

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
                java.util.Collections.sort(result, new java.util.Comparator<CfmItem>() {
                    @Override
                    public int compare(CfmItem o1, CfmItem o2) {
                        return Integer.compare(o2.hasPlan, o1.hasPlan); // Sắp xếp giảm dần: 1 lên trước, 0 xuống sau
                    }
                });
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
            fullCfmList.clear();
            fullCfmList.addAll(result);
            applyPlanFilter();
            recyclerView.scrollToPosition(0);

            if (result.isEmpty()) {
                Toast.makeText(MainActivity.this, "Khong tim thay du lieu CFM.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "Da tai " + result.size() + " CFM.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void applyPlanFilter() {
        if (fullCfmList.isEmpty()) {
            cfmList.clear();
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Không có dữ liệu.");
            setStatus("Sẵn sàng.");
            return;
        }

        int selectedPosition = spinnerPlanFilter.getSelectedItemPosition();
        cfmList.clear();
        for (CfmItem item : fullCfmList) {
            if (selectedPosition == 0) {
                cfmList.add(item);
            } else if (selectedPosition == 1) {
                if (item.hasPlan == 1) {
                    cfmList.add(item);
                }
            } else if (selectedPosition == 2) {
                if (item.hasPlan == 0) {
                    cfmList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();

        if (cfmList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Không có dữ liệu phù hợp.");
            setStatus("Tổng: 0 CFM.");
        } else {
            tvEmpty.setVisibility(View.GONE);
            setStatus("Tổng: " + cfmList.size() + " CFM.");
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void setupAutoComplete(final AutoCompleteTextView ac, final Runnable onSelectOrChange) {
        // Mở dropdown hiển thị danh sách khi click
        ac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ac.showDropDown();
            }
        });

        // Mở dropdown khi ô nhập nhận focus
        ac.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    ac.showDropDown();
                } else {
                    // Khi rời khỏi ô nhập (nhập tay xong và mất focus), chạy logic reload các bộ lọc tiếp theo
                    onSelectOrChange.run();
                }
            }
        });

        // Chạy logic reload khi chọn một giá trị từ danh sách gợi ý dropdown
        ac.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                onSelectOrChange.run();
            }
        });

        // Cấu hình hiển thị nút xóa (ic_clear) ở bên phải khi có chữ
        ac.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    ac.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_clear, 0);
                } else {
                    ac.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                }
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Touch Listener để bắt sự kiện click vào nút xóa
        ac.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    if (ac.getCompoundDrawables()[2] != null) {
                        int clearButtonWidth = ac.getCompoundDrawables()[2].getBounds().width();
                        int xClick = (int) event.getX();
                        // Nếu click trong phạm vi nút xóa (bên phải)
                        if (xClick >= (ac.getWidth() - ac.getPaddingRight() - clearButtonWidth - 10)) {
                            ac.setText("", false);
                            onSelectOrChange.run(); // Chạy lại logic lọc cho các ô phía sau
                            ac.showDropDown(); // Hiện lại dropdown sau khi xóa
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }
}