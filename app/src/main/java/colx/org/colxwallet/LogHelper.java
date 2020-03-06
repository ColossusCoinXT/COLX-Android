package colx.org.colxwallet;

import org.slf4j.LoggerFactory;

import global.ILogHelper;

public class LogHelper {
    public enum LogHelperType {
        logcat,
        http;
    }

    public static void setLogType(LogHelperType type) {
        type_ = type;
    }

    public static ILogHelper getLogHelper(Class<?> clazz) {
        return getLogHelper(clazz.getName());
    }

    public static ILogHelper getLogHelper(String name) {
        if (type_ == LogHelperType.http)
            return new LogHelperHTTP(name);
        else
            return new LogHelperDefault(LoggerFactory.getLogger(name));
    }

    private static LogHelperType type_ = LogHelperType.logcat;
}
