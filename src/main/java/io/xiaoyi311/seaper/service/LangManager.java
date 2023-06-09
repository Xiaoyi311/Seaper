package io.xiaoyi311.seaper.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.xiaoyi311.seaper.SeaperServerManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 国际化管理器
 * @author Xiaoyi311
 */
@Slf4j
public class LangManager {
    /**
     * 语言信息列表
     */
    public static Map<String, String> langInfos = new HashMap<>();

    /**
     * 语言集目录
     */
    static Resource langsFolder = SeaperServerManager.config.getBean(ResourceLoader.class).getResource("file:data/langs");

    /**
     * 目前语言
     */
    static JSONObject lang;

    /**
     * 初始化国际化系统
     */
    public static void initLanguage() {
        //读取语言列表
        File[] langs;
        try{
            langs = langsFolder.getFile().listFiles();
            if (langs == null){
                throw new IOException("This is not a folder! it's a file!");
            }
        }catch (IOException e){
            log.error("Can't read lang files: " + e.getMessage() + ", No language data will be loaded");
            return;
        }

        //遍历语言文件
        for (File lang : langs) {
            String code = lang.getName().replace(".json", "");
            String name = getLang(code, true).getString("Name");
            langInfos.put(code, name);
        }
        log.info("Language Files Found: " + StringUtils.join(LangManager.langInfos.keySet(), ','));

        //显示使用语言信息
        lang = getLang(SeaperServerManager.userConfig.lang, false);
        if(lang != null){
            Integer langVer = lang.getInteger("Version");
            if(langVer < SeaperServerManager.version){
                log.warn("LANGUAGE VERSION IS LOW!! YOU SHOULD UPDATE THE LANGUAGE FILE!!");
            }
            log.info("===== Language Information =====");
            log.info("Name: " + lang.getString("Name"));
            log.info("Author: " + lang.getString("Author"));
            log.info("Version: " + langVer);
            log.info("===== Language Information =====");
        }else{
            log.warn("WARNING !!! NO LANGUAGE LOADED!");
        }
    }

    /**
     * 获取国际化结果
     * @param node 语言节点
     * @param vars 参数
     * @return 输出文字
     */
    public static String msg(String node, String... vars){
        //判断语言文件格式
        if(lang == null || !lang.containsKey("backend")){
            return node;
        }

        //获取节点
        JSONObject jobj = lang.getJSONObject("backend");

        //获取语言文字
        String[] nodes = node.split("\\.");
        String data = null;
        for (int i = 0; i < nodes.length; i++) {
            //如果出现找不到节点
            if(jobj == null){
                return node;
            }

            if(i == nodes.length - 1){
                data = jobj.getString(nodes[i]);
            }else {
                jobj = jobj.getJSONObject(nodes[i]);
            }
        }
        if(data == null){
            return null;
        }

        //解析参数
        if(vars.length != 0) {
            for (int i = 0; i < vars.length; i++) {
                data = data.replace("{" + i + "}", vars[i]);
            }
        }
        return data;
    }

    /**
     * 获取语言文件
     * @param lang 语言代码
     * @return 语言数据
     */
    public static JSONObject getLang(String lang, boolean init){
        //判断是否存在
        if(!init && !langInfos.containsKey(lang)){
            return null;
        }

        //读取文件
        JSONObject langData;
        try {
            langData = JSON.parseObject(
                    Files.readString(
                            Path.of(
                                    langsFolder.getFile().getCanonicalPath(),
                                    lang + ".json"
                            )
                    )
            );
        } catch (IOException e) {
            log.error("Can't get language data: " + e.getMessage());
            return null;
        }

        return langData;
    }

    /**
     * 设置语言
     * @param lang 语言代码
     */
    public static void setLang(String lang){
        //判断是否存在
        if(langInfos.containsKey(lang)){
            LangManager.lang = getLang(lang, false);
        }

        log.info(msg("console.langChange") + lang);
    }

    /**
     * 获取前端语言数据
     * @return 前端语言数据
     */
    public static JSONObject getFrontLang() {
        return lang.getJSONObject("front");
    }

    /**
     * 获取语言列表
     * @return 语言列表
     */
    public static JSONArray getList() {
        JSONArray infos = new JSONArray();
        for (Map.Entry<String, String> langInfo : langInfos.entrySet()) {
            JSONObject info = new JSONObject();
            info.put("code", langInfo.getKey());
            info.put("name", langInfo.getValue());
            infos.add(info);
        }
        return infos;
    }
}
