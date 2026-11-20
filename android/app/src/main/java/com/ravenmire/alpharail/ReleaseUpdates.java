package com.ravenmire.alpharail;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Only discovers stable, versioned Alpharail release APKs from the official repository. */
final class ReleaseUpdates {
    private static final String REPO = "RavenmireLAbs/Alpharail_Launcher";
    private static final String VERSION = "(?:0|[1-9][0-9]{0,8})(?:\\.(?:0|[1-9][0-9]{0,8})){2}";
    static String downloadUrl(String version) {
        if (version == null || !version.matches(VERSION)) throw new IllegalArgumentException("Invalid version");
        return "https://github.com/" + REPO + "/releases/download/v" + version + "/Alpharail-v" + version + ".apk";
    }
    static boolean newer(String candidate, String installed) {
        if (candidate == null || installed == null || !candidate.matches(VERSION) || !installed.matches(VERSION)) return false;
        String[] a = candidate.split("\\."), b = installed.split("\\.");
        for (int i = 0; i < 3; i++) {
            int order = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (order != 0) return order > 0;
        }
        return false;
    }
    static String latest() throws IOException, JSONException {
        HttpsURLConnection connection = (HttpsURLConnection)new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Release lookup failed");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 256 * 1024) throw new IOException("Release metadata too large");
                    bytes.write(buffer, 0, count);
                }
                return parse(new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            }
        } finally { connection.disconnect(); }
    }
    static String parse(JSONObject release) throws IOException, JSONException {
        String tag = release.getString("tag_name");
        if (release.getBoolean("draft") || release.getBoolean("prerelease") || !tag.matches("v" + VERSION))
            throw new IOException("No stable release");
        String version = tag.substring(1), url = downloadUrl(version);
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (url.equals(asset.optString("browser_download_url")) && "uploaded".equals(asset.optString("state"))) return version;
        }
        throw new IOException("Official APK not available");
    }
    private ReleaseUpdates() {}
}
