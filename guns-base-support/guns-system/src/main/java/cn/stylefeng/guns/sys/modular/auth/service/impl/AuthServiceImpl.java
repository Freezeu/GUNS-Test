package cn.stylefeng.guns.sys.modular.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.SecureUtil;
import cn.stylefeng.guns.core.consts.CommonConstant;
import cn.stylefeng.guns.core.context.constant.ConstantContextHolder;
import cn.stylefeng.guns.core.enums.CommonStatusEnum;
import cn.stylefeng.guns.core.exception.AuthException;
import cn.stylefeng.guns.core.exception.ServiceException;
import cn.stylefeng.guns.core.exception.enums.AuthExceptionEnum;
import cn.stylefeng.guns.core.exception.enums.ServerExceptionEnum;
import cn.stylefeng.guns.core.pojo.login.SysLoginUser;
import cn.stylefeng.guns.core.util.HttpServeletUtil;
import cn.stylefeng.guns.core.util.IpAddressUtil;
import cn.stylefeng.guns.sys.core.cache.UserCache;
import cn.stylefeng.guns.sys.core.enums.LogSuccessStatusEnum;
import cn.stylefeng.guns.sys.core.jwt.JwtPayLoad;
import cn.stylefeng.guns.sys.core.jwt.JwtTokenUtil;
import cn.stylefeng.guns.sys.core.log.LogManager;
import cn.stylefeng.guns.sys.modular.auth.factory.LoginUserFactory;
import cn.stylefeng.guns.sys.modular.auth.service.AuthService;
import cn.stylefeng.guns.sys.modular.user.entity.SysUser;
import cn.stylefeng.guns.sys.modular.user.service.SysUserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 认证相关service实现类
 *
 * @author xuyuxiang
 * @date 2020/3/11 16:58
 */
@Service
public class AuthServiceImpl implements AuthService, UserDetailsService {

    @Resource
    private SysUserService sysUserService;

    @Resource
    private UserCache userCache;

    /**
     * 账号密码登录
     *
     * @author xuyuxiang
     * @date 2020/3/11 16:59
     */
    @Override
    public String login(String account, String password) {

        if (ObjectUtil.hasEmpty(account, password)) {
            LogManager.me().executeLoginLog(account, LogSuccessStatusEnum.FAIL.getCode(), AuthExceptionEnum.ACCOUNT_PWD_EMPTY.getMessage());
            throw new AuthException(AuthExceptionEnum.ACCOUNT_PWD_EMPTY);
        }

        SysUser sysUser = sysUserService.getUserByCount(account);

        //用户不存在，账号或密码错误
        if (ObjectUtil.isEmpty(sysUser)) {
            LogManager.me().executeLoginLog(account, LogSuccessStatusEnum.FAIL.getCode(), AuthExceptionEnum.ACCOUNT_PWD_ERROR.getMessage());
            throw new AuthException(AuthExceptionEnum.ACCOUNT_PWD_ERROR);
        }

        String sysUserSalt = sysUser.getSalt();

        //盐值不能为空
        if (ObjectUtil.isEmpty(sysUserSalt)) {
            LogManager.me().executeLoginLog(sysUser.getAccount(), LogSuccessStatusEnum.FAIL.getCode(), AuthExceptionEnum.USER_SALT_EMPTY.getMessage());
            throw new AuthException(AuthExceptionEnum.USER_SALT_EMPTY);
        }

        String requestMd5 = SecureUtil.md5(password + sysUserSalt);

        String passwordMd5 = sysUser.getPassword();

        //验证账号密码是否正确
        if (ObjectUtil.isEmpty(passwordMd5) || !requestMd5.equals(passwordMd5)) {
            LogManager.me().executeLoginLog(sysUser.getAccount(), LogSuccessStatusEnum.FAIL.getCode(), AuthExceptionEnum.ACCOUNT_PWD_ERROR.getMessage());
            throw new AuthException(AuthExceptionEnum.ACCOUNT_PWD_ERROR);
        }

        return this.doLogin(sysUser);
    }

