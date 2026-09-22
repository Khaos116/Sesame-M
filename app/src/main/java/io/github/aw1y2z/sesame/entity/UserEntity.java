package io.github.aw1y2z.sesame.entity;

import lombok.Data;
import lombok.Getter;
import io.github.aw1y2z.sesame.util.StringUtil;

@Getter
public class UserEntity {

    private final String userId;

    private final String account;

    private final Integer friendStatus;

    private final String realName;

    private final String nickName;

    private final String remarkName;

    private final String showName;

    private final String maskName;

    private final String fullName;

    public UserEntity(String userId, String account, Integer friendStatus, String realName, String nickName, String remarkName) {
        this.userId = userId;
        this.account = account;
        this.friendStatus = friendStatus;
        this.realName = realName;
        this.nickName = nickName;
        this.remarkName = remarkName;
        String showNameTmp;
        if (!StringUtil.isEmpty(remarkName)) {
            showNameTmp = remarkName;
        } else if (!StringUtil.isEmpty(nickName)) {
            showNameTmp = nickName;
        } else {
            // 昵称、备注都没有时兜底用真实姓名（可能为 null）
            showNameTmp = realName;
        }
        String maskNameTmp;
        if (realName != null && realName.length() > 1) {
            maskNameTmp = "*" + realName.substring(1);
        } else {
            maskNameTmp = realName;
        }
        /*if (isMaskAccount) {
            int length = account.length();
            if (length > 5) {
                int prefixIndex = Math.min(3, Math.max(1, length - 3));
                String prefix = account.substring(0, prefixIndex);
                int tmpIndex = prefixIndex + 3;
                int suffixIndex = length - 4;
                if (suffixIndex < tmpIndex) {
                    suffixIndex = tmpIndex;
                }
                String suffix = account.substring(suffixIndex);
                account = prefix + "***" + suffix;
            }
        }*/
        this.showName = showNameTmp;
        // 空安全拼接：任一项为 null 时用空串代替，避免持久化出 "null|null" 这类脏数据
        String safeShow = showNameTmp == null ? "" : showNameTmp;
        String safeMask = maskNameTmp == null ? "" : maskNameTmp;
        this.maskName = safeShow + "|" + safeMask;
        this.fullName = safeShow + "|" + (realName == null ? "" : realName) + "(" + (account == null ? "" : account) + ")";
    }

    public String getShowName() {
        return showName;
    }

    public String getAccount() {
        return account;
    }

    @Data
    public static class UserDto {

        private String userId;

        private String account;

        private Integer friendStatus;

        private String realName;

        private String nickName;

        private String remarkName;

        private String showName;

        public UserEntity toEntity() {
            return new UserEntity(userId, account, friendStatus, realName, nickName, remarkName);
        }

    }
}
