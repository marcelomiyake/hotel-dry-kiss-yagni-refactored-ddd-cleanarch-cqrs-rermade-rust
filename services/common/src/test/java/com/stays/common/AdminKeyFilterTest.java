package com.stays.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminKeyFilterTest {
    @Test
    void allowsPublicPathsWithoutAKey() throws Exception {
        AdminKeyFilter filter = new AdminKeyFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hotels");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void requiresAKeyForStaffPaths() throws Exception {
        AdminKeyFilter filter = new AdminKeyFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/hotels");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsStaffPathWhenKeyMatches() throws Exception {
        AdminKeyFilter filter = new AdminKeyFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/hotels");
        request.addHeader("X-Admin-Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void deniesAdminWhenNoServerKeyIsConfigured() throws Exception {
        AdminKeyFilter filter = new AdminKeyFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/hotels");
        request.addHeader("X-Admin-Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
