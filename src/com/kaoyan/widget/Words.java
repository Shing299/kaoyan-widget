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
            // v1.3.3：没有音标的词原来直接和释义粘在一起（"approximatea.近似的"），
            // 列表页再按空白拆的时候就拆不动，单词和释义都跟着错位。没音标也要补一个空格。
            return word + (ph.length() > 0 ? ("[" + ph + "] ") : " ") + mean;
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

    /**
     * 解析词库里的一行：`单词[音标] 释义` 或 `单词 释义`（`#` 开头为注释）。
     *
     * 音标允许含空格（`pretend [priˈtend, priː-]` 这种英/美两个读音），
     * 所以这里只在**词头之后**找 '['：词头按空白/逗号/分号切一刀，
     * 剩下的以 '[' 开头才当音标，否则整段都是释义。抽成方法是为了让
     * 加载和测试跑同一份切法（v1.3.3）。
     */
    static W parseLine(String raw) {
        String line = raw.trim();
        if (line.length() == 0 || line.charAt(0) == '#') return null;
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
        return w.length() > 0 ? new W(w, ph, mean) : null;
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
                W w = parseLine(line);
                if (w != null) list.add(w);
            }
            br.close();
        } catch (Exception e) { }
        return list;
    }
}
