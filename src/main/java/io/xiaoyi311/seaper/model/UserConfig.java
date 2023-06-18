package io.xiaoyi311.seaper.model;

/**
 * 用户自定义配置
 * @author Xiaoyi311
 */
public class UserConfig {
    /**
     * 端口号
     */
    public Integer port = 8088;

    /**
     * 语言
     */
    public String lang = "zh_CN";

    /**
     * 调试模式
     */
    public boolean debug = false;

    /**
     * 登录次数限制，为0则代表不检查
     */
    public Integer loginTryTime = 5;

    /**
     * 登录限制时间(秒)
     */
    public long loginStopTime = 60;
}
