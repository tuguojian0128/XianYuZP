package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformConversationProfileServiceTest {

    @Test
    void stopsBatchAfterFirstPlatformFailureAndCachesPlaceholders() {
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        AccountService accountService = mock(AccountService.class);
        XianyuApiCallUtils apiCallUtils = mock(XianyuApiCallUtils.class);
        XianyuAccount account = new XianyuAccount();
        account.setId(1L);
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(accountService.getCookieByAccountId(1L)).thenReturn("cookie");
        when(apiCallUtils.callApiWithRetry(
                eq(1L), eq("mtop.taobao.idlemessage.pc.user.query"), eq("4.0"),
                anyMap(), eq("cookie"), isNull(), anyMap()))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(false, null, "闲鱼太累了休息一会吧", false));

        PlatformConversationProfileService service = new PlatformConversationProfileService(
                accountMapper, accountService, apiCallUtils, new ObjectMapper());

        List<Map<String, Object>> first = service.query(1L, List.of("a@goofish", "b@goofish", "c@goofish"));
        List<Map<String, Object>> second = service.query(1L, List.of("a@goofish", "b@goofish", "c@goofish"));

        assertEquals(3, first.size());
        assertEquals(3, second.size());
        verify(apiCallUtils, times(1)).callApiWithRetry(
                eq(1L), eq("mtop.taobao.idlemessage.pc.user.query"), eq("4.0"),
                anyMap(), eq("cookie"), isNull(), anyMap());
    }
}
