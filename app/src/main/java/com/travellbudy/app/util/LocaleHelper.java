package com.travellbudy.app.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import java.util.Locale;

public class LocaleHelper {

    private static final Locale BULGARIAN = new Locale("bg");

    public static Context wrap(Context context) {
        Locale.setDefault(BULGARIAN);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(BULGARIAN);
        config.setLocales(new LocaleList(BULGARIAN));

        return context.createConfigurationContext(config);
    }

    public static void applyLocale(Context context) {
        Locale.setDefault(BULGARIAN);

        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(BULGARIAN);
        config.setLocales(new LocaleList(BULGARIAN));
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}


