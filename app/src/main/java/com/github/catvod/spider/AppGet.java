package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 通用 APP 源(AppGet) —— 新架构重建版
 * 规则还原自 tvkj.jar 内 AppGet.java
 */
public class AppGet extends Spider {

    private String api, key, iv, deviceId, version, ua, token;

    private byte[] aesEnc(String s) {
        try {
            SecretKeySpec k = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec v = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(1, k, v);
            return c.doFinal(s.getBytes());
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private String aesDec(String s) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS7Padding");
            c.init(2, new SecretKeySpec(key.getBytes(), "AES"), new IvParameterSpec(iv.getBytes()));
            return new String(c.doFinal(Base64.decode(s.getBytes(), 0)));
        } catch (Exception e) {
            try {
                Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                c.init(2, new SecretKeySpec(key.getBytes(), "AES"), new IvParameterSpec(iv.getBytes()));
                return new String(c.doFinal(Base64.decode(s.getBytes(), 0)));
            } catch (Exception e2) {
                return s;
            }
        }
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", TextUtils.isEmpty(ua) ? "okhttp/3.14.9" : ua);
        return h;
    }

    private String post(String path, String json) {
        try {
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            Map<String, String> h = headers();
            h.put("Content-Type", "application/x-www-form-urlencoded");
            h.put("app-user-device-id", deviceId == null ? "" : deviceId);
            h.put("app-version-code", version == null ? "" : version);
            if (!TextUtils.isEmpty(token)) h.put("app-user-token", token);
            h.put("app-api-verify-time", ts);
            h.put("app-ui-mode", "light");
            String resp = OkHttp.post(api + "/api.php" + path, json, h).getBody();
            if (TextUtils.isEmpty(resp)) return "";
            String data = new JSONObject(resp).getString("data");
            return aesDec(data);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        try {
            JSONObject o = new JSONObject(extend);
            api = o.optString("url");
            key = o.optString("dataKey");
            iv = o.optString("dataIv");
            deviceId = o.optString("deviceId");
            version = o.optString("version");
            ua = o.optString("ua");
            token = o.optString("token");
        } catch (Exception e) {
            api = "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();
        String r = post("/getappapi.index/initV119", new JSONObject().toString());
        if (!TextUtils.isEmpty(r)) {
            JSONObject o = new JSONObject(r);
            JSONArray types = o.optJSONArray("type_list");
            if (types != null) for (int i = 0; i < types.length(); i++) {
                JSONObject t = types.getJSONObject(i);
                String name = t.getString("type_name");
                if (!"伦理".equals(name) && !"福利".equals(name) && !"小影院".equals(name)) {
                    classes.add(new Class(t.getString("type_id"), name));
                }
            }
            list = parseList(o.optJSONArray("recommend_list"));
        }
        return Result.string(classes, list, new JSONObject());
    }

    @Override
    public String homeVideoContent() throws Exception {
        String r = post("/getappapi.index/initV119", new JSONObject().toString());
        List<Vod> list = new ArrayList<>();
        if (!TextUtils.isEmpty(r)) list = parseList(new JSONObject(r).optJSONArray("recommend_list"));
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        JSONObject body = new JSONObject();
        body.put("type_id", tid);
        String r = post("/getappapi.index/typeFilterVodList?page=" + pg, body.toString());
        if (!TextUtils.isEmpty(r)) list = parseList(new JSONObject(r).optJSONArray("recommend_list"));
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        JSONObject body = new JSONObject();
        body.put("vod_id", ids.get(0));
        String r = post("/getappapi.index/vodDetail", body.toString());
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        if (TextUtils.isEmpty(r)) return Result.string(vod);
        JSONObject d = new JSONObject(r).getJSONObject("vod");
        vod.setVodName(d.optString("vod_name"));
        vod.setVodPic(d.optString("vod_pic"));
        vod.setVodRemarks(d.optString("vod_remarks"));
        vod.setVodContent(d.optString("vod_content"));
        vod.setVodActor(d.optString("vod_actor"));
        vod.setVodDirector(d.optString("vod_director"));
        vod.setTypeName(d.optString("vod_class"));
        List<String> froms = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        JSONArray playList = d.optJSONArray("vod_play_list");
        if (playList != null) for (int i = 0; i < playList.length(); i++) {
            JSONObject pl = playList.getJSONObject(i);
            JSONObject pi = pl.optJSONObject("player_info");
            String show = pi != null ? pi.optString("show") : "";
            String parse = pi != null ? pi.optString("parse") : "";
            JSONArray u = pl.optJSONArray("urls");
            List<String> eps = new ArrayList<>();
            if (u != null) for (int j = 0; j < u.length(); j++) {
                JSONObject uo = u.getJSONObject(j);
                String name = uo.optString("name");
                String purl = uo.optString("url");
                String papi = uo.optString("parse_api_url");
                String ptoken = uo.optString("token");
                String link;
                if (papi.matches("^https?://.*")) {
                    link = name + "$" + papi + "|" + d.optString("vod_name") + "|" + uo.optString("nid");
                } else {
                    link = name + "$parse_api=" + parse + "&url=" + Base64.encodeToString(aesEnc(purl), 2) + "&token=" + ptoken + "|" + d.optString("vod_name") + "|" + uo.optString("nid");
                }
                eps.add(link);
            }
            froms.add(show);
            urls.add(TextUtils.join("#", eps));
        }
        vod.setVodPlayFrom(TextUtils.join("$$$", froms));
        vod.setVodPlayUrl(TextUtils.join("$$$", urls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] sp = id.split("\\|");
        if (sp.length == 0) return "";
        String item = sp[0];
        if (item.contains("parse_api=")) {
            String api2 = item.substring(item.indexOf("parse_api=") + 10);
            String[] parts = api2.split("&url=");
            if (parts.length == 2) {
                String parseApi = parts[0];
                String b64 = parts[1];
                try {
                    String url = aesDec(b64);
                    String signed = parseApi + "&url=" + URLEncoder.encode(url, "UTF-8");
                    String j = OkHttp.string(signed, headers());
                    JSONObject o = new JSONObject(j);
                    String real = o.optJSONObject("data").optString("url");
                    if (TextUtils.isEmpty(real)) real = o.optString("url");
                    if (!TextUtils.isEmpty(real)) return Result.get().url(real).parse(0).header(headers()).string();
                } catch (Exception ignored) { }
            }
        }
        if (item.matches(".*(m3u8|mp4|mkv).*")) {
            return Result.get().url(item).parse(0).header(headers()).string();
        }
        return Result.get().url(item).parse(1).header(headers()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        JSONObject body = new JSONObject();
        body.put("type_id", 0);
        body.put("keywords", key);
        body.put("page", 1);
        List<Vod> list = new ArrayList<>();
        String r = post("/getappapi.index/searchList", body.toString());
        if (!TextUtils.isEmpty(r)) list = parseList(new JSONObject(r).optJSONArray("search_list"));
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    private List<Vod> parseList(JSONArray arr) throws Exception {
        List<Vod> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            list.add(new Vod(o.optString("vod_id"), o.optString("vod_name"), o.optString("vod_pic"), o.optString("vod_remarks")));
        }
        return list;
    }
}