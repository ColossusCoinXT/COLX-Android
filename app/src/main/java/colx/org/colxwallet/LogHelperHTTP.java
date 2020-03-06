package colx.org.colxwallet;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.io.IOException;

import global.ILogHelper;

public class LogHelperHTTP implements ILogHelper {
    private final static OkHttpClient httpClient_ = new OkHttpClient();
    private final static String enpoint = "http://192.168.1.107:8888/log";

    private String name_;

    private enum LogType {
        trace, info, debug, warning, error
    }

    private void postLogRequest(LogType type, String msg, String[] addInfo)
    {
        try {
            final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("tag", name_);
            jsonObject.put("type", type.toString());
            jsonObject.put("msg", msg);
            if (addInfo != null && addInfo.length > 0)
                jsonObject.put("addinfo", addInfo);

            RequestBody body = RequestBody.create(JSON, jsonObject.toString());
            Request request = new Request.Builder()
                    .url(enpoint)
                    .post(body)
                    .build();

            httpClient_.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    LoggerFactory.getLogger(LogHelperHTTP.class).error(e.getMessage(), e);
                }

                @Override
                public void onResponse(Response response) { }
            });
        } catch (JSONException e) {
            LoggerFactory.getLogger(LogHelperHTTP.class).error(e.getMessage(), e);
        }
    }

    private String[] ConvertThrowableToArray(Throwable t) {
        if (t == null)
            return null;

        StackTraceElement[] st = t.getStackTrace();
        if (st == null || st.length == 0)
            return null;

        String[] strList = new String[st.length];
        for (int i = 0; i < st.length; ++i) strList[i] = st[i].toString();

        return strList;
    }

    LogHelperHTTP(String name) {
        super();
        this.name_ = name;
    }

    @Override
    public String getName() {
        return name_;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public void trace(String msg) {
        postLogRequest(LogType.trace, msg, null);
    }

    @Override
    public void trace(String format, Object arg) {
        postLogRequest(LogType.trace, String.format(format, arg), null);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        postLogRequest(LogType.trace, String.format(format, arg1, arg2), null);
    }

    @Override
    public void trace(String format, Object... arguments) {
        postLogRequest(LogType.trace, String.format(format, arguments), null);
    }

    @Override
    public void trace(String msg, Throwable t) {
        postLogRequest(LogType.trace, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return true;
    }

    @Override
    public void trace(Marker marker, String msg) {
        postLogRequest(LogType.trace, msg, null);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        postLogRequest(LogType.trace, String.format(format, arg), null);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        postLogRequest(LogType.trace, String.format(format, arg1, arg2), null);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        postLogRequest(LogType.trace, String.format(format, argArray), null);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        postLogRequest(LogType.trace, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String msg) {
        postLogRequest(LogType.debug, msg, null);
    }

    @Override
    public void debug(String format, Object arg) {
        postLogRequest(LogType.debug, String.format(format, arg), null);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        postLogRequest(LogType.debug, String.format(format, arg1, arg2), null);
    }

    @Override
    public void debug(String format, Object... arguments) {
        postLogRequest(LogType.debug, String.format(format, arguments), null);
    }

    @Override
    public void debug(String msg, Throwable t) {
        postLogRequest(LogType.debug, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return true;
    }

    @Override
    public void debug(Marker marker, String msg) {
        postLogRequest(LogType.debug, msg, null);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        postLogRequest(LogType.debug, String.format(format, arg), null);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        postLogRequest(LogType.debug, String.format(format, arg1, arg2), null);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        postLogRequest(LogType.debug, String.format(format, arguments), null);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        postLogRequest(LogType.debug, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        postLogRequest(LogType.info, msg, null);
    }

    @Override
    public void info(String format, Object arg) {
        postLogRequest(LogType.info, String.format(format, arg), null);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        postLogRequest(LogType.info, String.format(format, arg1, arg2), null);
    }

    @Override
    public void info(String format, Object... arguments) {
        postLogRequest(LogType.info, String.format(format, arguments), null);
    }

    @Override
    public void info(String msg, Throwable t) {
        postLogRequest(LogType.info, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return true;
    }

    @Override
    public void info(Marker marker, String msg) {
        postLogRequest(LogType.info, msg, null);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        postLogRequest(LogType.info, String.format(format, arg), null);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        postLogRequest(LogType.info, String.format(format, arg1, arg2), null);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        postLogRequest(LogType.info, String.format(format, arguments), null);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        postLogRequest(LogType.info, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        postLogRequest(LogType.warning, msg, null);
    }

    @Override
    public void warn(String format, Object arg) {
        postLogRequest(LogType.warning, String.format(format, arg), null);
    }

    @Override
    public void warn(String format, Object... arguments) {
        postLogRequest(LogType.warning, String.format(format, arguments), null);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        postLogRequest(LogType.warning, String.format(format, arg1, arg2), null);
    }

    @Override
    public void warn(String msg, Throwable t) {
        postLogRequest(LogType.warning, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return true;
    }

    @Override
    public void warn(Marker marker, String msg) {
        postLogRequest(LogType.warning, msg, null);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        postLogRequest(LogType.warning, String.format(format, arg), null);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        postLogRequest(LogType.warning, String.format(format, arg1, arg2), null);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        postLogRequest(LogType.warning, String.format(format, arguments), null);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        postLogRequest(LogType.warning, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        postLogRequest(LogType.error, msg, null);
    }

    @Override
    public void error(String format, Object arg) {
        postLogRequest(LogType.error, String.format(format, arg), null);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        postLogRequest(LogType.error, String.format(format, arg1, arg2), null);
    }

    @Override
    public void error(String format, Object... arguments) {
        postLogRequest(LogType.error, String.format(format, arguments), null);
    }

    @Override
    public void error(String msg, Throwable t) {
        postLogRequest(LogType.error, msg, ConvertThrowableToArray(t));
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return true;
    }

    @Override
    public void error(Marker marker, String msg) {
        postLogRequest(LogType.error, msg, null);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        postLogRequest(LogType.error, String.format(format, arg), null);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        postLogRequest(LogType.error, String.format(format, arg1, arg2), null);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        postLogRequest(LogType.error, String.format(format, arguments), null);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        postLogRequest(LogType.error, msg, ConvertThrowableToArray(t));
    }
}
