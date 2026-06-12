package com.example.nbcfm;

import android.content.Context;
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

        h.tvCfmId.setText("CFM ID: " + it.cfmId);
        h.tvHeader.setText(it.modelName + "  •  " + it.styleNo);
        h.tvSeason.setText(label("Season", it.season) + "    " + label("Brand", it.brandCode));
        h.tvStage.setText(label("Stage", it.currentStage) + "    " + label("Gender", it.gender));
        h.tvLast.setText(label("Last", it.lastName) + "    " + label("TP Date", it.tpDate));
        h.tvMold.setText(label("Mold New", it.moldNew) + "    " + label("Mold Exist", it.moldExist));
        h.tvDev.setText(label("VS Dev", it.vsDeveloper) + "    " + label("Site Dev", it.siteDeveloper));
        h.tvSpec.setText(label("Spec Issue", it.specIssue) + "    " + label("Mtl Arrived", it.mtlArrived));
        h.tvQty.setText(label("Qty Working", it.qtyWorking) + "    " + label("Qty Shipping", it.qtyShipping));

        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onClick(it);
            }
        });
    }

    private String label(String k, String v) {
        return k + ": " + (v == null || v.isEmpty() ? "-" : v);
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCfmId, tvHeader, tvSeason, tvStage, tvLast, tvMold, tvDev, tvSpec, tvQty;

        VH(@NonNull View v) {
            super(v);
            tvCfmId  = v.findViewById(R.id.tvCfmId);
            tvHeader = v.findViewById(R.id.tvHeader);
            tvSeason = v.findViewById(R.id.tvSeason);
            tvStage  = v.findViewById(R.id.tvStage);
            tvLast   = v.findViewById(R.id.tvLast);
            tvMold   = v.findViewById(R.id.tvMold);
            tvDev    = v.findViewById(R.id.tvDev);
            tvSpec   = v.findViewById(R.id.tvSpec);
            tvQty    = v.findViewById(R.id.tvQty);
        }
    }
}
