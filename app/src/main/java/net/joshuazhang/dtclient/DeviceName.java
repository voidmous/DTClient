package net.joshuazhang.dtclient;

import android.os.Build;

/**
 * 提取设备Model名称作为设备ID
 * 比如LGE-LG-SU640
 */
public class DeviceName {
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return spaceToHyphen(capitalize(model));
        } else {
            return spaceToHyphen(capitalize(manufacturer) + "-" + model);
        }
    }


    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    /*
     *  把空格用连字符代替
     *  https://stackoverflow.com/questions/5262554/replace-space-to-hyphen
     */
    private static String spaceToHyphen(String s) {
        return s.replace(' ', '-');
    }
}
