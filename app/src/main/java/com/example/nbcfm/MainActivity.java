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
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.facebook.shimmer.ShimmerFrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_UPDATE_CFM = 1001;

    private AutoCompleteTextView acSeason, acStyleNo, acStage, acModel, acTeam, acDev;
    private Spinner spinnerPlanFilter;
    private Button btnRetrieve, btnClearFilter, btnSelectAll, btnDeletePlan;
    private RecyclerView recyclerView;
    private ShimmerFrameLayout shimmerViewContainer;
    private ProgressBar progressBar;
    private TextView tvStatus, tvEmpty;
    private View layoutEmpty;

    private final ArrayList<String> arraySeason = new ArrayList<>();
    private final ArrayList<String> arrayStage  = new ArrayList<>();
    private final ArrayList<String> arrayModel  = new ArrayList<>();
    private final ArrayList<String> arrayTeam  = new ArrayList<>();
    private final ArrayList<String> arrayDev  = new ArrayList<>();
    private final ArrayList<String> arrayStyleNo = new ArrayList<>();

    private final ArrayList<CfmItem> cfmList = new ArrayList<>();
    private final ArrayList<CfmItem> fullCfmList = new ArrayList<>();
    private CfmAdapter adapter;

    // Chỉ hiện empty state sau khi user đã thực sự nhấn Load ít nhất 1 lần
    private boolean hasLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        acSeason    = findViewById(R.id.acSeason);
        acStyleNo   = findViewById(R.id.acStyleNo);
        acStage      = findViewById(R.id.acStage);
        acModel      = findViewById(R.id.acModel);
        acTeam       = findViewById(R.id.acTeam);
        acDev        = findViewById(R.id.acDev);
        btnRetrieve   = findViewById(R.id.btnRetrieve);
        btnClearFilter = findViewById(R.id.btnClearFilter);
        btnSelectAll  = findViewById(R.id.btnSelectAll);
        btnDeletePlan = findViewById(R.id.btnDeletePlan);
        recyclerView  = findViewById(R.id.recyclerView);
        shimmerViewContainer = findViewById(R.id.shimmerViewContainer);
        progressBar   = findViewById(R.id.progressBar);
        tvStatus      = findViewById(R.id.tvStatus);
        tvEmpty       = findViewById(R.id.tvEmpty);
        layoutEmpty   = findViewById(R.id.layoutEmpty);

        if (btnSelectAll != null) {
            btnSelectAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSelectAllHasPlan();
                }
            });
        }

        if (btnDeletePlan != null) {
            btnDeletePlan.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmAndBulkDeletePlan();
                }
            });
        }

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
        adapter.setOnSelectionChangedListener(new CfmAdapter.OnSelectionChanged() {
            @Override
            public void onChanged() {
                updateButtonState();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        // Khởi tạo trạng thái disable ban đầu cho 2 nút
        updateButtonState();

        // Khởi tạo tính năng gợi ý AutoComplete độc lập cho 6 ô (không ràng buộc)
        setupAutoComplete(acSeason);
        setupAutoComplete(acStyleNo);
        setupAutoComplete(acStage);
        setupAutoComplete(acModel);
        setupAutoComplete(acTeam);
        setupAutoComplete(acDev);

        btnRetrieve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                String rawSeason = acSeason.getText().toString();
                String normSeason = normalizeSeason(rawSeason);
                if (!rawSeason.equals(normSeason)) {
                    acSeason.setText(normSeason, false);
                }
                new RetrieveCfm().execute();
            }
        });

        btnClearFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acSeason.setText("", false);
                acStyleNo.setText("", false);
                acStage.setText("", false);
                acModel.setText("", false);
                acTeam.setText("", false);
                acDev.setText("", false);

                cfmList.clear();
                fullCfmList.clear();
                hasLoaded = false;  // Reset cờ để ẩn empty state sau khi clear
                spinnerPlanFilter.setSelection(0);
                adapter.notifyDataSetChanged();
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                setStatus("Sẵn sàng.");
                updateButtonState();
            }
        });

        // Nạp độc lập 1 lần duy nhất cho toàn bộ danh sách các bộ lọc
        initAllFilterOptions();

        // Kiểm tra cập nhật phiên bản mới khi mở app
        AppUpdater.checkForUpdate(this);
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
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
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



    // ----- Chức năng Chọn tất cả CFM Has Plan (Slide 2 - Revision 0723) -----
    private void toggleSelectAllHasPlan() {
        if (cfmList.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu CFM.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra xem đã chọn tất cả CFM có Kế hoạch chưa
        boolean allSelected = true;
        int hasPlanCount = 0;
        for (CfmItem item : cfmList) {
            if (item.hasPlan == 1) {
                hasPlanCount++;
                if (!item.isSelected) {
                    allSelected = false;
                }
            }
        }

        if (hasPlanCount == 0) {
            Toast.makeText(this, "Không có CFM nào có Kế hoạch (Has Plan) để chọn.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean targetState = !allSelected;
        for (CfmItem item : cfmList) {
            if (item.hasPlan == 1) {
                item.isSelected = targetState;
            }
        }

        adapter.notifyDataSetChanged();
        updateButtonState();  // Cập nhật nút xóa ngay sau khi chọn tất cả
        Toast.makeText(this, (targetState ? "Đã chọn tất cả " : "Đã bỏ chọn ") + hasPlanCount + " CFM có Kế hoạch.", Toast.LENGTH_SHORT).show();
    }

    // ----- Cập nhật trạng thái enable/disable + màu cho 2 nút -----
    private void updateButtonState() {
        // Đếm số CFM có plan và số CFM đang được chọn
        int hasPlanCount = 0;
        int selectedCount = 0;
        for (CfmItem item : cfmList) {
            if (item.hasPlan == 1) {
                hasPlanCount++;
                if (item.isSelected) selectedCount++;
            }
        }

        // Màu xanh đậm khi enabled, xám khi disabled
        int colorEnabled   = android.graphics.Color.parseColor("#1565C0");
        int colorDisabled  = android.graphics.Color.parseColor("#BDBDBD");
        int textEnabled    = android.graphics.Color.WHITE;
        int textDisabled   = android.graphics.Color.parseColor("#757575");

        // --- Nút "Chọn tất cả": enable khi có ít nhất 1 hasPlan ---
        boolean canSelectAll = hasPlanCount > 0;
        if (btnSelectAll != null) {
            btnSelectAll.setEnabled(canSelectAll);
            btnSelectAll.setBackgroundColor(canSelectAll ? colorEnabled : colorDisabled);
            btnSelectAll.setTextColor(canSelectAll ? textEnabled : textDisabled);
        }

        // --- Nút "Xóa kế hoạch": enable khi có ít nhất 1 item được chọn ---
        boolean canDelete = selectedCount > 0;
        if (btnDeletePlan != null) {
            btnDeletePlan.setEnabled(canDelete);
            btnDeletePlan.setBackgroundColor(canDelete ? android.graphics.Color.parseColor("#C62828") : colorDisabled);
            btnDeletePlan.setTextColor(canDelete ? textEnabled : textDisabled);
        }
    }

    // ----- Chức năng Xóa Kế Hoạch Hàng Loạt với Custom Dialog -----
    private void confirmAndBulkDeletePlan() {
        final ArrayList<CfmItem> selectedItems = new ArrayList<>();
        final ArrayList<String> selectedIds = new ArrayList<>();
        boolean hasAnyProdData = false;

        for (CfmItem item : cfmList) {
            if (item.hasPlan == 1 && item.isSelected) {
                selectedItems.add(item);
                selectedIds.add(item.cfmId);
                if (item.hasProductionData()) {
                    hasAnyProdData = true;
                }
            }
        }

        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 1 CFM (có Plan) để xóa kế hoạch.", Toast.LENGTH_LONG).show();
            return;
        }

        // Tạo Custom Dialog
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete_plan, null);
        dialog.setView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        TextView tvQuestion = dialogView.findViewById(R.id.tvDialogQuestion);
        LinearLayout layoutCfmContainer = dialogView.findViewById(R.id.layoutCfmContainer);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        Button btnDeletePlanOnly = dialogView.findViewById(R.id.btnDeletePlanOnly);
        Button btnDeletePlanAndProd = dialogView.findViewById(R.id.btnDeletePlanAndProd);

        tvSubtitle.setText("Danh sách " + selectedItems.size() + " CFM được chọn xóa:");

        // Dynamically add CFM preview cards into scroll container
        android.view.LayoutInflater inflater = getLayoutInflater();
        for (CfmItem item : selectedItems) {
            View itemView = inflater.inflate(R.layout.item_dialog_cfm_preview, layoutCfmContainer, false);

            TextView tvPreviewProdTag = itemView.findViewById(R.id.tvPreviewProdTag);
            TextView tvSeasonVal = itemView.findViewById(R.id.tvSeasonVal);
            TextView tvModelVal = itemView.findViewById(R.id.tvModelVal);
            TextView tvStageVal = itemView.findViewById(R.id.tvStageVal);
            TextView tvStyleVal = itemView.findViewById(R.id.tvStyleVal);
            TextView tvQtyVal = itemView.findViewById(R.id.tvQtyVal);

            // Bind values
            tvSeasonVal.setText(item.season.isEmpty() ? "-" : item.season);
            tvModelVal.setText(item.modelName.isEmpty() ? "-" : item.modelName);
            tvStageVal.setText(item.currentStage.isEmpty() ? "-" : item.currentStage);
            tvStyleVal.setText(item.styleNo.isEmpty() ? "-" : item.styleNo);
            tvQtyVal.setText(item.qtyWorking.isEmpty() ? "-" : item.qtyWorking);

            if (item.hasProductionData()) {
                tvPreviewProdTag.setVisibility(View.VISIBLE);
            } else {
                tvPreviewProdTag.setVisibility(View.GONE);
            }

            layoutCfmContainer.addView(itemView);
        }

        if (selectedItems.size() > 3) {
            android.widget.ScrollView scrollViewCfm = dialogView.findViewById(R.id.scrollViewCfm);
            if (scrollViewCfm != null) {
                int maxHeightPx = (int) (240 * getResources().getDisplayMetrics().density);
                scrollViewCfm.getLayoutParams().height = maxHeightPx;
            }
        }

        if (!hasAnyProdData) {
            // Case 1: Chưa có số liệu sản xuất
            tvTitle.setText("Xác nhận xóa kế hoạch");
            tvQuestion.setText("Bạn có chắc chắn muốn xóa toàn bộ kế hoạch của các CFM này không?");
            btnDeletePlanAndProd.setVisibility(View.GONE);
            btnDeletePlanOnly.setText("XÓA KẾ HOẠCH");
        } else {
            // Case 2: Đã có sản lượng sản xuất -> Cảnh báo 2 tùy chọn xóa
            tvTitle.setText("⚠️ CFM đã có số liệu sản xuất");
            tvQuestion.setText("Một số CFM đã có sản lượng thực tế. Chọn phương thức xóa:");
            btnDeletePlanAndProd.setVisibility(View.VISIBLE);
            btnDeletePlanOnly.setText("CHỈ XÓA KH");
            btnDeletePlanAndProd.setText("XÓA KH + SX");
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnDeletePlanOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                new BulkDeletePlanTask(selectedIds).execute();
            }
        });

        btnDeletePlanAndProd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                new BulkDeletePlanAndProdTask(selectedIds).execute();
            }
        });

        dialog.show();
    }

    private class BulkDeletePlanTask extends AsyncTask<Void, Void, String> {
        private final ArrayList<String> selectedIds;

        public BulkDeletePlanTask(ArrayList<String> selectedIds) {
            this.selectedIds = selectedIds;
        }

        @Override
        protected void onPreExecute() {
            showLoading(true);
            setStatus("Đang xóa kế hoạch...");
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                JSONObject body = new JSONObject();
                JSONArray idsArr = new JSONArray();
                for (String id : selectedIds) {
                    idsArr.put(id);
                }
                body.put("CFM_IDS", idsArr);

                HttpHandler sh = new HttpHandler();
                String resp = sh.makePostCall(Config.DELETE_CFM_PLAN_BULK, body.toString());
                if (resp == null) {
                    return "Lỗi kết nối server khi xóa kế hoạch.";
                }

                JSONObject obj = new JSONObject(resp);
                if (!obj.optString("result", "").equalsIgnoreCase("OK")) {
                    return obj.optString("msg", "Xóa kế hoạch thất bại.");
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
                Toast.makeText(MainActivity.this, "Đã xóa thành công kế hoạch của " + selectedIds.size() + " CFM!", Toast.LENGTH_LONG).show();
                new RetrieveCfm().execute();
            } else {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                setStatus(result);
            }
        }
    }

    /** Xóa cả kế hoạch + reset toàn bộ số liệu sản xuất về 0 */
    private class BulkDeletePlanAndProdTask extends AsyncTask<Void, Void, String> {
        private final ArrayList<String> selectedIds;

        public BulkDeletePlanAndProdTask(ArrayList<String> selectedIds) {
            this.selectedIds = selectedIds;
        }

        @Override
        protected void onPreExecute() {
            showLoading(true);
            setStatus("Đang xóa kế hoạch và số liệu sản xuất...");
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                JSONObject body = new JSONObject();
                JSONArray idsArr = new JSONArray();
                for (String id : selectedIds) {
                    idsArr.put(id);
                }
                body.put("CFM_IDS", idsArr);

                HttpHandler sh = new HttpHandler();
                String resp = sh.makePostCall(Config.DELETE_CFM_PLAN_AND_PROD_BULK, body.toString());
                if (resp == null) {
                    return "Lỗi kết nối server.";
                }

                JSONObject obj = new JSONObject(resp);
                if (!obj.optString("result", "").equalsIgnoreCase("OK")) {
                    return obj.optString("msg", "Xóa thất bại.");
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
                Toast.makeText(MainActivity.this,
                        "Đã xóa kế hoạch + số liệu sản xuất của " + selectedIds.size() + " CFM!",
                        Toast.LENGTH_LONG).show();
                new RetrieveCfm().execute();
            } else {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                setStatus(result);
            }
        }
    }

    // ----- Menu khi chon 1 cardview -----
    private void showItemMenu(final CfmItem item) {
        Intent it = new Intent(MainActivity.this, UpdateCfmActivity.class);
        it.putExtra("CFM_ID", item.cfmId);
        it.putExtra("MODEL_NAME", item.modelName);
        it.putExtra("STYLE_NO", item.styleNo);
        it.putExtra("QTY_WORKING", item.qtyWorking);
        startActivityForResult(it, REQUEST_UPDATE_CFM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UPDATE_CFM && resultCode == RESULT_OK) {
            // Tự động reload lại danh sách CFM sau khi lưu tiến độ thành công
            new RetrieveCfm().execute();
        }
    }

    // ========================= Nạp bộ lọc độc lập (Tách biệt 100%) =========================

    private void initAllFilterOptions() {
        // 1. Nạp Seasons tĩnh
        arraySeason.clear();
        arraySeason.add("S127");
        arraySeason.add("S227");
        arraySeason.add("S128");
        arraySeason.add("S228");
        arraySeason.add("S129");
        arraySeason.add("S229");
        arraySeason.add("S130");
        arraySeason.add("S230");
        arraySeason.add("S131");
        arraySeason.add("S231");
        arraySeason.add("S132");
        arraySeason.add("S232");
        arraySeason.add("S133");
        arraySeason.add("S233");
        arraySeason.add("S134");
        arraySeason.add("S234");
        arraySeason.add("S135");
        ArrayAdapter<String> adSeason = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arraySeason);
        acSeason.setAdapter(adSeason);

        // 2. Nạp công đoạn (Stages) chuẩn + động từ DB
        new LoadAllStages().execute();

        // 3. Nạp song song toàn bộ Style, Model, Team, Dev độc lập 1 lần từ server
        new LoadAllStyles().execute();
        new LoadAllModels().execute();
        new LoadAllTeams().execute();
        new LoadAllDevs().execute();
    }

    private static final String[] STANDARD_STAGES = {
        "1ST PROTO", "2ND PROTO", "3RD PROTO", "PROTO 0",
        "Pullover", "PROJECT CFM", "PRODUCTION CFM",
        "WEAR TEST", "SALES SAMPLE", "EXT", "XTR"
    };

    private class LoadAllStages extends AsyncTask<Void, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_STAGES; // Gọi API lấy toàn bộ Stages từ DB
                Log.d("Debug", "Load All Stages URL: " + url);
                String jsonStr = sh.makeServiceCall(url);

                ArrayList<String> stages = new ArrayList<>();
                for (String stg : STANDARD_STAGES) {
                    stages.add(stg);
                }

                if (jsonStr != null) {
                    JSONArray arr = new JSONArray(jsonStr);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        String stage = c.optString("CURRENT_STAGE", "").trim();
                        if (!stage.isEmpty()) {
                            boolean exists = false;
                            for (String s : stages) {
                                if (s.equalsIgnoreCase(stage) || s.replace(" ", "").equalsIgnoreCase(stage.replace(" ", ""))) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                stages.add(stage);
                            }
                        }
                    }
                }

                arrayStage.clear();
                arrayStage.addAll(stages);
            } catch (Exception e) {
                error = "Lỗi đọc Stage: " + e.getMessage();
                Log.e("LoadAllStages", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error == null && !arrayStage.isEmpty()) {
                ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayStage);
                acStage.setAdapter(ad);
            }
        }
    }

    private class LoadAllStyles extends AsyncTask<Void, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_STYLES; // Gọi API không tham số -> lấy toàn bộ Styles
                Log.d("Debug", "Load All Styles URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr != null) {
                    JSONArray arr = new JSONArray(jsonStr);
                    arrayStyleNo.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        String style = c.optString("STYLE_NO", "").trim();
                        if (!style.isEmpty() && !arrayStyleNo.contains(style)) {
                            arrayStyleNo.add(style);
                        }
                    }
                }
            } catch (Exception e) {
                error = "Lỗi đọc Style: " + e.getMessage();
                Log.e("LoadAllStyles", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error == null && !arrayStyleNo.isEmpty()) {
                ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayStyleNo);
                acStyleNo.setAdapter(ad);
            }
        }
    }

    private class LoadAllModels extends AsyncTask<Void, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_MODELS; // Gọi API không tham số -> lấy toàn bộ Models
                Log.d("Debug", "Load All Models URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr != null) {
                    JSONArray arr = new JSONArray(jsonStr);
                    arrayModel.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        String model = c.optString("MODEL_NAME", "").trim();
                        if (!model.isEmpty() && !arrayModel.contains(model)) {
                            arrayModel.add(model);
                        }
                    }
                }
            } catch (Exception e) {
                error = "Lỗi đọc Model: " + e.getMessage();
                Log.e("LoadAllModels", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error == null && !arrayModel.isEmpty()) {
                ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayModel);
                acModel.setAdapter(ad);
            }
        }
    }

    private class LoadAllTeams extends AsyncTask<Void, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_TEAMS; // Gọi API không tham số -> lấy toàn bộ Teams
                Log.d("Debug", "Load All Teams URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr != null) {
                    JSONArray arr = new JSONArray(jsonStr);
                    arrayTeam.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        String team = c.optString("VS_TEAM", c.optString("VALUE", "")).trim();
                        if (!team.isEmpty() && !arrayTeam.contains(team)) {
                            arrayTeam.add(team);
                        }
                    }
                }
            } catch (Exception e) {
                error = "Lỗi đọc Team: " + e.getMessage();
                Log.e("LoadAllTeams", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error == null && !arrayTeam.isEmpty()) {
                ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayTeam);
                acTeam.setAdapter(ad);
            }
        }
    }

    private class LoadAllDevs extends AsyncTask<Void, Void, Void> {
        private String error = null;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                HttpHandler sh = new HttpHandler();
                String url = Config.GET_DEVS; // Gọi API không tham số -> lấy toàn bộ Devs
                Log.d("Debug", "Load All Devs URL: " + url);
                String jsonStr = sh.makeServiceCall(url);
                if (jsonStr != null) {
                    JSONArray arr = new JSONArray(jsonStr);
                    arrayDev.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        String dev = c.optString("VS_DEVELOPER", c.optString("VALUE", "")).trim();
                        if (!dev.isEmpty() && !arrayDev.contains(dev)) {
                            arrayDev.add(dev);
                        }
                    }
                }
            } catch (Exception e) {
                error = "Lỗi đọc Dev: " + e.getMessage();
                Log.e("LoadAllDevs", error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (error == null && !arrayDev.isEmpty()) {
                ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, arrayDev);
                acDev.setAdapter(ad);
            }
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
                String season   = normalizeSeason(acSeason.getText().toString().trim());
                String styleNo  = acStyleNo.getText().toString().trim();
                String stage    = acStage.getText().toString().trim();
                String model    = acModel.getText().toString().trim();
                String team     = acTeam.getText().toString().trim();
                String dev      = acDev.getText().toString().trim();

                if (season.isEmpty()) season = "%";
                if (styleNo.isEmpty()) styleNo = "%";
                if (stage.isEmpty()) stage = "%";
                if (model.isEmpty()) model = "%";
                if (model.contains(" / ")) {
                    model = model.split(" / ")[0].trim();
                }
                if (team.isEmpty()) team = "%";
                if (dev.isEmpty()) dev = "%";

                HttpHandler sh = new HttpHandler();
                String url = Config.GET_CFM_LIST
                        + "?season="      + HttpHandler.enc(season)
                        + "&style_no="    + HttpHandler.enc(styleNo)
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
            hasLoaded = true;   // Đánh dấu đã load ít nhất 1 lần → cho phép hiện empty state
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
            // Chỉ hiện empty state nếu đã từng load (không hiện ngay khi mở app)
            if (hasLoaded) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("Không có dữ liệu.");
                recyclerView.setVisibility(View.GONE);
                setStatus("Tổng: 0 CFM.");
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                setStatus("Sẵn sàng.");
            }
            updateButtonState();
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
        recyclerView.scrollToPosition(0);

        if (cfmList.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Không có dữ liệu phù hợp.");
            recyclerView.setVisibility(View.GONE);
            setStatus("Tổng: 0 CFM.");
        } else {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            setStatus("Tổng: " + cfmList.size() + " CFM.");
        }
        // Cập nhật trạng thái 2 nút sau khi dữ liệu thay đổi
        updateButtonState();
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

    private void setupAutoComplete(final AutoCompleteTextView ac) {
        // Mở dropdown hiển thị danh sách khi click
        ac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ac.showDropDown();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(ac, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        // Mở dropdown khi ô nhập nhận focus
        ac.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    ac.showDropDown();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(ac, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                } else {
                    if (ac == acSeason) {
                        String rawVal = ac.getText().toString();
                        String normVal = normalizeSeason(rawVal);
                        if (!rawVal.equals(normVal)) {
                            ac.setText(normVal, false);
                        }
                    }
                }
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
                            
                            // Reset bộ lọc của adapter về rỗng ngầm để hiển thị toàn bộ phần tử trong lần click sau
                            android.widget.ListAdapter adapter = ac.getAdapter();
                            if (adapter instanceof android.widget.Filterable) {
                                ((android.widget.Filterable) adapter).getFilter().filter("");
                            }
                            
                            ac.dismissDropDown();
                            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.hideSoftInputFromWindow(ac.getWindowToken(), 0);
                            }
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    private String normalizeSeason(String val) {
        if (val == null) return "";
        val = val.trim();
        if (val.isEmpty() || val.equals("%")) return val;
        if (val.toLowerCase().startsWith("s")) {
            return "S" + val.substring(1).toUpperCase();
        }
        if (val.matches("\\d+")) {
            return "S" + val;
        }
        return val.toUpperCase();
    }

}