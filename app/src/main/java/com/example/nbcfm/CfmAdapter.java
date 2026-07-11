package com.example.nbcfm;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CfmAdapter extends RecyclerView.Adapter<CfmAdapter.VH> {

    public interface OnItemClick {
        void onClick(CfmItem item);
    }

    private final List<CfmItem> data;
    private final OnItemClick listener;

    public CfmAdapter(Context ctx, List<CfmItem> data, OnItemClick listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cfm_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final CfmItem it = data.get(position);

        // --- BẮT ĐẦU PHẦN HIGHLIGHT ---
        // Ép kiểu root view sang CardView để đổi màu nền thẻ
        androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) h.itemView;
        if (it.hasPlan == 1) {
            // Màu xanh lá nhạt cho tất cả CFM đã có plan
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"));
            h.tvCfmId.setText("CFM ID: " + it.cfmId + " (Has Plan)");
        } else {
            // Trả về màu trắng mặc định cho các dòng chưa có kế hoạch
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
            h.tvCfmId.setText("CFM ID: " + it.cfmId);
        }
        // --- KẾT THÚC PHẦN HIGHLIGHT ---
        
        // Bind highlighted fields
        h.tvSeasonVal.setText(it.season.isEmpty() ? "-" : it.season);
        h.tvModelVal.setText(it.modelName.isEmpty() ? "-" : it.modelName);
        h.tvStageVal.setText(it.currentStage.isEmpty() ? "-" : it.currentStage);
        h.tvStyleVal.setText(it.styleNo.isEmpty() ? "-" : it.styleNo);
        h.tvQtyVal.setText(it.qtyWorking.isEmpty() ? "-" : it.qtyWorking);

        // Bind secondary details in a clean 2-column grid
        h.tvBrand.setText(formatField("Brand", it.brandCode));
        h.tvGender.setText(formatField("Gender", it.gender));
        h.tvLast.setText(formatField("Last", it.lastName));
        h.tvTpDate.setText(formatField("TP Date", it.tpDate));
        h.tvMoldNew.setText(formatField("Mold New", it.moldNew));
        h.tvMoldExist.setText(formatField("Mold Exist", it.moldExist));
        h.tvVsDev.setText(formatField("VS Dev", it.vsDeveloper));
        h.tvSiteDev.setText(formatField("Site Dev", it.siteDeveloper));
        h.tvSpecIssue.setText(formatField("Spec Issue", it.specIssue));
        h.tvMtlArrived.setText(formatField("Mtl Arrived", it.mtlArrived));
        h.tvQtyShipping.setText(formatField("Qty Shipping", it.qtyShipping));

        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onClick(it);
            }
        });
    }

    private SpannableStringBuilder formatField(String label, String value) {
        if (value == null || value.isEmpty()) {
            value = "-";
        }
        String fullText = label + ": " + value;
        SpannableStringBuilder ssb = new SpannableStringBuilder(fullText);
        int colonIndex = label.length() + 2; // "Label: "
        ssb.setSpan(new ForegroundColorSpan(0xFF616161), 0, colonIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(0xFF212121), colonIndex, fullText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), colonIndex, fullText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCfmId, tvSeasonVal, tvModelVal, tvStageVal, tvStyleVal, tvQtyVal;
        TextView tvBrand, tvGender, tvLast, tvTpDate, tvMoldNew, tvMoldExist, tvVsDev, tvSiteDev, tvSpecIssue, tvMtlArrived, tvQtyShipping;

        VH(@NonNull View v) {
            super(v);
            tvCfmId       = v.findViewById(R.id.tvCfmId);
            tvSeasonVal   = v.findViewById(R.id.tvSeasonVal);
            tvModelVal    = v.findViewById(R.id.tvModelVal);
            tvStageVal    = v.findViewById(R.id.tvStageVal);
            tvStyleVal    = v.findViewById(R.id.tvStyleVal);
            tvQtyVal      = v.findViewById(R.id.tvQtyVal);
            
            tvBrand       = v.findViewById(R.id.tvBrand);
            tvGender      = v.findViewById(R.id.tvGender);
            tvLast        = v.findViewById(R.id.tvLast);
            tvTpDate      = v.findViewById(R.id.tvTpDate);
            tvMoldNew     = v.findViewById(R.id.tvMoldNew);
            tvMoldExist   = v.findViewById(R.id.tvMoldExist);
            tvVsDev       = v.findViewById(R.id.tvVsDev);
            tvSiteDev     = v.findViewById(R.id.tvSiteDev);
            tvSpecIssue   = v.findViewById(R.id.tvSpecIssue);
            tvMtlArrived  = v.findViewById(R.id.tvMtlArrived);
            tvQtyShipping = v.findViewById(R.id.tvQtyShipping);
        }
    }
}
