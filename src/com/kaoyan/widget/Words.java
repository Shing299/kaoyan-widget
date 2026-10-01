package com.kaoyan.widget;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Words {
    public static class W {
        public String word;
        public String ph;
        public String mean;
        public W(String w, String p, String m) { word = w; ph = p; mean = m; }
        public String fmt() {
            return word + (ph.length() > 0 ? ("[" + ph + "] ") : "") + mean;
        }
    }

    /** 自定义词库文件（用户导入后存放于此，优先于内置词库）。 */
    public static File customFile(Context c) { return new File(c.getFilesDir(), "words_custom.txt"); }

    public static boolean hasCustom(Context c) {
        File f = customFile(c);
        return f.exists() && f.length() > 0;
    }

    public static String sourceName(Context c) {
        return hasCustom(c) ? "自定义词库" : "内置考研词库";
    }

    public static List<W> load(Context ctx) {
        List<W> list = new ArrayList<W>();
        InputStream in = null;
        try {
            if (hasCustom(ctx)) in = new FileInputStream(customFile(ctx));
            else in = ctx.getAssets().open("words.txt");
        } catch (Exception e) { return list; }
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) continue;
                if (line.charAt(0) == '#') continue;
                int i = 0, n = line.length();
                while (i < n) {
                    char ch = line.charAt(i);
                    if (Character.isWhitespace(ch) || ch == ',' || ch == '\t' || ch == ';') break;
                    i++;
                }
                String w = line.substring(0, i);
                String rest = (i < n ? line.substring(i) : "").trim();
                if (rest.startsWith(",") || rest.startsWith(";")) rest = rest.substring(1).trim();
                String ph = "", mean = rest;
                if (rest.startsWith("[")) {
                    int e = rest.indexOf(']');
                    if (e > 0) { ph = rest.substring(1, e).trim(); mean = rest.substring(e + 1).trim(); }
                }
                if (w.length() > 0) list.add(new W(w, ph, mean));
            }
            br.close();
        } catch (Exception e) { }
        return list;
    }
}
