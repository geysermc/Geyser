/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.text;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class GeyserLocale {
    public static final String SYSTEM_LOCALE = "system";

    /**
     * If we determine the default locale that the user wishes to use, use that locale
     */
    private static String DEFAULT_LOCALE;
    /**
     * Whether the system locale cannot be loaded by Geyser.
     */
    private static boolean SYSTEM_LOCALE_INVALID;

    private static final Map<String, Properties> LOCALE_MAPPINGS = new HashMap<>();

    /**
     * Loads the initial locale(s) with the help of the bootstrap.
     */
    public static void init(GeyserBootstrap bootstrap) {
        String defaultLocale = formatLocale(Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry());
        String loadedLocale = loadGeyserLocale(defaultLocale, bootstrap);
        if (loadedLocale != null) {
            DEFAULT_LOCALE = loadedLocale;
            // Load English as a backup in case something goes really wrong
            if (!"en_US".equals(loadedLocale)) {
                loadGeyserLocale("en_US", bootstrap);
            }
            SYSTEM_LOCALE_INVALID = false;
        } else {
            DEFAULT_LOCALE = loadGeyserLocale("en_US", bootstrap);
            if (DEFAULT_LOCALE == null) {
                // en_US can't be loaded?
                throw new IllegalStateException("English locale not found in Geyser. Did you clone the submodules? (git submodule update --init)");
            }
            SYSTEM_LOCALE_INVALID = true;
        }
    }

    /**
     * Finalize the default locale, now that we know what the default locale should be.
     */
    public static void finalizeDefaultLocale(GeyserImpl geyser) {
        String newDefaultLocale = geyser.config().defaultLocale();
        if (SYSTEM_LOCALE.equals(newDefaultLocale)) {
            // We want to use the system locale which is already loaded
            return;
        }
        String loadedNewLocale = loadGeyserLocale(newDefaultLocale, geyser.getBootstrap());
        if (loadedNewLocale != null) {
            // The config's locale is valid
            DEFAULT_LOCALE = loadedNewLocale;
        } else if (SYSTEM_LOCALE_INVALID) {
            geyser.getLogger().warning(Locale.getDefault().toString() + " is not a valid Bedrock language.");
        }
    }

    public static String getDefaultLocale() {
        return DEFAULT_LOCALE;
    }

    /**
     * Loads a Geyser locale from resources, if the file doesn't exist it just logs a warning
     *
     * @param locale Locale to load
     */
    public static void loadGeyserLocale(String locale) {
        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser == null) {
            throw new IllegalStateException("Geyser instance cannot be null when loading a locale!");
        }
        loadGeyserLocale(locale, geyser.getBootstrap());
    }

    private static @Nullable String loadGeyserLocale(String locale, GeyserBootstrap bootstrap) {
        locale = formatLocale(locale);
        // Don't load the locale if it's already loaded.
        if (LOCALE_MAPPINGS.containsKey(locale)) {
            return locale;
        }

        Properties localeProp = new Properties();

        File localLanguage;
        Path localFolder = bootstrap.getConfigFolder().resolve("languages");
        if (Files.exists(localFolder)) {
            localLanguage = localFolder.resolve(locale + ".properties").toFile();
        } else {
            localLanguage = null;
        }
        boolean validLocalLanguage = localLanguage != null && localLanguage.exists();

        InputStream localeStream = bootstrap.getResourceOrNull("languages/texts/" + locale + ".properties");

        // Load the locale
        if (localeStream != null) {
            try {
                try (InputStreamReader reader = new InputStreamReader(localeStream, StandardCharsets.UTF_8)) {
                    localeProp.load(reader);
                } catch (Exception e) {
                    throw new AssertionError(getLocaleStringLog("geyser.language.load_failed", locale), e);
                }

                // Insert the locale into the mappings
                LOCALE_MAPPINGS.put(locale, localeProp);
            } finally {
                try {
                    localeStream.close();
                } catch (IOException ignored) {}
            }
        } else {
            if (!validLocalLanguage) {
                // Don't warn on missing locales if a local file has been found
                bootstrap.getGeyserLogger().debug("Missing locale: " + locale);
            }
        }

        // Load any language overrides that exist after, to override any strings that we just added
        // By loading both, we ensure that if a language string doesn't exist in the custom properties folder,
        // it's loaded from our jar
        if (validLocalLanguage) {
            try (InputStream stream = new FileInputStream(localLanguage)) {
                localeProp.load(stream);
            } catch (IOException e) {
                String message = "Unable to load custom language override!";
                bootstrap.getGeyserLogger().error(message, e);
            }

            LOCALE_MAPPINGS.putIfAbsent(locale, localeProp);
        }
        return localeProp.isEmpty() ? null : locale;
    }

    /**
     * Get a formatted language string with the default locale for Geyser
     *
     * @param key Language string to translate
     * @return Translated string or the original message if it was not found in the given locale
     */
    public static String getLocaleStringLog(String key) {
        return getLocaleStringLog(key, ObjectArrays.EMPTY_ARRAY);
    }

    /**
     * Get a formatted language string with the default locale for Geyser
     *
     * @param key Language string to translate
     * @param values Values to put into the string
     * @return Translated string or the original message if it was not found in the given locale
     */
    public static String getLocaleStringLog(String key, Object... values) {
        return getPlayerLocaleString(key, getDefaultLocale(), values);
    }

    /**
     * Get a formatted language string with the given locale for Geyser
     *
     * @param key Language string to translate
     * @param locale Locale to translate to
     * @return Translated string or the original message if it was not found in the given locale
     */
    public static String getPlayerLocaleString(String key, String locale) {
        return getPlayerLocaleString(key, locale, ObjectArrays.EMPTY_ARRAY);
    }

    /**
     * Get a formatted language string with the given locale for Geyser
     *
     * @param key Language string to translate
     * @param locale Locale to translate to
     * @param values Values to put into the string
     * @return Translated string or the original message if it was not found in the given locale
     */
    public static String getPlayerLocaleString(String key, String locale, Object... values) {
        locale = formatLocale(locale);

        Properties properties = LOCALE_MAPPINGS.get(locale);
        String formatString = null;

        if (properties != null) {
            formatString = properties.getProperty(key);
        }

        // Try and get the key from the default locale
        if (formatString == null) {
            properties = LOCALE_MAPPINGS.get(getDefaultLocale());
            formatString = properties.getProperty(key);

            // Try and get the key from en_US (this should only ever happen in development)
            if (formatString == null) {
                properties = LOCALE_MAPPINGS.get("en_US");
                formatString = properties.getProperty(key);

                // Final fallback
                if (formatString == null) {
                    return key;
                }
            }
        }

        String message = formatString.replace("&", "\u00a7");
        if (values == null || values.length == 0) {
            // Nothing to replace
            return message;
        }

        return MessageFormat.format(message.replace("'", "''"), values);
    }

    /**
     * Cleans up and formats a locale string
     *
     * @param locale The locale to format
     * @return The formatted locale
     */
    public static String formatLocale(String locale) {
        // Currently, all valid Geyser locales follow the same pattern of ll_CC, where ll is the language and
        // CC is the country
        if (locale.length() != 5 || locale.indexOf('_') != 2) {
            // Invalid locale
            return locale;
        }

        // See https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes - covers the special case that is norwegian
        String lowerCaseLocale = locale.toLowerCase(Locale.ROOT);
        if (lowerCaseLocale.equals("nn_no") || lowerCaseLocale.equals("no_no")) {
            locale = "nb_NO";
        }

        String language = locale.substring(0, 2);
        String country = locale.substring(3);
        return language.toLowerCase(Locale.ENGLISH) + "_" + country.toUpperCase(Locale.ENGLISH);
    }
}
