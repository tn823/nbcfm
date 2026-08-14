package com.example.nbcfm;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class StatusDialogHelper {

    public enum DialogType {
        SUCCESS,
        WARNING,
        ERROR
    }

    private static View currentSuccessToastView = null;
    private static Handler toastHandler = null;
    private static Runnable dismissRunnable = null;

    /**
     * Hiển thị thông báo Thành công ở góc trên bên phải (Top-Right Toast / Banner).
     * Tự động biến mất sau 3.2 giây hoặc bấm nút 'X' để đóng ngay lập tức.
     */
    public static void showSuccess(Context context, String message, Runnable onConfirm) {
        showSuccess(context, "Thành công", message, onConfirm);
    }

    public static void showSuccess(final Context context, final String title, final String message, final Runnable onConfirm) {
        if (context == null) return;

        // Nếu context không phải Activity thì fallback về dialog thông thường
        if (!(context instanceof Activity)) {
            showDialog(context, DialogType.SUCCESS, title, message, "ĐỒNG Ý", onConfirm);
            return;
        }

        final Activity activity = (Activity) context;
        if (activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ViewGroup decorView = (ViewGroup) activity.findViewById(android.R.id.content);
                    if (decorView == null) return;

                    // Xóa toast thông báo cũ nếu đang hiển thị
                    if (currentSuccessToastView != null) {
                        if (toastHandler != null && dismissRunnable != null) {
                            toastHandler.removeCallbacks(dismissRunnable);
                        }
                        try {
                            decorView.removeView(currentSuccessToastView);
                        } catch (Exception ignored) {}
                        currentSuccessToastView = null;
                    }

                    final View toastView = LayoutInflater.from(activity).inflate(R.layout.layout_top_success_notification, decorView, false);
                    currentSuccessToastView = toastView;

                    TextView tvTitle   = toastView.findViewById(R.id.tvToastTitle);
                    TextView tvMessage = toastView.findViewById(R.id.tvToastMessage);
                    ImageView ivClose  = toastView.findViewById(R.id.ivCloseToast);

                    tvTitle.setText(title != null && !title.isEmpty() ? title : "Thành công");
                    tvMessage.setText(message != null ? message : "");

                    // Canh chỉnh vị trí: Góc trên bên phải (Top - Right)
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.gravity = Gravity.TOP | Gravity.END;
                    int marginEnd = (int) (12 * activity.getResources().getDisplayMetrics().density);
                    int marginTop = (int) (16 * activity.getResources().getDisplayMetrics().density);
                    int marginStart = (int) (48 * activity.getResources().getDisplayMetrics().density);
                    lp.setMargins(marginStart, marginTop, marginEnd, 0);
                    toastView.setLayoutParams(lp);

                    decorView.addView(toastView);

                    // Hiệu ứng trượt xuống & mờ dần (Slide down & Fade in)
                    toastView.setAlpha(0f);
                    toastView.setTranslationY(-50f);
                    toastView.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(260)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();

                    // Tự động biến mất sau 3.2 giây
                    toastHandler = new Handler(Looper.getMainLooper());
                    dismissRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (currentSuccessToastView == toastView) {
                                toastView.animate()
                                        .alpha(0f)
                                        .translationY(-50f)
                                        .setDuration(200)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    decorView.removeView(toastView);
                                                } catch (Exception ignored) {}
                                                if (currentSuccessToastView == toastView) {
                                                    currentSuccessToastView = null;
                                                }
                                            }
                                        }).start();
                            }
                        }
                    };
                    toastHandler.postDelayed(dismissRunnable, 3200);

                    // Nút 'X' đóng ngay lập tức
                    ivClose.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (toastHandler != null && dismissRunnable != null) {
                                toastHandler.removeCallbacks(dismissRunnable);
                            }
                            dismissRunnable.run();
                        }
                    });

                    // Thực thi callback (nếu có)
                    if (onConfirm != null) {
                        onConfirm.run();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void showWarning(Context context, String message, Runnable onConfirm) {
        showWarning(context, "Cảnh báo", message, onConfirm);
    }

    public static void showWarning(Context context, String title, String message, Runnable onConfirm) {
        showDialog(context, DialogType.WARNING, title, message, "HIỂU RỒI", onConfirm);
    }

    public static void showError(Context context, String message, Runnable onConfirm) {
        showError(context, "Lỗi / Thất bại", message, onConfirm);
    }

    public static void showError(Context context, String title, String message, Runnable onConfirm) {
        showDialog(context, DialogType.ERROR, title, message, "ĐÓNG", onConfirm);
    }

    /**
     * Hiển thị Hộp thoại Popup ở giữa màn hình (dành cho Cảnh báo và Báo lỗi).
     */
    public static void showDialog(Context context, DialogType type, String title, String message, String buttonText, final Runnable onConfirm) {
        if (context == null) return;
        if (context instanceof Activity) {
            Activity act = (Activity) context;
            if (act.isFinishing()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && act.isDestroyed()) return;
        }

        try {
            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_status_popup);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                int dialogWidth = (int) (metrics.widthPixels * 0.85); // 85% chiều rộng màn hình
                int maxWidth = (int) (380 * metrics.density);         // Giới hạn tối đa 380dp
                if (dialogWidth > maxWidth) {
                    dialogWidth = maxWidth;
                }

                android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
                lp.copyFrom(dialog.getWindow().getAttributes());
                lp.width = dialogWidth;
                lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = Gravity.CENTER;
                dialog.getWindow().setAttributes(lp);
            }

            FrameLayout layoutIconContainer = dialog.findViewById(R.id.layoutIconContainer);
            ImageView ivPopupIcon           = dialog.findViewById(R.id.ivPopupIcon);
            TextView tvPopupTitle           = dialog.findViewById(R.id.tvPopupTitle);
            TextView tvPopupMessage         = dialog.findViewById(R.id.tvPopupMessage);
            Button btnPopupAction           = dialog.findViewById(R.id.btnPopupAction);

            tvPopupTitle.setText(title != null && !title.isEmpty() ? title : "Thông báo");
            tvPopupMessage.setText(message != null ? message : "");
            btnPopupAction.setText(buttonText != null && !buttonText.isEmpty() ? buttonText : "ĐÓNG");

            switch (type) {
                case SUCCESS:
                    layoutIconContainer.setBackgroundResource(R.drawable.bg_icon_circle_success);
                    ivPopupIcon.setImageResource(R.drawable.ic_popup_success);
                    btnPopupAction.setBackgroundResource(R.drawable.btn_popup_success);
                    break;
                case WARNING:
                    layoutIconContainer.setBackgroundResource(R.drawable.bg_icon_circle_warning);
                    ivPopupIcon.setImageResource(R.drawable.ic_popup_warning);
                    btnPopupAction.setBackgroundResource(R.drawable.btn_popup_warning);
                    break;
                case ERROR:
                default:
                    layoutIconContainer.setBackgroundResource(R.drawable.bg_icon_circle_error);
                    ivPopupIcon.setImageResource(R.drawable.ic_popup_error);
                    btnPopupAction.setBackgroundResource(R.drawable.btn_popup_error);
                    break;
            }

            btnPopupAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {}
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                }
            });

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
