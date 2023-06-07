package io.xiaoyi311.seaper.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.xiaoyi311.seaper.SeaperServerManager;
import io.xiaoyi311.seaper.exception.DataFileException;
import io.xiaoyi311.seaper.exception.ErrorPageException;
import io.xiaoyi311.seaper.model.PageData;
import io.xiaoyi311.seaper.model.User;
import io.xiaoyi311.seaper.utils.PageUtil;
import io.xiaoyi311.seaper.utils.PermissionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 用户管理器
 * @author Xiaoyi311
 */
@Service
@Slf4j
public class UserManager {
    /**
     * 用户识别码列表
     */
    public static Map<String, String> users = new HashMap<>();

    /**
     * 用户集目录
     */
    static Resource usersFolder = SeaperServerManager.config.getBean(ResourceLoader.class).getResource("file:data/users");

    /**
     * 识别码读取用户
     * @param uuid 识别码
     * @return 目标用户
     */
    public static User getByUUID(String uuid) throws DataFileException {
        //判断是否存在
        if(!users.containsKey(uuid)){
            return null;
        }

        return pGetByUUID(uuid);
    }

    /**
     * 不检查根据 UUID 获取用户信息
     * @param uuid UUID
     * @return 用户信息
     */
    private static User pGetByUUID(String uuid) throws DataFileException {
        try {
            return JSONObject.parseObject(
                    Files.readString(
                            Path.of(
                                    usersFolder.getFile().getCanonicalPath(),
                                    uuid + ".json"
                            )
                    ),
                    User.class
            );
        } catch (IOException e) {
            throw new DataFileException(LangManager.msg("dataFile.readUserDataError", e.getMessage()));
        }
    }

    /**
     * 用户名读取用户
     * @param username 识别码
     * @return 目标用户
     */
    public static User getByUsername(String username) {
        //判断是否存在
        if(!users.containsValue(username)){
            return null;
        }

        //读取文件
        User userinfo;
        try {
            userinfo = JSONObject.parseObject(
                    Files.readString(
                            Path.of(
                                    usersFolder.getFile().getCanonicalPath(),
                                    getUUIDByUsername(username) + ".json"
                            )
                    ),
                    User.class
            );
        } catch (IOException e) {
            log.error(LangManager.msg("dataFile.readUserDataError", e.getMessage()));
            return null;
        }

        return userinfo;
    }