    /**
     * 根据请求头获取token，存在可能为空的情况
     *
     * @author xuyuxiang
     * @date 2020/3/13 11:49
     */
    @Override
    public String getTokenFromRequest(HttpServletRequest request) {
        String authToken = request.getHeader(CommonConstant.AUTHORIZATION);
        if (ObjectUtil.isEmpty(authToken)) {
            return null;
        } else {
            //token不是以Bearer打头，则响应回格式不正确
            if (!authToken.startsWith(CommonConstant.TOKEN_TYPE_BEARER)) {
                throw new AuthException(AuthExceptionEnum.NOT_VALID_TOKEN_TYPE);
            }
            try {
                authToken = authToken.substring(CommonConstant.TOKEN_TYPE_BEARER.length() + 1);
            } catch (StringIndexOutOfBoundsException e) {
                throw new AuthException(AuthExceptionEnum.NOT_VALID_TOKEN_TYPE);
            }
        }

        return authToken;
    }

    /**
     * 根据token获取当前登录用户信息
     *
     * @author xuyuxiang
     * @date 2020/3/13 12:00
     */
    @Override
    public SysLoginUser getLoginUserByToken(String token) {

        //校验token，错误则抛异常
        this.checkToken(token);

        //根据token获取JwtPayLoad部分
        JwtPayLoad jwtPayLoad = JwtTokenUtil.getJwtPayLoad(token);

        //从redis缓存中获取登录用户
        Object cacheObject = userCache.get(jwtPayLoad.getUuid());

        //用户不存在则表示登录已过期
        if (ObjectUtil.isEmpty(cacheObject)) {
            throw new AuthException(AuthExceptionEnum.LOGIN_EXPIRED);
        }

        //转换成登录用户
        SysLoginUser sysLoginUser = (SysLoginUser) cacheObject;

        //用户存在, 无痛刷新缓存，在登录过期前活动的用户自动刷新缓存时间
        this.cacheLoginUser(jwtPayLoad, sysLoginUser);

        //返回用户
        return sysLoginUser;
    }

    /**
     * 校验token，错误则抛异常
     *
     * @author xuyuxiang
     * @date 2020/3/19 19:15
     */
    @Override
    public void checkToken(String token) {
        //校验token是否正确
        Boolean tokenCorrect = JwtTokenUtil.checkToken(token);
        if (!tokenCorrect) {
            throw new AuthException(AuthExceptionEnum.REQUEST_TOKEN_ERROR);
        }

        //校验token是否失效
        Boolean tokenExpired = JwtTokenUtil.isTokenExpired(token);
        if (tokenExpired) {
            throw new AuthException(AuthExceptionEnum.LOGIN_EXPIRED);
        }
    }

    /**
     * 退出登录
     *
     * @author xuyuxiang, fengshuonan
     * @date 2020/3/16 15:05
     */
    @Override
    public void logout() {

        HttpServletRequest request = HttpServeletUtil.getRequest();

        if (ObjectUtil.isNotNull(request)) {

            //获取token
            String token = this.getTokenFromRequest(request);

            //如果token为空直接返回
            if (ObjectUtil.isEmpty(token)) {
                return;
            }

            //校验token，错误则抛异常，待确定
            this.checkToken(token);

            //根据token获取JwtPayLoad部分
            JwtPayLoad jwtPayLoad = JwtTokenUtil.getJwtPayLoad(token);

            //获取缓存的key
            String loginUserCacheKey = jwtPayLoad.getUuid();
            this.clearUser(loginUserCacheKey, jwtPayLoad.getAccount());

        } else {
            throw new ServiceException(ServerExceptionEnum.REQUEST_EMPTY);
        }
    }

    /**
     * 根据key清空登陆信息
     *
     * @author xuyuxiang
     * @date 2020/6/19 12:28
     */
    private void clearUser(String loginUserKey, String account) {
        //获取缓存的用户
        Object cacheObject = userCache.get(loginUserKey);

        //如果缓存的用户存在，清除会话，否则表示该会话信息已失效，不执行任何操作
        if (ObjectUtil.isNotEmpty(cacheObject)) {
            //清除登录会话
            userCache.remove(loginUserKey);
            //创建退出登录日志
            LogManager.me().executeExitLog(account);
        }
    }

