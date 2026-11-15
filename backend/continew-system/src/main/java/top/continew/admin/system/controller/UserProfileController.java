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

package top.continew.admin.system.controller;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.util.SecureUtils;
import top.continew.admin.system.model.req.user.UserBasicInfoUpdateReq;
import top.continew.admin.system.model.req.user.UserPasswordUpdateReq;
import top.continew.admin.system.model.resp.AvatarResp;
import top.continew.admin.system.service.UserService;
import top.continew.starter.core.util.validation.ValidationUtils;

import java.io.IOException;

/**
 * 个人信息 API
 * <p>
 * 本平台仅开放账号密码登录，安全字段（手机号、邮箱）与三方账号绑定均不对外开放，
 * 因此这里只保留头像、基础信息与密码三项自助维护能力。
 * </p>
 *
 * @author Charles7c
 * @since 2023/1/2 11:41
 */
@Tag(name = "个人信息 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/profile")
public class UserProfileController {

    private static final String DECRYPT_FAILED = "当前密码解密失败";
    private final UserService userService;

    /**
     * 修改头像
     *
     * @param avatarFile 头像文件
     * @return 新头像 URL
     */
    @Operation(summary = "修改头像", description = "用户修改个人头像")
    @PatchMapping("/avatar")
    public AvatarResp updateAvatar(@NotNull(message = "头像不能为空") MultipartFile avatarFile)
        throws IOException {
        ValidationUtils.throwIf(avatarFile::isEmpty, "头像不能为空");
        String newAvatar = userService.updateAvatar(avatarFile, UserContextHolder.getUserId());
        return AvatarResp.builder().avatar(newAvatar).build();
    }

    /**
     * 修改基础信息
     *
     * @param req 基础信息
     */
    @Operation(summary = "修改基础信息", description = "修改用户基础信息")
    @PatchMapping("/basic/info")
    public void updateBasicInfo(@RequestBody @Valid UserBasicInfoUpdateReq req) {
        userService.updateBasicInfo(req, UserContextHolder.getUserId());
    }

    /**
     * 修改密码
     *
     * @param updateReq 当前密码和新密码
     */
    @Operation(summary = "修改密码", description = "修改用户登录密码")
    @PatchMapping("/password")
    public void updatePassword(@RequestBody @Valid UserPasswordUpdateReq updateReq) {
        String oldPassword = StrUtil.isNotBlank(updateReq.getOldPassword())
            ? SecureUtils.decryptPasswordByRsaPrivateKey(updateReq.getOldPassword(), DECRYPT_FAILED)
            : null;
        String newPassword =
            SecureUtils.decryptPasswordByRsaPrivateKey(updateReq.getNewPassword(), "新密码解密失败");
        userService.updatePassword(oldPassword, newPassword, UserContextHolder.getUserId());
    }
}