    /**
     * 根据 用户名 获取 UUID
     * @param Username 用户名
     * @return UUID
     */
    private static String getUUIDByUsername(String Username){
        for(Map.Entry<String,String > entry : users.entrySet()){
            if(Objects.equals(entry.getValue(), Username)){
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Session 读取用户
     * @param session Session
     * @return 目标用户
     */
    public static User getBySession(HttpSession session) throws DataFileException {
        //是否已经登录
        if(!isLogin(session)){
            return null;
        }

        return getByUUID(session.getAttribute("AUTH_uuid").toString());
    }

    /**
     * 新建用户
     * @param username 用户名
     * @param password 密码
     * @param who 谁更改的
     * @return 新建的用户
     * @exception DataFileException 数据文件读写错误
     */
    public static User create(String username, String password, String who) throws DataFileException {
        String uuid = UUID.randomUUID().toString();
        User user = new User(
                uuid,
                username,
                password,
                users.size() == 0 ? PermissionUtil.SuperAdmin : PermissionUtil.Default
        );

        create(user, who);
        return user;
    }

    /**
     * 新建用户
     * @param user 用户
     * @param who 谁更改的
     * @exception DataFileException 数据文件读写错误
     */
    public static void create(User user, String who) throws DataFileException {
        set(user, who);
        UserManager.users.put(user.uuid, user.username);
    }

    /**
     * 写入用户
     * @param user 用户信息
     * @param who  谁更改的
     * @exception DataFileException 数据文件读写错误
     */
    public static void set(User user, String who) throws DataFileException {
        //解析路径
        Path userFile;
        try {
            userFile = Path.of(
                    usersFolder.getFile().getCanonicalPath(),
                    user.uuid + ".json"
            );
        } catch (IOException e) {
            throw new DataFileException(LangManager.msg("dataFile.readUserDataError", e.getMessage()));
        }

        //文件是否存在
        if(!Files.exists(userFile)){
            try {
                Files.createFile(userFile);
                log.info(LangManager.msg("console.createUser", who, user.uuid));
            } catch (IOException e) {
                throw new DataFileException(LangManager.msg("dataFile.createUserDataError", e.getMessage()));
            }
        }

        //尝试写入
        try {
            Files.write(userFile, JSON.toJSONString(user).getBytes());
        } catch (IOException e) {
            throw new DataFileException(LangManager.msg("dataFile.writeUserDataError", e.getMessage()));
        }
    }

    /**
     * 初始化所有用户数据
     */
    public static void initUsers(){
        //读取用户列表
        File[] users;
        try{
            users = usersFolder.getFile().listFiles();
            if (users == null){
                throw new IOException(LangManager.msg("dataFile.folderIsFile"));
            }
        }catch (IOException e){
            log.error(LangManager.msg("dataFile.readUsersDataError", e.getMessage()));
            return;
        }

        //遍历用户文件
        for (File user : users) {
            String uuid = user.getName().replace(".json", "");
            try {
                User muser = pGetByUUID(uuid);
                if(muser != null){
                    UserManager.users.put(uuid, muser.username);
                }
            } catch (DataFileException e) {
                log.error(e.getMessage());
            }
        }
        log.info(LangManager.msg("dataFile.userDataLoaded", String.valueOf(UserManager.users.size())));
    }

    /**
     * 登录
     * @param user 用户信息
     * @param session 对话信息
     * @return 是否登录成功
     */
    public static boolean login(User user, HttpSession session) {
        User relUser = getByUsername(user.username);

        //是否存在
        if(relUser == null){
            return false;
        }

        //是否登陆失败
        if(!Objects.equals(relUser.password, user.password)){
            return false;
        }

        //登录
        session.setAttribute("AUTH_login", true);
        session.setAttribute("AUTH_uuid", relUser.uuid);
        session.setAttribute("AUTH_username", relUser.username);
        session.setAttribute("AUTH_password", relUser.password);
        session.setMaxInactiveInterval(30*60);
        log.info(LangManager.msg("console.loginUser" , relUser.uuid, relUser.username));
        return true;
    }

    /**
     * 是否登录
     * @param session Session
     * @return 是否登录
     */
    public static boolean isLogin(HttpSession session){
        Object login = session.getAttribute("AUTH_login");
        return !session.isNew() && login != null && (boolean)login;
    }

    /**
     * 检查权限组
     * @param user 用户
     * @param permissions 检查的权限
     * @return 是否通过
     */
    public static boolean checkPermissions(User user, String[] permissions){
        return PermissionUtil.checkPermissions(user.permissions, new ArrayList<>(List.of(permissions)));
    }

    /**
     * 获取用户列表
     * @param show 展示的数量
     * @param page 页码
     * @return 用户列表
     * @throws ErrorPageException 页码数据错误
     */
    public static PageData getPageList(int show, int page) throws ErrorPageException {
        //获取用户 UUID 数据
        Object[] uuids = UserManager.users.keySet().toArray();
        int total = PageUtil.total(show, uuids);
        List<Object> uuidList = PageUtil.get(page, show, uuids);

        //判断页面是否不存在
        if(uuidList == null){
            throw new ErrorPageException(page, total);
        }

        //转换所有数据
        uuidList.replaceAll(o -> {
            try {
                return UserManager.getByUUID(o.toString());
            } catch (DataFileException e) {
                return new User(o.toString(), "error", "error", new ArrayList<>());
            }
        });

        //返回最终数据
        return new PageData(
                total,
                page,
                uuidList
        );
    }

    /**
     * 删除用户
     * @param uuid UUID
     * @param who 谁更改的
     * @return 是否成功
     */
    public static boolean remove(String uuid, String who) throws DataFileException {
        //判断用户是否存在
        if(!users.containsKey(uuid)){
            return false;
        }

        //解析路径
        Path userFile;
        try {
            userFile = Path.of(
                    usersFolder.getFile().getCanonicalPath(),
                    uuid + ".json"
            );
        } catch (IOException e) {
            throw new DataFileException(LangManager.msg("dataFile.readUserDataError", e.getMessage()));
        }

        //文件是否存在
        if(Files.exists(userFile)){
            try {
                Files.delete(userFile);
                log.info(LangManager.msg("console.removeUser", who, uuid));
            } catch (IOException e) {
                throw new DataFileException(LangManager.msg("dataFile.removeUserDataError", e.getMessage()));
            }
        }

        //删除缓存
        users.remove(uuid);
        return true;
    }
}