    /**
     * 执行登录方法
     *
     * @author xuyuxiang
     * @date 2020/3/12 10:43
     */
    private String doLogin(SysUser sysUser) {

        Integer sysUserStatus = sysUser.getStatus();

        //验证账号是否被冻结
        if (CommonStatusEnum.DISABLE.getCode().equals(sysUserStatus)) {
            LogManager.me().executeLoginLog(sysUser.getAccount(), LogSuccessStatusEnum.FAIL.getCode(), AuthExceptionEnum.ACCOUNT_FREEZE_ERROR.getMessage());
            throw new AuthException(AuthExceptionEnum.ACCOUNT_FREEZE_ERROR);
        }

        //构造SysLoginUser
        SysLoginUser sysLoginUser = this.genSysLoginUser(sysUser);

        //构造jwtPayLoad
        JwtPayLoad jwtPayLoad = new JwtPayLoad(sysUser.getId(), sysUser.getAccount());

        //生成token
        String token = JwtTokenUtil.generateToken(jwtPayLoad);

        //缓存token与登录用户信息对应, 默认2个小时
        this.cacheLoginUser(jwtPayLoad, sysLoginUser);

        //设置最后登录ip和时间
        sysUser.setLastLoginIp(IpAddressUtil.getIp(HttpServeletUtil.getRequest()));
        sysUser.setLastLoginTime(DateTime.now());

        //更新用户登录信息
        sysUserService.updateById(sysUser);

        //登录成功，记录登录日志
        LogManager.me().executeLoginLog(sysUser.getAccount(), LogSuccessStatusEnum.SUCCESS.getCode(), null);

        //登录成功，设置SpringSecurityContext上下文，方便获取用户
        this.setSpringSecurityContextAuthentication(sysLoginUser);

        //如果开启限制单用户登陆，则踢掉原来的用户
        Boolean enableSingleLogin = ConstantContextHolder.getEnableSingleLogin();
        if (enableSingleLogin) {

            //获取所有的登陆用户
            Map<String, SysLoginUser> allLoginUsers = userCache.getAllKeyValues();
            for (Map.Entry<String, SysLoginUser> loginedUserEntry : allLoginUsers.entrySet()) {

                String loginedUserKey = loginedUserEntry.getKey();
                SysLoginUser loginedUser = loginedUserEntry.getValue();

                //如果账号名称相同，并且redis缓存key和刚刚生成的用户的uuid不一样，则清除以前登录的
                if (loginedUser.getName().equals(sysUser.getName())
                        && !loginedUserKey.equals(jwtPayLoad.getUuid())) {
                    this.clearUser(loginedUserKey, loginedUser.getAccount());
                }
            }
        }

        //返回token
        return token;
    }

    /**
     * 设置SpringSecurityContext上下文，方便获取用户
     *
     * @author xuyuxiang
     * @date 2020/3/19 19:58
     */
    @Override
    public void setSpringSecurityContextAuthentication(SysLoginUser sysLoginUser) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                new UsernamePasswordAuthenticationToken(
                        sysLoginUser,
                        null,
                        sysLoginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
    }

    /**
     * 获取SpringSecurityContext中认证信息
     *
     * @author xuyuxiang
     * @date 2020/3/19 20:04
     */
    @Override
    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 构造登录用户信息
     *
     * @author xuyuxiang
     * @date 2020/3/12 17:32
     */
    private SysLoginUser genSysLoginUser(SysUser sysUser) {
        SysLoginUser sysLoginUser = new SysLoginUser();
        BeanUtil.copyProperties(sysUser, sysLoginUser);
        LoginUserFactory.fillLoginUserInfo(sysLoginUser);
        return sysLoginUser;
    }

    /**
     * 缓存token与登录用户信息对应, 默认2个小时
     *
     * @author xuyuxiang
     * @date 2020/3/13 14:51
     */
    private void cacheLoginUser(JwtPayLoad jwtPayLoad, SysLoginUser sysLoginUser) {
        String redisLoginUserKey = jwtPayLoad.getUuid();
        userCache.put(redisLoginUserKey, sysLoginUser);
    }

    /**
     * 根据用户名获取用户
     *
     * @author xuyuxiang
     * @date 2020/4/2 16:04
     */
    @Override
    public SysLoginUser loadUserByUsername(String account) throws UsernameNotFoundException {
        SysLoginUser sysLoginUser = new SysLoginUser();
        SysUser user = sysUserService.getUserByCount(account);
        BeanUtil.copyProperties(user, sysLoginUser);
        return sysLoginUser;
    }
}