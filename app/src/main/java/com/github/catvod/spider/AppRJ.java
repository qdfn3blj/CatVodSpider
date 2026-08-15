package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 APP 源 —— 新架构重建版
 * 规则还原自 tvkj.jar 内 AppRJ.java
 */
public class AppRJ extends Spider {

    private static final String SALT = "7gp0bnd2sr85ydii2j32pcypscoc4w6c7g5spl";
    private String base;

    private String md5(String s) {
        try {
            return new BigInteger(1, MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"))).toString(16).toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private String post(String path, Map<String, String> form) {
        try {
            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", "okhttp-okgo/jeasonlzy");
            return OkHttp.post(base + path, form, header).getBody();
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, String> signed() {
        String ts = (System.currentTimeMillis() / 1000) + "";
        Map<String, String> m = new HashMap<>();
        m.put("timestamp", ts);
        m.put("sign", md5(SALT + ts));
        return m;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        try {
            base = new JSONObject(extend).getString("url");
        } catch (Exception e) {
            base = "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        String r = post("/v3/type/top_type", signed());
        if (TextUtils.isEmpty(r)) return Result.string(classes, new LinkedHashMap<>());
        JSONArray arr = new JSONObject(r).getJSONObject("data").optJSONArray("list");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String tid = o.optString("type_id");
                String tname = o.optString("type_name");
                classes.add(new Class(tid, tname));
                List<Filter> fl = new ArrayList<>();
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    String label = k.equals("extend") ? "类型" : k.equals("area") ? "地区" : k.equals("year") ? "年份" : k.equals("lang") ? "语言" : "";
                    if (TextUtils.isEmpty(label)) continue;
                    JSONArray vals = o.optJSONArray(k);
                    List<Filter.Value> fv = new ArrayList<>();
                    if (vals != null) for (int j = 0; j < vals.length(); j++) {
                        String v = vals.optString(j);
                        if (v.length() > 1) fv.add(new Filter.Value(v, v));
                    }
                    if (fv.size() > 1) fl.add(new Filter(k.replace("extend", "class"), label, fv));
                }
                filters.put(tid, fl);
            }
        }
        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        Map<String, String> m = signed();
        m.put("type_id", tid);
        m.put("limit", "12");
        m.put("page", pg);
        if (extend != null) {
            if (extend.containsKey("area")) m.put("area", extend.get("area"));
            if (extend.containsKey("class")) m.put("class", extend.get("class"));
            if (extend.containsKey("lang")) m.put("lang", extend.get("lang"));
            if (extend.containsKey("year")) m.put("year", extend.get("year"));
        }
        List<Vod> list = new ArrayList<>();
        String r = post("/v3/home/type_search", m);
        if (!TextUtils.isEmpty(r)) {
            JSONArray arr = new JSONObject(r).getJSONObject("data").optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String pic = o.optString("vod_pic");
                if (TextUtils.isEmpty(pic)) pic = o.optString("vod_pic_thumb");
                list.add(buildVod(o.optString("vod_id"), o.optString("vod_name"), pic, o.optString("vod_remarks")));
            }
        }
        return Result.get().vod(list).page(Integer.parseInt(pg), 1, 12, list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Map<String, String> m = signed();
        m.put("vod_id", ids.get(0));
        String r = post("/v3/home/vod_details", m);
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        if (TextUtils.isEmpty(r)) return Result.string(vod);
        JSONObject d = new JSONObject(r).getJSONObject("data");
        String vodName = d.optString("vod_name");
        vod.setVodName(vodName);
        String pic = d.optString("vod_pic");
        if (TextUtils.isEmpty(pic)) pic = d.optString("vod_pic_thumb");
        vod.setVodPic(pic);
        vod.setVodRemarks(d.optString("vod_remarks"));
        vod.setVodContent(d.optString("vod_content").replaceAll("[^一-龥\u3000-〿\uff00-\uffef]", ""));
        vod.setVodYear(d.optString("vod_year"));
        vod.setVodActor(d.optString("vod_actor"));
        vod.setVodDirector(d.optString("vod_director"));
        vod.setTypeName(d.optString("vod_class"));
        List<String> froms = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        JSONArray playList = d.optJSONArray("vod_play_list");
        if (playList != null) for (int i = 0; i < playList.length(); i++) {
            JSONObject pl = playList.getJSONObject(i);
            String name = pl.getString("name");
            String ua = pl.optString("ua");
            JSONArray urlsArr = pl.optJSONArray("urls");
            JSONArray parseUrls = pl.optJSONArray("parse_urls");
            StringBuilder parse = new StringBuilder();
            if (parseUrls != null) for (int j = 0; j < parseUrls.length(); j++) {
                parse.append(parseUrls.optString(j)).append("@");
            }
            List<String> eps = new ArrayList<>();
            if (urlsArr != null) for (int j = 0; j < urlsArr.length(); j++) {
                JSONObject uo = urlsArr.getJSONObject(j);
                String epName = uo.optString("name");
                String url = parse.toString() + "|" + uo.optString("url") + "|" + ua + "|" + vodName + "|" + uo.optString("nid");
                eps.add(epName + "$" + url);
            }
            froms.add(name);
            urls.add(TextUtils.join("#", eps));
        }
        vod.setVodPlayFrom(TextUtils.join("$$$", froms));
        vod.setVodPlayUrl(TextUtils.join("$$$", urls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] sp = id.split("\\|");
        if (sp.length == 5) {
            sp = (sp[0] + "|" + sp[1] + "|" + sp[2] + "||" + sp[3] + "|" + sp[4]).split("\\|");
        }
        String parseUrls = sp.length > 0 ? sp[0] : "";
        String url = sp.length > 1 ? sp[1] : "";
        String ua = sp.length > 2 ? sp[2] : "";
        if (!TextUtils.isEmpty(parseUrls)) {
            for (String pu : parseUrls.split("@")) {
                if (TextUtils.isEmpty(pu)) continue;
                String ts = (System.currentTimeMillis() / 1000) + "";
                Map<String, String> h = new HashMap<>();
                h.put("Referer", "");
                String full = pu + url + "&sign=" + md5(SALT + ts) + "&timestamp=" + ts;
                try {
                    String j = OkHttp.string(full, h);
                    JSONObject o = new JSONObject(j);
                    url = o.optString("url");
                    String forcedUa = o.optString("UA", ua);
                    if (!TextUtils.isEmpty(forcedUa)) ua = forcedUa;
                    if (url.startsWith("http")) break;
                } catch (Exception ignored) { }
            }
        }
        if (!url.startsWith("http")) return "";
        Map<String, String> header = new HashMap<>();
        if (!TextUtils.isEmpty(ua)) header.put("User-Agent", ua);
        return Result.get().url(url).header(header).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        Map<String, String> m = signed();
        m.put("keyword", key);
        m.put("limit", "12");
        m.put("page", "1");
        List<Vod> list = new ArrayList<>();
        String r = post("/v3/home/search", m);
        if (!TextUtils.isEmpty(r)) {
            JSONArray arr = new JSONObject(r).getJSONObject("data").optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String pic = o.optString("vod_pic");
                if (TextUtils.isEmpty(pic)) pic = o.optString("vod_pic_thumb");
                list.add(buildVod(o.optString("vod_id"), o.optString("vod_name"), pic, o.optString("vod_remarks")));
            }
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    private Vod buildVod(String id, String name, String pic, String remarks) {
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodRemarks(remarks);
        return vod;
    }
}