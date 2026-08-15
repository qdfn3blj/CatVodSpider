package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 听书站(6yueting.com) —— 新架构重建版
 * 规则还原自 tvkj.jar 内 TingBook.java，选择器已 XOR 解密。
 */
public class TingBook extends Spider {

    private static String HOST = "http://www.6yueting.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36";

    private Map<String, String> header() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        return headers;
    }

    private String absUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "http:" + url;
        return HOST + (url.startsWith("/") ? url : "/" + url);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) HOST = extend;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();
        String html = OkHttp.string(HOST + "/ys/t1", header());
        if (!TextUtils.isEmpty(html)) {
            Document doc = Jsoup.parse(html);
            for (Element cat : doc.select("ul.category-list > li > a")) {
                String href = cat.attr("href");
                String name = cat.text().trim();
                if (TextUtils.isEmpty(href) || TextUtils.isEmpty(name)) continue;
                String[] parts = href.split("/");
                classes.add(new Class(parts.length > 0 ? parts[parts.length - 1] : href, name));
            }
            list = parseList(doc);
        }
        return Result.string(classes, list, new org.json.JSONObject());
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = OkHttp.string(HOST + "/ys/t1", header());
        return Result.string(TextUtils.isEmpty(html) ? new ArrayList<Vod>() : parseList(Jsoup.parse(html)));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = HOST + "/ys/" + tid + "/o1/" + pg;
        List<Vod> list = new ArrayList<>();
        String html = OkHttp.string(url, header());
        if (!TextUtils.isEmpty(html)) list = parseList(Jsoup.parse(html));
        return Result.string(list);
    }

    private List<Vod> parseList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("ul.album-list > .album-item");
        for (Element item : items) {
            Element nameA = item.selectFirst(".book-item-name a");
            if (nameA == null) continue;
            String title = nameA.attr("title");
            if (TextUtils.isEmpty(title)) title = nameA.text().trim();
            String href = nameA.attr("href");
            if (TextUtils.isEmpty(href)) continue;
            Element img = item.selectFirst(".book-item-img img");
            String pic = img != null ? img.attr("src") : "";
            String status = "";
            int idx = item.text().indexOf("状态：");
            if (idx >= 0) status = item.text().substring(idx + 3).trim();
            Vod vod = new Vod();
            vod.setVodId(href);
            vod.setVodName(title);
            if (!TextUtils.isEmpty(pic)) vod.setVodPic(absUrl(pic));
            if (!TextUtils.isEmpty(status)) vod.setVodRemarks(status);
            list.add(vod);
        }
        return list;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);
        String html = OkHttp.string(absUrl(id), header());
        if (TextUtils.isEmpty(html)) return "";
        Document doc = Jsoup.parse(html);
        Vod vod = new Vod();
        vod.setVodId(id);
        Element top = doc.selectFirst(".book-item-top img");
        if (top != null) {
            String alt = top.attr("alt");
            if (!TextUtils.isEmpty(alt)) vod.setVodName(alt);
            vod.setVodPic(absUrl(top.attr("src")));
        }
        Element intro = doc.selectFirst(".detail-intro .detail-text");
        if (intro != null) vod.setVodContent(intro.text().trim());
        List<String> playUrls = new ArrayList<>();
        for (Element a : doc.select(".play-list > .list a")) {
            String name = a.text().trim();
            String href = a.attr("href");
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(href)) continue;
            playUrls.add(name + "$" + absUrl(href));
        }
        vod.setVodPlayFrom(playUrls.isEmpty() ? "" : "听书");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) return Result.get().url("").string();
        return Result.get().url(absUrl(id)).parse(0).header(header()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = HOST + "/search/" + URLEncoder.encode(key, "UTF-8");
        List<Vod> list = new ArrayList<>();
        String html = OkHttp.string(url, header());
        if (!TextUtils.isEmpty(html)) list = parseList(Jsoup.parse(html));
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }
}