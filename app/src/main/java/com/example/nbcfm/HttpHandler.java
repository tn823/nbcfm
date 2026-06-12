package com.example.nbcfm;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Network helper. Giữ nguyên style makeServiceCall như bạn đang dùng,
 * bổ sung thêm makePostCall cho việc cập nhật dữ liệu.
 */
public class HttpHandler {

    private static final String TAG = "HttpHandler";
    private static final int TIMEOUT = 15000;

    public HttpHandler() {
    }

    /** GET - trả về chuỗi JSON, null nếu lỗi. */
    public String makeServiceCall(String reqUrl) {
        String response = null;
        try {
            URL url = new URL(reqUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            int code = conn.getResponseCode();
            InputStream in;
            if (code >= 200 && code < 400) {
                in = conn.getInputStream();
            } else {
                in = conn.getErrorStream();
            }
            response = readStream(in);
        } catch (Exception e) {
            Log.e(TAG, "makeServiceCall error: " + e.toString());
        }
        return response;
    }

    /** POST - gửi body JSON, trả về chuỗi phản hồi, null nếu lỗi. */
    public String makePostCall(String reqUrl, String jsonBody) {
        String response = null;
        try {
            URL url = new URL(reqUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream in;
            if (code >= 200 && code < 400) {
                in = conn.getInputStream();
            } else {
                in = conn.getErrorStream();
            }
            response = readStream(in);
        } catch (Exception e) {
            Log.e(TAG, "makePostCall error: " + e.toString());
        }
        return response;
    }

    public static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String readStream(InputStream in) throws Exception {
        if (in == null) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }
}
