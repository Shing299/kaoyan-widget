package com.kaoyan.widget;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Engine {
    // 兜底值：未设置初试日期时才使用
    public static final int NEW_PER_DAY = 70;
    public static final int REVIEW_CAP = 40;
    public static final int WORDS_PER_PUSH = 5;
    public static final int[] INTERVALS = {1, 2, 4, 7, 15};
    public static final int INF = 1000000000;
    public static final int START_HOUR = 8;
    public static final int END_HOUR = 22;

    static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd");
    static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static String today() { return DAY.format(new Date()); }
    public static String nowTs() { return TS.format(new Date()); }

    // 每日推送时段默认值（可被 state 里的 push_start / push_end 覆盖）
    public static final int DEF_START_MIN = 8 * 60;
    public static final int DEF_END_MIN = 22 * 60;

    /** 解析 "HH:mm" 为当天分钟数；非法则返回 def。 */
    public static int parseHM(String s, int def) {
        if (s == null) return def;
        s = s.trim();
        int c = s.indexOf(':');
        if (c <= 0) return def;
        try {
            int h = Integer.parseInt(s.substring(0, c).trim());
            int m = Integer.parseInt(s.substring(c + 1).trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return def;
            return h * 60 + m;
        } catch (Exception e) { return def; }
    }

    /** 当天分钟数 -> "HH:mm"。 */
    public static String fmtHM(int minutes) {
        if (minutes < 0) minutes = 0;
        if (minutes > 23 * 60 + 59) minutes = 23 * 60 + 59;
        int h = minutes / 60, m = minutes % 60;
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    /** 推送开始时间（分钟）。 */
    public static int pushStartMin(JSONObject st) {
        return parseHM(st.optString("push_start", ""), DEF_START_MIN);
    }

    /** 推送结束时间（分钟）。 */
    public static int pushEndMin(JSONObject st) {
        return parseHM(st.optString("push_end", ""), DEF_END_MIN);
    }

    static long daysBetween(String a, String b) {
        try {
            Date da = DAY.parse(a), db = DAY.parse(b);
            return (db.getTime() - da.getTime()) / 86400000L;
        } catch (Exception e) { return 0; }
    }

    /** 距离初试剩余天数（未设置返回 0）。 */
    public static int daysLeft(JSONObject st, String today) {
        String ex = st.optString("exam", "");
        if (ex.length() < 10) return 0;
        long d = daysBetween(today, ex.substring(0, 10));
        if (d < 1) d = 1;
        return (int) d;
    }

    /** 根据剩余天数和未学词数推算每日新学词量。 */
    public static int dailyNew(JSONObject st, int wordCount, String today) {
        String ex = st.optString("exam", "");
        if (ex.length() < 10) return NEW_PER_DAY;
        int dl = daysLeft(st, today);
        if (dl < 1) dl = 1;
        int introduced = 0;
        JSONObject cards = st.optJSONObject("cards");
        if (cards != null) introduced = cards.length();
        int remaining = wordCount - introduced;
        if (remaining < 1) remaining = 1;
        int per = (int) Math.ceil((double) remaining / (double) dl);
        if (per < 1) per = 1;
        if (per > 300) per = 300;
        return per;
    }

    /** 复习上限：至少 REVIEW_CAP，词量多时同步放大。 */
    public static int reviewCap(JSONObject st, int dailyNew) {
        int c = REVIEW_CAP;
        if (dailyNew > c) c = dailyNew;
        return c;
    }

    public static JSONObject ensureState(JSONObject st) {
        try {
            if (!st.has("seed")) st.put("seed", new Random().nextInt(Integer.MAX_VALUE - 1) + 1);
            if (!st.has("cards")) st.put("cards", new JSONObject());
            if (!st.has("day0")) st.put("day0", today());
            if (!st.has("day")) st.put("day", JSONObject.NULL);
            if (!st.has("plan")) st.put("plan", new JSONArray());
            if (!st.has("plan_pos")) st.put("plan_pos", 0);
            if (!st.has("plan_day_no")) st.put("plan_day_no", 0);
            if (!st.has("push_start")) st.put("push_start", fmtHM(DEF_START_MIN));
            if (!st.has("push_end")) st.put("push_end", fmtHM(DEF_END_MIN));
        } catch (Exception e) { }
        return st;
    }

    static int[] makeOrder(int n, long seed) {
        List<Integer> o = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) o.add(i);
        Collections.shuffle(o, new Random(seed));
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = o.get(i);
        return a;
    }

    /** 生成当天计划。返回 [dayNo, reviewCnt, newCnt]。 */
    public static int[] buildPlan(JSONObject st, int wordCount, String today) throws Exception {
        long dayNo = daysBetween(st.getString("day0"), today);
        JSONObject cards = st.getJSONObject("cards");
        int newPerDay = dailyNew(st, wordCount, today);
        int revCap = reviewCap(st, newPerDay);
        int[] order = makeOrder(wordCount, st.getLong("seed"));

        Set<Integer> introduced = new HashSet<Integer>();
        java.util.Iterator<String> it = cards.keys();
        while (it.hasNext()) introduced.add(Integer.parseInt(it.next()));

        // 到期复习词：stage 小的优先，其次逾期久的优先
        List<int[]> due = new ArrayList<int[]>();
        java.util.Iterator<String> it2 = cards.keys();
        while (it2.hasNext()) {
            String k = it2.next();
            JSONArray v = cards.getJSONArray(k);
            int stage = v.getInt(0), dueDay = v.getInt(1);
            if (dueDay <= dayNo) due.add(new int[]{stage, dueDay, Integer.parseInt(k)});
        }
        for (int i = 1; i < due.size(); i++) {
            int[] cur = due.get(i);
            int j = i - 1;
            while (j >= 0) {
                int[] p = due.get(j);
                boolean less = (p[0] > cur[0]) || (p[0] == cur[0] && (p[1] > cur[1] || (p[1] == cur[1] && p[2] > cur[2])));
                if (less) { due.set(j + 1, p); j--; } else break;
            }
            due.set(j + 1, cur);
        }
        List<Integer> review = new ArrayList<Integer>();
        for (int i = 0; i < due.size() && i < revCap; i++) review.add(due.get(i)[2]);

        List<Integer> nw = new ArrayList<Integer>();
        for (int idx : order) {
            if (nw.size() >= newPerDay) break;
            if (!introduced.contains(idx)) { nw.add(idx); introduced.add(idx); }
        }

        List<int[]> items = new ArrayList<int[]>();
        for (int idx : review) items.add(new int[]{idx, 1});
        for (int idx : nw) items.add(new int[]{idx, 0});
        Collections.shuffle(items);

        JSONArray plan = new JSONArray();
        for (int[] p : items) { JSONArray a = new JSONArray(); a.put(p[0]); a.put(p[1]); plan.put(a); }
        st.put("day", today);
        st.put("plan_day_no", (int) dayNo);
        st.put("plan", plan);
        st.put("plan_pos", 0);
        return new int[]{(int) dayNo, review.size(), nw.size()};
    }

    static void applyCards(JSONObject st, JSONArray items) throws Exception {
        JSONObject cards = st.getJSONObject("cards");
        int dayNo = st.getInt("plan_day_no");
        for (int i = 0; i < items.length(); i++) {
            JSONArray it = items.getJSONArray(i);
            int idx = it.getInt(0), kind = it.getInt(1);
            String key = String.valueOf(idx);
            if (kind == 0) {
                JSONArray v = new JSONArray(); v.put(0); v.put(dayNo + INTERVALS[0]);
                cards.put(key, v);
            } else {
                if (!cards.has(key)) {
                    JSONArray v = new JSONArray(); v.put(0); v.put(dayNo + INTERVALS[0]);
                    cards.put(key, v);
                } else {
                    JSONArray old = cards.getJSONArray(key);
                    int ns = old.getInt(0) + 1;
                    JSONArray v = new JSONArray();
                    v.put(ns);
                    v.put(ns < INTERVALS.length ? dayNo + INTERVALS[ns] : INF);
                    cards.put(key, v);
                }
            }
        }
    }

    /** 推送下一批（最多 WORDS_PER_PUSH 个）。返回 true 表示已推送。 */
    public static boolean pushNext(Context ctx, List<Words.W> words, JSONObject st) {
        try {
            JSONArray plan = st.getJSONArray("plan");
            int pos = st.getInt("plan_pos");
            if (pos >= plan.length()) return false;
            int end = Math.min(pos + WORDS_PER_PUSH, plan.length());
            JSONArray batch = new JSONArray();
            for (int i = pos; i < end; i++) batch.put(plan.get(i));
            pushBatch(ctx, words, batch, st);
            return true;
        } catch (Exception e) { return false; }
    }

    public static void pushBatch(Context ctx, List<Words.W> words, JSONArray items, JSONObject st) {
        try {
            StringBuilder content = new StringBuilder();
            JSONArray lines = new JSONArray();
            int newCnt = 0, revCnt = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONArray it = items.getJSONArray(i);
                int idx = it.getInt(0), kind = it.getInt(1);
                String tag = (kind == 1) ? "[复]" : "[新]";
                String ln = (i + 1) + "." + tag + " " + words.get(idx).fmt();
                if (i > 0) content.append("\n");
                content.append(ln);
                lines.put(ln);
                if (kind == 0) newCnt++; else revCnt++;
            }

            applyCards(st, items);
            st.put("plan_pos", st.getInt("plan_pos") + items.length());
            Store.saveState(ctx, st);

            int done = st.getInt("plan_pos");
            int total = st.getJSONArray("plan").length();
            int dn = st.optInt("plan_day_no", 0);
            String title = "考研单词 D" + (dn + 1) + " " + done + "/" + total;

            Notifier.postBatch(ctx, title, content.toString());

            JSONObject rec = new JSONObject();
            rec.put("ts", nowTs());
            rec.put("day", dn);
            rec.put("title", title);
            rec.put("new", newCnt);
            rec.put("rev", revCnt);
            rec.put("lines", lines);
            Store.appendHistory(ctx, rec.toString());

            Notifier.dismissRecall(ctx);
            WidgetProvider.updateWidget(ctx, title, words, items);
        } catch (Exception e) { }
    }
}