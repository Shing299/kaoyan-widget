package com.kaoyan.widget;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class Store {
    private static File f(Context c, String name) { return new File(c.getFilesDir(), name); }

    public static JSONObject loadState(Context c) {
        try {
            File file = f(c, "state.json");
            if (!file.exists()) return new JSONObject();
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) { return new JSONObject(); }
    }

    public static void saveState(Context c, JSONObject st) {
        try {
            File file = f(c, "state.json");
            File tmp = f(c, "state.json.tmp");
            OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(tmp), Charset.forName("UTF-8"));
            w.write(st.toString());
            w.close();
            if (file.exists()) file.delete();
            tmp.renameTo(file);
        } catch (Exception e) { }
    }

    public static void appendHistory(Context c, String jsonLine) {
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(f(c, "history.jsonl"), true), Charset.forName("UTF-8"));
            w.write(jsonLine + "\n");
            w.close();
        } catch (Exception e) { }
    }

    public static JSONArray readHistory(Context c, int n) {
        JSONArray out = new JSONArray();
        try {
            File file = f(c, "history.jsonl");
            if (!file.exists()) return out;
            ArrayList<JSONObject> all = new ArrayList<JSONObject>();
            BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (l.length() == 0) continue;
                try { all.add(new JSONObject(l)); } catch (Exception e) { }
            }
            br.close();
            int start = (n > 0 && all.size() > n) ? all.size() - n : 0;
            for (int i = start; i < all.size(); i++) out.put(all.get(i));
        } catch (Exception e) { }
        return out;
    }

    public static boolean hasFile(Context c, String name) { return f(c, name).exists(); }
}