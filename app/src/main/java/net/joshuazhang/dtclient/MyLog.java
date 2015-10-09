package net.joshuazhang.dtclient;

import android.util.Log;
import android.widget.TextView;

/**
 * 应用内日志，除了写入系统日志外，还会在应用内打印日志方便脱机调试
 * TODO 有待重新规划实现
 */
public class MyLog {
    private static final String LOG_TAG = "DTClient";
    private TextView logTextView;

    public MyLog(TextView tv) {
        logTextView = tv;
    }
    public int v(String msg) {
        logTextView.append(msg + "\n");
        return Log.v(LOG_TAG, msg);
    }

    public int i(String msg) {
        logTextView.append(msg + "\n");
        return Log.i(LOG_TAG, msg);
    }

    public int d(String msg) {
        logTextView.append(msg + "\n");
        return Log.d(LOG_TAG, msg);
    }

    public int e(String msg) {
        logTextView.append(msg + "\n");
        return Log.e(LOG_TAG, msg);
    }

    public int w(String msg) {
        logTextView.append(msg + "\n");
        return Log.w(LOG_TAG, msg);
    }

    public int wtf(String msg) {
        logTextView.append(msg + "\n");
        return Log.wtf(LOG_TAG, msg);
    }
}
