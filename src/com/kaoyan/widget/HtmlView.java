package com.kaoyan.widget;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class HtmlView {

    /** 由侧边栏「设置」触发，强制显示设置页。 */
    public static boolean FORCE_SETUP = false;

    public static String build(Context ctx) {
        JSONObject st = Store.loadState(ctx);
        Engine.ensureState(st);
        String exam = st.optString("exam", "");
        String today = Engine.today();
        if (FORCE_SETUP || exam.length() < 10) return setupPage(ctx, exam, today);

        JSONArray all = Store.readHistory(ctx, 0);
        List<JSONObject> todays = new ArrayList<JSONObject>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject r = all.optJSONObject(i);
            if (r == null) continue;
            if (r.optString("ts", "").startsWith(today)) todays.add(r);
        }
        if (todays.isEmpty()) {
            for (int i = Math.max(0, all.length() - 8); i < all.length(); i++) {
                JSONObject r = all.optJSONObject(i);
                if (r != null) todays.add(r);
            }
        }
        // 从新到旧排序
        for (int i = 1; i < todays.size(); i++) {
            JSONObject cur = todays.get(i);
            String cts = cur.optString("ts", "");
            int j = i - 1;
            while (j >= 0 && todays.get(j).optString("ts", "").compareTo(cts) < 0) {
                todays.set(j + 1, todays.get(j)); j--;
            }
            todays.set(j + 1, cur);
        }

        int total = 0;
        for (JSONObject r : todays) {
            JSONArray l = r.optJSONArray("lines");
            if (l != null) total += l.length();
        }

        int wc = Words.load(ctx).size();
        int per = Engine.dailyNew(st, wc, today);
        int rev = Engine.reviewCap(st, per);
        int dl = Engine.daysLeft(st, today);
        String win = Engine.fmtHM(Engine.pushStartMin(st)) + "–" + Engine.fmtHM(Engine.pushEndMin(st));

        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset='utf-8'>");
        h.append("<meta name='viewport' content='width=device-width,initial-scale=1'>");
        h.append("<title>考研单词·今日</title><style>");
        h.append("body{font-family:-apple-system,'Noto Sans SC',sans-serif;margin:0;padding:16px;background:#faf7ff;color:#2b2340}");
        h.append("h1{font-size:20px;margin:0 0 4px}");
        h.append("p.sub{color:#8a7fa8;font-size:13px;margin:0 0 16px}");
        h.append(".batch{background:#fff;border-radius:14px;padding:12px 14px;margin-bottom:12px;box-shadow:0 2px 8px rgba(120,90,200,.08)}");
        h.append(".bt{font-size:13px;color:#7a5cc0;font-weight:600;margin-bottom:6px}");
        h.append(".w{display:flex;gap:8px;padding:7px 0;border-top:1px dashed #eee;font-size:15px;line-height:1.5;align-items:baseline}");
        h.append(".w:first-of-type{border-top:none}");
        h.append(".tag{flex:0 0 auto;font-size:12px;color:#fff;background:#8b6fd6;border-radius:6px;padding:1px 6px}");
        h.append(".tag.rev{background:#e08a3c}");
        h.append(".en{font-weight:700}.ph{color:#9a8fb5;font-size:13px}");
        h.append(".cn{color:#4a4066}");
        h.append("#topbar{position:sticky;top:0;z-index:20;display:flex;align-items:center;gap:10px;padding:8px 12px;margin:-16px -16px 12px;background:rgba(250,247,255,.95);border-bottom:1px solid #efeaf8}");
        h.append("#menuBtn{width:38px;height:38px;border:none;border-radius:10px;background:#8b6fd6;color:#fff;font-size:18px;cursor:pointer}");
        h.append("#barTitle{font-weight:700;font-size:16px}");
        h.append("#scrim{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(40,30,60,.38);opacity:0;visibility:hidden;transition:.25s;z-index:30}");
        h.append("#scrim.open{opacity:1;visibility:visible}");
        h.append("#drawer{position:fixed;top:0;left:0;height:100%;width:250px;max-width:78%;background:#fff;z-index:40;transform:translateX(-105%);transition:transform .28s;box-shadow:4px 0 24px rgba(80,60,140,.18);padding:18px 14px;box-sizing:border-box;display:flex;flex-direction:column}");
        h.append("#drawer.open{transform:translateX(0)}");
        h.append(".drawer-head{font-size:16px;font-weight:700;margin:4px 6px 14px}");
        h.append(".mi{display:block;padding:12px;border-radius:10px;font-size:15px;cursor:pointer;color:#3a3352}");
        h.append(".mi:active{background:#f1ecfb}");
        h.append(".drawer-foot{margin-top:auto;font-size:12px;color:#9a8fb5;line-height:1.8;padding:10px 6px;border-top:1px dashed #eee}");
        h.append("</style></head><body>");
        h.append("<div id='topbar'><button id='menuBtn'>&#9776;</button><span id='barTitle'>考研单词</span></div>");
        h.append("<div id='scrim'></div>");
        h.append("<nav id='drawer'><div class='drawer-head'>菜单</div>");
        h.append("<a class='mi' onclick='goTop()'>🏠 今日单词</a>");
        h.append("<a class='mi' onclick='App.pushNow()'>⚡ 立即推送一批</a>");
        h.append("<a class='mi' onclick='App.reload()'>🔄 刷新数据</a>");
        h.append("<a class='mi' onclick='App.openSetup()'>⚙️ 设置 / 换词库</a>");
        h.append("<a class='mi' onclick='goTop()'>⬆️ 回到顶部</a>");
        h.append("<div class='drawer-foot'>推送 ").append(win).append("<br>初试 ").append(esc(exam)).append("（剩 ").append(dl).append(" 天）<br>每日新学 ").append(per).append(" · 复习 ≤ ").append(rev).append("</div></nav>");
        h.append("<script>");
        h.append("function toggleMenu(o){var d=document.getElementById('drawer'),s=document.getElementById('scrim');if(o===undefined)o=!d.classList.contains('open');if(o){d.classList.add('open');s.classList.add('open');}else{d.classList.remove('open');s.classList.remove('open');}}");
        h.append("function goTop(){window.scrollTo(0,0);toggleMenu(false);}");
        h.append("window.onload=function(){var mb=document.getElementById('menuBtn');if(mb)mb.onclick=function(){toggleMenu();};var sc=document.getElementById('scrim');if(sc)sc.onclick=function(){toggleMenu(false);};};");
        h.append("</script>");
        h.append("<h1>今日单词</h1>");
        h.append("<p class='sub'>更新 ").append(Engine.nowTs())
         .append(" ｜ 共 ").append(todays.size()).append(" 批 / ").append(total).append(" 词</p>");
        for (JSONObject r : todays) {
            h.append("<div class='batch'><div class='bt'>").append(esc(r.optString("ts", "")))
             .append(" ｜ ").append(esc(r.optString("title", ""))).append("</div>");
            JSONArray lines = r.optJSONArray("lines");
            if (lines != null) for (int i = 0; i < lines.length(); i++) {
                String ln = lines.optString(i, "");
                boolean isRev = ln.contains("[复]");
                String body = ln.indexOf(' ') >= 0 ? ln.substring(ln.indexOf(' ') + 1) : ln;
                body = body.replace("[复]", "").replace("[新]", "").trim();
                String en, ph = "", cn;
                int sp = 0, n = body.length();
                while (sp < n && !Character.isWhitespace(body.charAt(sp))) sp++;
                en = body.substring(0, sp);
                String rest = (sp < n ? body.substring(sp) : "").trim();
                if (rest.startsWith("[")) {
                    int e = rest.indexOf(']');
                    if (e > 0) { ph = rest.substring(1, e).trim(); rest = rest.substring(e + 1).trim(); }
                }
                cn = rest;
                String tag = isRev ? "<span class='tag rev'>复</span>" : "<span class='tag'>新</span>";
                h.append("<div class='w'>").append(tag).append("<span><span class='en'>")
                 .append(esc(en)).append("</span> <span class='ph'>")
                 .append(ph.length() > 0 ? esc("[" + ph + "]") : "").append("</span> <span class='cn'>")
                 .append(esc(cn)).append("</span></span></div>");
            }
            h.append("</div>");
        }
        h.append("</body></html>");
        return h.toString();
    }

    /** 首次设置页：初试日期 + 词库管理。 */
    static String setupPage(Context ctx, String exam, String today) {
        JSONObject pst = Store.loadState(ctx);
        Engine.ensureState(pst);
        String pstart = Engine.fmtHM(Engine.pushStartMin(pst));
        String pend = Engine.fmtHM(Engine.pushEndMin(pst));
        int wc = Words.load(ctx).size();
        String src = Words.sourceName(ctx);
        String val = (exam != null && exam.length() >= 10) ? exam.substring(0, 10) : "2027-12-20";
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset='utf-8'>");
        h.append("<meta name='viewport' content='width=device-width,initial-scale=1'>");
        h.append("<title>设置</title><style>");
        h.append("body{font-family:-apple-system,'Noto Sans SC',sans-serif;margin:0;padding:22px 18px;background:#faf7ff;color:#2b2340}");
        h.append("h1{font-size:22px;margin:0 0 6px}");
        h.append("p.sub{color:#8a7fa8;font-size:13px;margin:0 0 22px;line-height:1.6}");
        h.append(".lb{display:block;font-size:13px;color:#7a5cc0;font-weight:600;margin:0 0 8px}");
        h.append("input[type=date]{width:100%;box-sizing:border-box;font-size:17px;padding:12px;border:1px solid #e3dbf5;border-radius:12px;background:#fff;color:#2b2340}");
        h.append(".calc{margin:12px 0 20px;font-size:14px;color:#5b4a92;background:#f1ecfb;border-radius:10px;padding:12px;line-height:1.5}");
        h.append(".btn{width:100%;border:none;border-radius:12px;background:#8b6fd6;color:#fff;font-size:16px;font-weight:700;padding:14px;cursor:pointer}");
        h.append(".card{background:#fff;border-radius:14px;padding:16px;margin-top:20px;box-shadow:0 2px 8px rgba(120,90,200,.08)}");
        h.append(".ct{font-size:15px;font-weight:700;margin-bottom:8px}");
        h.append(".wc{font-size:13px;color:#6a5f8c;margin-bottom:14px;line-height:1.5}");
        h.append(".btn2{width:100%;border:1px solid #d8ccf2;border-radius:10px;background:#fff;color:#5b4a92;font-size:15px;padding:12px;margin-top:10px;cursor:pointer}");
        h.append("input[type=time]{width:100%;box-sizing:border-box;font-size:17px;padding:12px;border:1px solid #e3dbf5;border-radius:12px;background:#fff;color:#2b2340}");
        h.append(".row{display:flex;gap:10px;align-items:center}");
        h.append("</style></head><body>");
        h.append("<h1>欢迎使用 · 考研单词</h1>");
        h.append("<p class='sub'>首次使用请设置初试日期，系统会按剩余天数和词库总量，自动推算每日需要新学的单词数量。</p>");
        h.append("<label class='lb'>初试日期</label>");
        h.append("<input id='exam' type='date' value='").append(val).append("'>");
        h.append("<div id='calc' class='calc'></div>");
        h.append("<button class='btn' onclick='saveExam()'>保存并开始</button>");
        h.append("<div class='card'><div class='ct'>词库</div>");
        h.append("<div class='wc'>当前 <b>").append(wc).append("</b> 词（").append(src).append("）<br>默认内置考研词表，可导入自己的 txt 覆盖。</div>");
        h.append("<button class='btn2' onclick='App.pickWords()'>📂 导入自定义词库（txt）</button>");
        h.append("<button class='btn2' onclick='App.resetWords()'>↩️ 恢复内置考研词库</button></div>");
        h.append("<div class='card'><div class='ct'>每日推送时段</div>");
        h.append("<div class='wc'>单词会在该时段内分批推送（每批 ").append(Engine.WORDS_PER_PUSH).append(" 个）。默认 08:00–22:00。</div>");
        h.append("<div class='row'><input id='pstart' type='time' value='").append(pstart).append("'>");
        h.append("<span>—</span><input id='pend' type='time' value='").append(pend).append("'></div>");
        h.append("<button class='btn2' onclick='savePush()'>保存推送时间</button></div>");
        h.append("<script>");
        h.append("var WC=").append(wc).append(";");
        h.append("function calc(){var ex=document.getElementById('exam');var v=ex.value;var c=document.getElementById('calc');");
        h.append("if(!v){c.innerText='请选择日期';return;}");
        h.append("var d=new Date(v+'T00:00:00');var n=new Date('").append(today).append("T00:00:00');");
        h.append("var days=Math.round((d-n)/86400000);if(isNaN(days)){c.innerText='日期无效';return;}if(days<1)days=1;");
        h.append("var per=Math.max(1,Math.ceil(WC/days));");
        h.append("c.innerText='距离初试 '+days+' 天｜每日约需新学 '+per+' 个单词（约 '+Math.ceil(per/5)+' 批/天）';}");
        h.append("var ex=document.getElementById('exam');ex.addEventListener('input',calc);ex.addEventListener('change',calc);");
        h.append("function saveExam(){var v=ex.value;if(!v){return;}App.setExam(v);}");
        h.append("function savePush(){var s=document.getElementById('pstart').value;var e=document.getElementById('pend').value;if(!s||!e){return;}if(e<=s){alert('结束时间需晚于开始时间');return;}App.setPushTime(s,e);alert('已保存推送时段：'+s+' – '+e);}");
        h.append("calc();");
        h.append("</script>");
        h.append("</body></html>");
        return h.toString();
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}