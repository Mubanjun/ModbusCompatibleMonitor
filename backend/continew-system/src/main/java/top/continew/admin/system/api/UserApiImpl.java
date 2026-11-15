/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.system.api;

import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.api.system.UserApi;
import top.continew.admin.common.constant.CacheConstants;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.req.user.UserPasswordResetReq;
import top.continew.admin.system.service.UserService;

/**
 * 用户业务 API 实现
 *
 * @author Charles7c
 * @since 2025/7/23 20:57
 */
@Service
@RequiredArgsConstructor
public class UserApiImpl implements UserApi {

    private final UserService baseService;
    private final UserMapper baseMapper;

    // 审计缺陷 36：运行时写入统一用 create_user = 0（系统用户，库里没有这条记录），而 JetCache 默认
    // 不缓存 null → 每个列表行都会真去查一次库（实测 1000 行列表 = 1020 次 SELECT）。
    // 打开 cacheNullValue 后，查不到的 id（例如 0）也会被缓存，列表接口的查询次数直接降到 0。
    @Override
    @Cached(key = "#id", name = CacheConstants.USER_KEY_PREFIX, cacheType = CacheType.BOTH,
        syncLocal = true, cacheNullValue = true)
    public String getNicknameById(Long id) {
        return baseMapper.selectNicknameById(id);
    }

    @Override
    public void resetPassword(String newPassword, Long id) {
        UserPasswordResetReq req = new UserPasswordResetReq();
        req.setNewPassword(newPassword);
        baseService.resetPassword(req, id);
    }
}
