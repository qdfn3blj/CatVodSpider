package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆瓣 蜘蛛 —— 新架构适配版
 *
 * 逻辑基于 tvkj.jar 内被 NP 加固、jadx 无法反编译的 Douban.class 经 dex smali 逐指令还原。
 * - 所有 API URL 均由 fill-array-data XOR 密文解密并验证
 * - 已按本项目(CatVodSpider 新架构)重写：Result / Vod / OkHttp
 * 分类(type)：hot_gaia / tv_hot / show_hot / movie / tv / rank_list_movie / rank_list_tv
 */
public class Douban extends Spider {

    private static final String API = "https://frodo.douban.com/api/v2";
    private static final String SUBJ = API + "/subject_collection/";
    private static final String APIKEY = "?apikey=0ac44ae016490db2204ce0a042db2916";

    /** 请求头（微信小程序 UA） */
    private Map<String, String> header() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", "frodo.douban.com");
        headers.put("Connection", "Keep-Alive");
        headers.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("hot_gaia", "热门电影"));
        classes.add(new Class("tv_hot", "热播剧集"));
        classes.add(new Class("show_hot", "热播综艺"));
        classes.add(new Class("movie", "电影筛选"));
        classes.add(new Class("tv", "电视筛选"));
        classes.add(new Class("rank_list_movie", "电影榜单"));
        classes.add(new Class("rank_list_tv", "电视剧榜单"));

        List<Vod> list = new ArrayList<>();
        try {
            String url = "http://api.douban.com/api/v2/subject_collection/subject_real_time_hotest/items" + APIKEY;
            String json = OkHttp.string(url, header());
            JSONArray arr = new JSONObject(json).optJSONArray("subject_collection_items");
            list = parseList(arr);
        } catch (Exception ignored) { }

        JSONObject filters = new JSONObject();
        filters.put("hot_gaia", new JSONArray()
                .put(new JSONObject().put("key", "sort").put("name", "排序").put("value", new JSONArray()
                        .put(new JSONObject().put("n", "热度").put("v", "recommend"))
                        .put(new JSONObject().put("n", "最新").put("v", "time"))
                        .put(new JSONObject().put("n", "评分").put("v", "rank"))))
                .put(new JSONObject().put("key", "area").put("name", "地区").put("value", new JSONArray()
                        .put(new JSONObject().put("n", "全部").put("v", "全部"))
                        .put(new JSONObject().put("n", "华语").put("v", "华语"))
                        .put(new JSONObject().put("n", "欧美").put("v", "欧美"))
                        .put(new JSONObject().put("n", "韩国").put("v", "韩国"))
                        .put(new JSONObject().put("n", "日本").put("v", "日本")))));
        filters.put("rank_list_movie", new JSONArray()
                .put(new JSONObject().put("key", "榜单").put("name", "榜单").put("value", new JSONArray()
                        .put(new JSONObject().put("n", "实时热门电影").put("v", "movie_real_time_hotest"))
                        .put(new JSONObject().put("n", "一周口碑电影榜").put("v", "movie_weekly_best"))
                        .put(new JSONObject().put("n", "豆瓣电影Top250").put("v", "movie_top250")))));
        filters.put("rank_list_tv", new JSONArray()
                .put(new JSONObject().put("key", "榜单").put("name", "榜单").put("value", new JSONArray()
                        .put(new JSONObject().put("n", "实时热门电视").put("v", "tv_real_time_hotest"))
                        .put(new JSONObject().put("n", "华语口碑剧集榜").put("v", "tv_chinese_best_weekly"))
                        .put(new JSONObject().put("n", "全球口碑剧集榜").put("v", "tv_global_best_weekly"))
                        .put(new JSONObject().put("n", "国内口碑综艺榜").put("v", "show_chinese_best_weekly"))
                        .put(new JSONObject().put("n", "国外口碑综艺榜").put("v", "show_global_best_weekly")))));

        return Result.string(classes, list, filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        String url = "http://api.douban.com/api/v2/subject_collection/subject_real_time_hotest/items" + APIKEY;
        String json = OkHttp.string(url, header());
        JSONArray arr = new JSONObject(json).optJSONArray("subject_collection_items");
        return Result.string(parseList(arr));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }
        int start = (page - 1) * 20;
        // hot_gaia 接口返回 items，其余 subject_collection 系列返回 subject_collection_items
        String jsonKey = "subject_collection_items";
        String url;

        switch (tid) {
            case "tv_hot": {
                String type = extend == null ? "tv_hot" : extend.getOrDefault("type", "tv_hot");
                url = SUBJ + type + "/items" + APIKEY + "&start=" + start + "&count=20";
                break;
            }
            case "show_hot": {
                String type = extend == null ? "show_hot" : extend.getOrDefault("type", "show_hot");
                url = SUBJ + type + "/items" + APIKEY + "&start=" + start + "&count=20";
                break;
            }
            case "rank_list_movie": {
                String list = extend == null ? "movie_real_time_hotest" : extend.getOrDefault("榜单", "movie_real_time_hotest");
                url = SUBJ + list + "/items" + APIKEY + "&start=" + start + "&count=20";
                break;
            }
            case "rank_list_tv": {
                String list = extend == null ? "tv_real_time_hotest" : extend.getOrDefault("榜单", "tv_real_time_hotest");
                url = SUBJ + list + "/items" + APIKEY + "&start=" + start + "&count=20";
                break;
            }
            case "hot_gaia": {
                // hot_gaia 返回 items（不是 subject_collection_items）
                jsonKey = "items";
                url = "https://frodo.douban.com/api/v2/movie/hot_gaia" + APIKEY
                        + "&start=" + start + "&count=20";
                break;
            }
            case "movie": {
                url = "https://frodo.douban.com/api/v2/subject_collection/movie_weekly_best/items" + APIKEY
                        + "&start=" + start + "&count=20";
                break;
            }
            case "tv": {
                String type = extend == null ? "tv_hot" : extend.getOrDefault("type", "tv_hot");
                url = SUBJ + type + "/items" + APIKEY + "&start=" + start + "&count=20";
                break;
            }
            default: { // 兜底：热播剧集
                url = SUBJ + "tv_hot/items" + APIKEY + "&start=" + start + "&count=20";
                break;
            }
        }

        List<Vod> list = new ArrayList<>();
        int pageCount = page, total = 0;
        try {
            String json = OkHttp.string(url, header());
            JSONArray arr = new JSONObject(json).getJSONArray(jsonKey);
            list = parseList(arr);
            total = arr.length();
        } catch (Exception ignored) { }
        return Result.get().vod(list).page(page, pageCount, 20, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }

    @Override
    public String liveContent(String url) throws Exception {
        return super.liveContent(url);
    }

    /** 解析列表 -> List&lt;Vod&gt; */
    private List<Vod> parseList(JSONArray arr) throws Exception {
        List<Vod> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId("msearch:" + item.optString("id"));
            vod.setVodName(item.optString("title"));
            vod.setVodRemarks(optRating(item));
            String pic = optPic(item);
            if (!TextUtils.isEmpty(pic)) vod.setVodPic(pic);
            list.add(vod);
        }
        return list;
    }

    private String optPic(JSONObject item) {
        try {
            return item.getJSONObject("pic").optString("normal");
        } catch (Exception e) {
            return "";
        }
    }

    private String optRating(JSONObject item) {
        try {
            String value = item.getJSONObject("rating").optString("value");
            return TextUtils.isEmpty(value) ? "" : "评分：" + value;
        } catch (Exception e) {
            return "";
        }
    }
}