package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 电影天堂(dygod.net) —— 新架构重建版
 * 规则还原自 tvkj.jar 内 DyGod.java
 */
public class DyGod extends Spider {

    private String HOST = "https://www.dygod.net";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";

    private Map<String, String> header() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Referer", HOST + "/");
        return h;
    }

    private String fetchGB(String url, Map<String, String> headers) {
        try {
            OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();
            Request.Builder rb = new Request.Builder().url(url);
            if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) rb.header(e.getKey(), e.getValue());
            Response resp = client.newCall(rb.build()).execute();
            return new String(resp.body().bytes(), "gb2312").replaceAll("[\r\n]", "");
        } catch (Exception e) {
            return "";
        }
    }

    private String fancy(String link) {
        try {
            if (link.startsWith("ed2k:")) {
                Matcher m = Pattern.compile("\\|file\\|(.*?)\\|").matcher(URLDecoder.decode(link));
                if (m.find()) return "电驴-" + m.group(1);
            } else if (link.startsWith("magnet:")) {
                Matcher m = Pattern.compile("(^|&)dn=([^&]*)(&|$)").matcher(URLDecoder.decode(link));
                if (m.find()) return "磁力-" + m.group(2);
            }
        } catch (Exception ignored) { }
        return link;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) HOST = extend;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String[] names = {"最新影片", "国内电影", "欧美电影", "华语电视", "日韩电视", "欧美电视", "最新综艺", "旧版综艺", "动漫资源", "手机电影"};
        String[] ids = {"gndy/dyzz", "gndy/china", "gndy/oumei", "tv/hytv", "tv/rihantv", "tv/oumeitv", "zongyi2013", "2009zongyi", "dongman", "3gp/3gpmovie"};
        for (int i = 0; i < names.length; i++) classes.add(new Class(ids[i], names[i]));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = HOST + "/html/" + tid + "/index.html";
        if (!"1".equals(pg)) url = HOST + "/html/" + tid + "/index_" + pg + ".html";
        List<Vod> list = new ArrayList<>();
        String html = fetchGB(url, header());
        if (!TextUtils.isEmpty(html)) {
            Document doc = Jsoup.parse(html);
            for (Element table : doc.select("div.co_content8 table")) {
                Element b = table.selectFirst("b");
                if (b == null) continue;
                String title = b.text();
                String remarks = "";
                Element click = table.selectFirst("tr:contains(点击：)");
                if (click != null) remarks = click.text();
                if (title.contains("《") && title.contains("》")) {
                    remarks = title.split("》").length > 1 ? title.split("》")[1] : remarks;
                    title = title.split("《").length > 1 ? title.split("《")[1].split("》")[0] : title;
                }
                Elements links = table.select("table a");
                String href = links.isEmpty() ? "" : links.get(links.size() - 1).attr("href");
                if (TextUtils.isEmpty(href)) continue;
                list.add(new Vod(href, title, "", remarks));
            }
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);
        String html = fetchGB(HOST + id, header());
        if (TextUtils.isEmpty(html)) return "";
        Document doc = Jsoup.parse(html);
        Vod vod = new Vod();
        vod.setVodId(id);
        Element zoom = doc.selectFirst("div#Zoom>img");
        if (zoom != null) vod.setVodPic(absUrl(HOST, zoom.attr("src")));
        Element h1 = doc.selectFirst("div.title_all>h1");
        if (h1 != null) vod.setVodName(h1.text());
        List<String> froms = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        if (html.contains("player_list")) {
            Elements items = doc.select("div.player_list li");
            List<String> eps = new ArrayList<>();
            for (int i = items.size() - 1; i >= 0; i--) {
                Element a = items.get(i).selectFirst("a");
                if (a == null) continue;
                String name = a.text();
                String href = a.attr("href");
                if (href.contains("jianpian")) href = "tvbox-xg:" + href.split("path=")[1];
                eps.add(name + "$" + href);
            }
            if (!eps.isEmpty()) { froms.add("视频播列表"); urls.add(TextUtils.join("#", eps)); }
        }
        if (html.contains("id=\"downlist")) {
            Elements tables = doc.select("div#downlist table");
            List<String> eps = new ArrayList<>();
            for (int i = tables.size() - 1; i >= 0; i--) {
                Element td = tables.get(i).selectFirst("td");
                Element a = tables.get(i).selectFirst("a");
                if (td == null || a == null) continue;
                eps.add(fancy(td.text()) + "$" + a.attr("href"));
            }
            if (!eps.isEmpty()) { froms.add("磁力列表"); urls.add(TextUtils.join("#", eps)); }
        } else if (html.contains("href=\"ftp") || html.contains("href=\"magnet") || html.contains("href=\"ed2k")) {
            Elements tables = doc.select("div#Zoom table");
            List<String> eps = new ArrayList<>();
            for (int i = tables.size() - 1; i >= 0; i--) {
                Element a = tables.get(i).selectFirst("a");
                if (a == null) continue;
                eps.add(fancy(a.text()) + "$" + a.attr("href"));
            }
            if (!eps.isEmpty()) { froms.add("磁力列表"); urls.add(TextUtils.join("#", eps)); }
        }
        vod.setVodPlayFrom(TextUtils.join("$$$", froms));
        vod.setVodPlayUrl(TextUtils.join("$$$", urls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) return Result.get().url("").string();
        return Result.get().url(id).parse(0).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = HOST + "/e/search/index.php";
        String body = "show=title&tempid=1&keyboard=" + URLEncoder.encode(key, "UTF-8") + "&Submit=立即搜索";
        Map<String, String> h = header();
        h.put("Content-Type", "application/x-www-form-urlencoded; charset=gb2312");
        String html = fetchGBPost(url, body, h);
        if (!TextUtils.isEmpty(html)) {
            Document doc = Jsoup.parse(html);
            for (Element table : doc.select("div.co_content8 table")) {
                Element b = table.selectFirst("b");
                if (b == null) continue;
                String title = b.text();
                String remarks = "";
                Element click = table.selectFirst("tr:contains(点击：)");
                if (click != null) remarks = click.text();
                if (title.contains("《") && title.contains("》")) {
                    remarks = title.split("》").length > 1 ? title.split("》")[1] : remarks;
                    title = title.split("《").length > 1 ? title.split("《")[1].split("》")[0] : title;
                }
                Elements links = table.select("table a");
                String href = links.isEmpty() ? "" : links.get(links.size() - 1).attr("href");
                if (TextUtils.isEmpty(href)) continue;
                list.add(new Vod(href, title, "", remarks));
            }
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    private String fetchGBPost(String url, String body, Map<String, String> headers) {
        try {
            OkHttpClient client = new OkHttpClient.Builder().build();
            Request.Builder rb = new Request.Builder().url(url)
                    .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=gb2312"), body));
            if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) rb.header(e.getKey(), e.getValue());
            Response resp = client.newCall(rb.build()).execute();
            return new String(resp.body().bytes(), "gb2312").replaceAll("[\r\n]", "");
        } catch (Exception e) {
            return "";
        }
    }

    private String absUrl(String base, String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "http:" + url;
        return base + (url.startsWith("/") ? url : "/" + url);
    }
}