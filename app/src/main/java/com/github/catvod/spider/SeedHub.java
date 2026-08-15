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
 * SeedHub 种子电影站 —— 新架构重建版
 * 规则还原自 tvkj.jar 内 SeedHub.java
 */
public class SeedHub extends Spider {

    private static String SITE = "https://www.seedhub.cc";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";

    private Map<String, String> header() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", SITE + "/");
        return headers;
    }

    private String abs(String base, String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return base + (url.startsWith("/") ? url : "/" + url);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) SITE = extend;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("3", "电视剧"));
        classes.add(new Class("2", "动漫"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = SITE + "/categories/" + tid + "/movies/";
        if (pg != null && !pg.equals("1")) url = url + "?page=" + pg;
        List<Vod> list = new ArrayList<>();
        String html = OkHttp.string(url, header());
        if (TextUtils.isEmpty(html)) return Result.get().vod(list).string();
        Document doc = Jsoup.parse(html);
        Elements covers = doc.select("div.content div.cover");
        for (Element cover : covers) {
            Element link = cover.selectFirst("a");
            if (link == null) continue;
            String title = link.text().trim();
            if (TextUtils.isEmpty(title)) title = link.attr("title");
            String href = link.attr("href");
            if (TextUtils.isEmpty(href)) continue;
            String pic = "";
            Element img = cover.selectFirst("img");
            if (img != null) pic = img.attr("src");
            String remarks = "";
            Element score = cover.selectFirst("li:contains(评分: )>a");
            if (score != null) remarks = score.text().trim();
            Vod vod = new Vod();
            vod.setVodId(href);
            vod.setVodName(title);
            if (!TextUtils.isEmpty(pic)) vod.setVodPic(withHeader(abs(SITE, pic)));
            if (!TextUtils.isEmpty(remarks)) vod.setVodRemarks(remarks);
            list.add(vod);
        }
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) { }
        return Result.get().vod(list).page(page, 1, 20, list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);
        String url = abs(SITE, id);
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName("SeedHub");
        List<String> playUrls = new ArrayList<>();
        try {
            String html = OkHttp.string(url, header());
            Document doc = Jsoup.parse(html);
            Element cover = doc.selectFirst("div.content>div.cover img");
            if (cover != null) {
                vod.setVodPic(withHeader(abs(SITE, cover.attr("src"))));
                String alt = cover.attr("alt");
                if (!TextUtils.isEmpty(alt)) vod.setVodName(alt);
            }
            Element start = doc.selectFirst("div.content>p.link-start>a");
            String magnetUrl = start != null ? abs(SITE, start.attr("href")) : "";
            Elements seeds = doc.select("ul.seeds li a");
            if (!seeds.isEmpty()) {
                for (int i = seeds.size() - 1; i >= 0; i--) {
                    Element it = seeds.get(i);
                    String name = it.attr("title");
                    String link = it.attr("href");
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(link)) continue;
                    playUrls.add(name + "$" + link);
                }
            } else if (!TextUtils.isEmpty(magnetUrl) && (magnetUrl.startsWith("magnet:") || magnetUrl.startsWith("ed2k:"))) {
                playUrls.add("立即播放$" + magnetUrl);
            }
        } catch (Exception ignored) { }
        vod.setVodPlayFrom(playUrls.isEmpty() ? "" : "SeedHub");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) return Result.get().url("").string();
        if (id.startsWith("magnet:") || id.startsWith("ed2k:")) return Result.get().url(id).parse(0).string();
        return Result.get().url(id).parse(1).header(header()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String bing = "https://cn.bing.com/search?q=" + URLEncoder.encode("site:www.seedhub.cc/movies/" + key, "UTF-8");
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        String html = OkHttp.string(bing, h);
        if (!TextUtils.isEmpty(html)) {
            Document doc = Jsoup.parse(html);
            for (Element item : doc.select("ol#b_results li")) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                if (href.contains("/movies/")) list.addAll(parsePage(href));
            }
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    private List<Vod> parsePage(String href) {
        List<Vod> list = new ArrayList<>();
        try {
            String html = OkHttp.string(href, header());
            Document doc = Jsoup.parse(html);
            Element cover = doc.selectFirst("div.content>div.cover>img");
            String title = cover != null ? cover.attr("alt") : "";
            String pic = cover != null ? cover.attr("src") : "";
            for (int i = doc.select("ul.seeds li a").size() - 1; i >= 0; i--) {
                Element it = doc.select("ul.seeds li a").get(i);
                String name = it.attr("title");
                String link = it.attr("href");
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(link)) continue;
                Vod vod = new Vod();
                vod.setVodId(href + "###" + link);
                vod.setVodName(title + " " + name);
                vod.setVodRemarks("SeedHub");
                if (!TextUtils.isEmpty(pic)) vod.setVodPic(withHeader(abs(SITE, pic)));
                list.add(vod);
            }
        } catch (Exception ignored) { }
        return list;
    }

    private String withHeader(String url) {
        if (TextUtils.isEmpty(url)) return url;
        return url + "@Headers={\"User-Agent\":\"" + UA + "\"}";
    }
}