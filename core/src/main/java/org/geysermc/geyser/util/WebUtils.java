/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;

import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

public class WebUtils {

    /**
     * Makes a web request to the given URL and returns the body as a string
     *
     * @param reqURL URL to fetch
     * @return Body contents or error message if the request fails
     */
    public static String getBody(String reqURL) {
        try {
            URL url = new URL(reqURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", getUserAgent()); // Otherwise Java 8 fails on checking updates
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);

            return connectionToString(con);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Makes a web request to the given URL and returns the body as a {@link JsonObject}.
     *
     * @param reqURL URL to fetch
     * @return the response as JSON
     */
    public static JsonObject getJson(String reqURL) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(reqURL).openConnection();
        con.setRequestProperty("User-Agent", getUserAgent());
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        try (JsonReader reader = GeyserImpl.GSON.newJsonReader(new InputStreamReader(con.getInputStream()))) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    /**
     * Downloads a file from the given URL and saves it to disk
     *
     * @param reqURL File to fetch
     * @param fileLocation Location to save on disk
     */
    public static void downloadFile(String reqURL, String fileLocation) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(reqURL).openConnection();
            con.setRequestProperty("User-Agent", getUserAgent());
            InputStream in = con.getInputStream();
            Files.copy(in, Paths.get(fileLocation), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Unable to download and save file: " + fileLocation + " (" + reqURL + ")", e);
        }
    }

    /**
     * Post a string to the given URL
     *
     * @param reqURL URL to post to
     * @param postContent String data to post
     * @return String returned by the server
     * @throws IOException If the request fails
     */
    public static String post(String reqURL, String postContent) throws IOException {
        URL url = new URL(reqURL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "text/plain");
        con.setRequestProperty("User-Agent", getUserAgent());
        con.setDoOutput(true);

        OutputStream out = con.getOutputStream();
        out.write(postContent.getBytes(StandardCharsets.UTF_8));
        out.close();

        return connectionToString(con);
    }

    /**
     * Get the string output from the passed {@link HttpURLConnection}
     *
     * @param con The connection to get the string from
     * @return The body of the returned page
     * @throws IOException If the request fails
     */
    private static String connectionToString(HttpURLConnection con) throws IOException {
        // Send the request (we dont use this but its required for getErrorStream() to work)
        con.getResponseCode();

        // Read the error message if there is one if not just read normally
        InputStream inputStream = con.getErrorStream();
        if (inputStream == null) {
            inputStream = con.getInputStream();
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
                content.append("\n");
            }

            con.disconnect();
        }

        return content.toString();
    }

    /**
     * Post fields to a URL as a form
     *
     * @param reqURL URL to post to
     * @param fields Form data to post
     * @return String returned by the server
     * @throws IOException If the request fails
     */
    public static String postForm(String reqURL, Map<String, String> fields) throws IOException {
        URL url = new URL(reqURL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("User-Agent", getUserAgent());
        con.setDoOutput(true);

        try (OutputStream out = con.getOutputStream()) {
            // Write the form data to the output
            for (Map.Entry<String, String> field : fields.entrySet()) {
                out.write((field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8) + "&").getBytes(StandardCharsets.UTF_8));
            }
        }

        return connectionToString(con);
    }

    /**
     * Find a SRV record for the given address
     *
     * @param geyser Geyser instance
     * @param remoteAddress Address to find the SRV record for
     * @return The SRV record or null if not found
     */
    public static String @Nullable [] findSrvRecord(GeyserImpl geyser, String remoteAddress) {
        try {
            // Searches for a server address and a port from a SRV record of the specified host name
            InitialDirContext ctx = new InitialDirContext();
            Attribute attr = ctx.getAttributes("dns:///_minecraft._tcp." + remoteAddress, new String[]{"SRV"}).get("SRV");
            // size > 0 = SRV entry found
            if (attr != null && attr.size() > 0) {
                return ((String) attr.get(0)).split(" ");
            }
        } catch (Exception | NoClassDefFoundError ex) { // Check for a NoClassDefFoundError to prevent Android crashes
            if (geyser.config().debugMode()) {
                geyser.getLogger().debug("Exception while trying to find an SRV record for the remote host.");
                ex.printStackTrace(); // Otherwise we can get a stack trace for any domain that doesn't have an SRV record
            }
        }
        return null;
    }

    /**
     * Get a stream of lines from the given URL
     *
     * @param reqURL URL to fetch
     * @return Stream of lines from the URL or an empty stream if the request fails
     */
    public static Stream<String> getLineStream(String reqURL) {
        try {
            URL url = new URL(reqURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", getUserAgent()); // Otherwise Java 8 fails on checking updates
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);

            return connectionToString(con).lines();
        } catch (Exception e) {
            GeyserImpl.getInstance().getLogger().error("Error while trying to get a stream from " + reqURL, e);
            return Stream.empty();
        }
    }

    public static String getUserAgent() {
        return "Geyser-" + GeyserImpl.getInstance().platformType().platformName() + "/" + GeyserImpl.VERSION;
    }
}
