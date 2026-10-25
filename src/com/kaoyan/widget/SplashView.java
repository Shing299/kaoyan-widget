package com.kaoyan.widget;

/**
 * 「每日首次打开」的开屏动画（v1.3.1）。
 *
 * 内容：距离初试剩余天数 + 今日单词清单里随机抽的 2 个词。
 * 交互：2 秒后自动淡出；也可以点任意位置（或右上角 ×）立即关闭。
 *
 * 特意不 import 任何 Android 类：同一份代码既能被 {@link HtmlView} 嵌进 WebView，
 * 也能用 javac 单独编译成 build/preview 下的预览页，改样式不用装到手机上试。
 */
public class SplashView {

    /**
     * 生成开屏动画的 HTML 片段（自带 style / script，插到 body 末尾即可）。
     *
     * @param daysLeft 距离初试剩余天数（<1 时按 1 处理）
     * @param words    今日随机抽到的词，每项 {英文, 音标, 释义}，可为 null / 空
     * @param dark     是否深色模式
     */
    public static String build(int daysLeft, String[][] words, boolean dark) {
        if (daysLeft < 1) daysLeft = 1;
        StringBuilder h = new StringBuilder();

        // ---------- 样式 ----------
        h.append("<style>");
        h.append("#splash{position:fixed;left:0;top:0;right:0;bottom:0;z-index:99;overflow:hidden;");
        h.append("display:flex;align-items:center;justify-content:center;");
        h.append("font-family:-apple-system,'Noto Sans SC',sans-serif;color:#fff;");
        h.append(dark
                ? "background:radial-gradient(circle at 18% 12%,#4b32a6 0%,#241a5e 46%,#0d0922 100%)"
                : "background:radial-gradient(circle at 18% 12%,#8a63ea 0%,#5231c8 46%,#241566 100%)");
        h.append(";opacity:1;transition:opacity .45s ease}");
        h.append("#splash.sp-out{opacity:0;pointer-events:none}");
        // 背景光斑
        h.append("#splash .sp-glow{position:absolute;border-radius:50%;filter:blur(26px);opacity:.5;pointer-events:none}");
        h.append("#splash .g1{width:260px;height:260px;left:-70px;top:-50px;");
        h.append("background:radial-gradient(circle,#c4a5ff,transparent 66%);animation:spFloat 7s ease-in-out infinite}");
        h.append("#splash .g2{width:300px;height:300px;right:-100px;bottom:-70px;");
        h.append("background:radial-gradient(circle,#35d6ff,transparent 66%);animation:spFloat 9s ease-in-out infinite reverse}");
        // 数字外的呼吸圆环
        h.append("#splash .sp-ring{position:absolute;left:50%;top:42%;width:250px;height:250px;");
        h.append("margin:-125px 0 0 -125px;border-radius:50%;border:1px solid rgba(255,255,255,.22);");
        h.append("animation:spRing 2.2s ease-out infinite;pointer-events:none}");
        // 右上角关闭按钮
        h.append("#splash .sp-close{position:absolute;top:14px;right:16px;z-index:3;width:36px;height:36px;");
        h.append("border-radius:50%;background:rgba(255,255,255,.16);color:#fff;font-size:20px;line-height:34px;");
        h.append("text-align:center;-webkit-tap-highlight-color:transparent;user-select:none;cursor:pointer}");
        // 主体
        h.append("#splash .sp-inner{position:relative;z-index:2;width:100%;padding:56px 24px 40px;box-sizing:border-box;");
        h.append("display:flex;flex-direction:column;align-items:center}");
        h.append("#splash .sp-label{font-size:14px;letter-spacing:.42em;text-indent:.42em;color:rgba(255,255,255,.72);");
        h.append("animation:spUp .45s ease .05s both}");
        h.append("#splash .sp-days{margin:6px 0 0;line-height:1;white-space:nowrap;");
        h.append("animation:spPop .78s cubic-bezier(.2,1.5,.4,1) .08s both}");
        h.append("#splash .sp-num{font-size:92px;font-weight:800;letter-spacing:-3px;");
        h.append("text-shadow:0 0 30px rgba(200,170,255,.75),0 0 70px rgba(120,80,255,.45)}");
        h.append("#splash .sp-unit{font-size:22px;font-weight:600;margin-left:8px;color:rgba(255,255,255,.9)}");
        h.append("#splash .sp-hint{font-size:13px;color:rgba(255,255,255,.62);margin-top:10px;animation:spUp .45s ease .22s both}");
        // 今日随机词
        h.append("#splash .sp-words{width:100%;margin-top:28px;display:flex;flex-direction:column;gap:10px}");
        h.append("#splash .sp-wt{font-size:12px;letter-spacing:.18em;color:rgba(255,255,255,.55);text-align:center;margin-bottom:2px}");
        h.append("#splash .sp-w{background:rgba(255,255,255,.13);border:1px solid rgba(255,255,255,.2);");
        h.append("border-radius:16px;padding:13px 16px;text-align:left;animation:spUp .5s ease both}");
        h.append("#splash .sp-w1{animation-delay:.3s}");
        h.append("#splash .sp-w2{animation-delay:.44s}");
        h.append("#splash .sp-top{display:flex;align-items:baseline;gap:8px;flex-wrap:wrap}");
        h.append("#splash .sp-en{font-size:19px;font-weight:700;letter-spacing:.2px}");
        h.append("#splash .sp-ph{font-size:12px;color:rgba(255,255,255,.6)}");
        h.append("#splash .sp-cn{font-size:13.5px;line-height:1.45;color:rgba(255,255,255,.9);margin-top:4px}");
        h.append("#splash .sp-empty{width:100%;margin-top:28px;text-align:center;font-size:13px;color:rgba(255,255,255,.6)}");
        h.append("#splash .sp-tip{margin-top:26px;font-size:11px;letter-spacing:.08em;color:rgba(255,255,255,.42);text-align:center}");
        // 底部 2 秒进度条
        h.append("#splash .sp-bar{position:absolute;left:0;bottom:0;height:3px;width:100%;");
        h.append("background:linear-gradient(90deg,rgba(255,255,255,.95),rgba(190,170,255,.9));animation:spBar 2s linear forwards}");
        h.append("@keyframes spUp{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:none}}");
        h.append("@keyframes spPop{0%{opacity:0;transform:scale(.3)}55%{opacity:1;transform:scale(1.12)}100%{opacity:1;transform:scale(1)}}");
        h.append("@keyframes spFloat{0%,100%{transform:translate3d(0,0,0)}50%{transform:translate3d(14px,-20px,0)}}");
        h.append("@keyframes spRing{0%{opacity:.7;transform:scale(.82)}100%{opacity:0;transform:scale(1.38)}}");
        h.append("@keyframes spBar{from{width:100%}to{width:0}}");
        h.append("</style>");

        // ---------- 结构 ----------
        h.append("<div id='splash' role='dialog' aria-modal='true' aria-label='距离初试提醒'>");
        h.append("<div class='sp-glow g1'></div><div class='sp-glow g2'></div><div class='sp-ring'></div>");
        h.append("<div class='sp-close' onclick='spClose()' aria-label='关闭'>&#215;</div>");
        h.append("<div class='sp-inner'>");
        h.append("<div class='sp-label'>距离初试</div>");
        h.append("<div class='sp-days'><span class='sp-num' id='spNum'>0</span><span class='sp-unit'>天</span></div>");
        h.append("<div class='sp-hint'>").append(esc(hint(daysLeft))).append("</div>");
        if (words != null && words.length > 0) {
            h.append("<div class='sp-words'><div class='sp-wt'>今日单词 · 随机两个</div>");
            int n = Math.min(words.length, 2);
            for (int i = 0; i < n; i++) {
                String[] w = words[i];
                String en = (w != null && w.length > 0) ? w[0] : "";
                String ph = (w != null && w.length > 1) ? w[1] : "";
                String cn = (w != null && w.length > 2) ? w[2] : "";
                h.append("<div class='sp-w sp-w").append(i + 1).append("'>");
                h.append("<div class='sp-top'><span class='sp-en'>").append(esc(en)).append("</span>");
                if (ph.length() > 0) h.append("<span class='sp-ph'>").append(esc("[" + ph + "]")).append("</span>");
                h.append("</div><div class='sp-cn'>").append(esc(cn)).append("</div></div>");
            }
            h.append("</div>");
        } else {
            h.append("<div class='sp-empty'>今日单词清单已清空，休息一下吧</div>");
        }
        h.append("<div class='sp-tip'>轻触任意处关闭 · 2 秒后自动淡出</div>");
        h.append("</div>");
        h.append("<div class='sp-bar'></div>");
        h.append("</div>");

        // ---------- 动画与关闭 ----------
        h.append("<script>(function(){var N=").append(daysLeft);
        h.append(";var s=document.getElementById('splash');if(!s)return;");
        h.append("var el=document.getElementById('spNum');var t0=null,dur=650;");
        h.append("function step(t){if(t0===null)t0=t;var p=(t-t0)/dur;if(p>1)p=1;");
        h.append("var e=1-Math.pow(1-p,3);el.textContent=Math.round(N*e);");
        h.append("if(p<1){requestAnimationFrame(step);}else{el.textContent=N;}}");
        h.append("requestAnimationFrame(step);");
        h.append("function spClose(){if(!s||s.className.indexOf('sp-out')>=0)return;");
        h.append("s.className=s.className+' sp-out';");
        h.append("setTimeout(function(){if(s.parentNode)s.parentNode.removeChild(s);},520);}");
        h.append("window.spClose=spClose;");
        h.append("setTimeout(spClose,2000);");
        h.append("s.addEventListener('click',function(){spClose();});})();</script>");

        return h.toString();
    }

    /** 按剩余天数给一句鼓励（纯氛围文案，不影响功能）。 */
    static String hint(int daysLeft) {
        if (daysLeft <= 7) return "最后几天，沉住气";
        if (daysLeft <= 30) return "冲刺阶段，每天都要见";
        if (daysLeft <= 60) return "稳一点，一天一天来";
        return "路还长，先把今天过好";
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
