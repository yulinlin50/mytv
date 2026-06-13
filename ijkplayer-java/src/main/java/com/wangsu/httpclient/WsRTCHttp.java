package com.wangsu.httpclient;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
public class WsRTCHttp {
    public static final String HTTP_ORIGIN = "https://appr.tc";
    public static final int HTTP_TIMEOUT_MS = 8000;
    public static final String TAG = "WsRTCHttp";

    public static String AndroidHttpGet(String str) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            if (200 == httpURLConnection.getResponseCode()) {
                httpURLConnection.setRequestProperty("Connection", "close");
                return drainStream(httpURLConnection.getInputStream());
            }
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    public static String AndroidHttpGet2(String str, int i) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setConnectTimeout(i);
            httpURLConnection.setReadTimeout(i);
            httpURLConnection.setRequestProperty("Connection", "close");
            return 200 != httpURLConnection.getResponseCode() ? "" : drainStream(httpURLConnection.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    public static String AndroidHttpPost(String str, String str2, int i) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setConnectTimeout(i);
            httpURLConnection.addRequestProperty("origin", HTTP_ORIGIN);
            httpURLConnection.setReadTimeout(i);
            httpURLConnection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            httpURLConnection.setRequestProperty("Connection", "close");
            DataOutputStream dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());
            dataOutputStream.write(str2.getBytes("UTF-8"));
            dataOutputStream.flush();
            dataOutputStream.close();
            return httpURLConnection.getResponseCode() != 200 ? "" : drainStream(httpURLConnection.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    public static long AndroidHttpPost2(String str, byte[] bArr, int i) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setConnectTimeout(i);
            httpURLConnection.addRequestProperty("origin", HTTP_ORIGIN);
            httpURLConnection.setReadTimeout(i);
            httpURLConnection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            httpURLConnection.setRequestProperty("Connection", "close");
            DataOutputStream dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());
            dataOutputStream.write(bArr);
            dataOutputStream.flush();
            dataOutputStream.close();
            return httpURLConnection.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
            return -1L;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    public static String drainStream(InputStream inputStream) {
        Scanner useDelimiter = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
        return useDelimiter.hasNext() ? useDelimiter.next() : "";
    }
}
