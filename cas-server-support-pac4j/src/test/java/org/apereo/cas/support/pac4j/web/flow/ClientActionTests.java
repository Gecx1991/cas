package org.apereo.cas.support.pac4j.web.flow;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationManager;
import org.apereo.cas.authentication.AuthenticationTransaction;
import org.apereo.cas.authentication.TestUtils;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.apereo.cas.CasProtocolConstants;
import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.authentication.AuthenticationResult;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.support.pac4j.test.MockFacebookClient;
import org.apereo.cas.ticket.ExpirationPolicy;
import org.apereo.cas.ticket.TicketGrantingTicketImpl;
import org.junit.Test;
import org.pac4j.core.client.Clients;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;
import org.pac4j.oauth.client.FacebookClient;
import org.pac4j.oauth.client.TwitterClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.theme.ThemeChangeInterceptor;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.core.collection.MutableAttributeMap;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.test.MockRequestContext;

import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * This class tests the {@link ClientAction} class.
 *
 * @author Jerome Leleu
 * @since 3.5.2
 */
@SuppressWarnings("rawtypes")
public class ClientActionTests {

    private static final String TGT_NAME = "ticketGrantingTicketId";
    private static final String TGT_ID = "TGT-00-xxxxxxxxxxxxxxxxxxxxxxxxxx.cas0";

    private static final String MY_KEY = "my_key";

    private static final String MY_SECRET = "my_secret";

    private static final String MY_LOGIN_URL = "http://casserver/login";

    private static final String MY_SERVICE = "http://myservice";

    private static final String MY_THEME = "my_theme";

    private static final String MY_LOCALE = "fr";

    private static final String MY_METHOD = "POST";

    @Test
    public void verifyStartAuthentication() throws Exception {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter(ThemeChangeInterceptor.DEFAULT_PARAM_NAME, MY_THEME);
        mockRequest.setParameter(LocaleChangeInterceptor.DEFAULT_PARAM_NAME, MY_LOCALE);
        mockRequest.setParameter(CasProtocolConstants.PARAMETER_METHOD, MY_METHOD);

        final MockHttpSession mockSession = new MockHttpSession();
        mockRequest.setSession(mockSession);

        final ServletExternalContext servletExternalContext = mock(ServletExternalContext.class);
        when(servletExternalContext.getNativeRequest()).thenReturn(mockRequest);

        final MockRequestContext mockRequestContext = new MockRequestContext();
        mockRequestContext.setExternalContext(servletExternalContext);
        mockRequestContext.getFlowScope().put(CasProtocolConstants.PARAMETER_SERVICE,
                org.apereo.cas.services.TestUtils.getService(MY_SERVICE));

        final FacebookClient facebookClient = new FacebookClient(MY_KEY, MY_SECRET);
        final TwitterClient twitterClient = new TwitterClient(MY_KEY, MY_SECRET);
        final Clients clients = new Clients(MY_LOGIN_URL, facebookClient, twitterClient);
        final ClientAction action = new ClientAction();
        action.setCentralAuthenticationService(mock(CentralAuthenticationService.class));
        action.setClients(clients);

        final Event event = action.execute(mockRequestContext);
        assertEquals("error", event.getId());
        assertEquals(MY_THEME, mockSession.getAttribute(ThemeChangeInterceptor.DEFAULT_PARAM_NAME));
        assertEquals(MY_LOCALE, mockSession.getAttribute(LocaleChangeInterceptor.DEFAULT_PARAM_NAME));
        assertEquals(MY_METHOD, mockSession.getAttribute(CasProtocolConstants.PARAMETER_METHOD));
        final MutableAttributeMap flowScope = mockRequestContext.getFlowScope();
        final Set<ClientAction.ProviderLoginPageConfiguration> urls =
                (Set<ClientAction.ProviderLoginPageConfiguration>) flowScope.get(ClientAction.PAC4J_URLS);
        assertFalse(urls.isEmpty());

        assertTrue(urls.stream()
                .filter(cfg -> cfg.getName().equalsIgnoreCase("facebook"))
                .findFirst()
                .get().getRedirectUrl()
                .startsWith("https://www.facebook.com/v2.2/dialog/oauth?client_id=my_key&redirect_uri=http%3A%2F%2Fcasserver%2Flogin%3F"
                        + Clients.DEFAULT_CLIENT_NAME_PARAMETER + "%3DFacebookClient&state="));

        assertEquals(urls.stream()
                .filter(cfg -> cfg.getName().equalsIgnoreCase("twitter"))
                .findFirst()
                .get().getRedirectUrl(), MY_LOGIN_URL + '?' + Clients.DEFAULT_CLIENT_NAME_PARAMETER
                + "=TwitterClient&needs_client_redirection=true");
    }

    @Test
    public void verifyFinishAuthentication() throws Exception {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter(Clients.DEFAULT_CLIENT_NAME_PARAMETER, "FacebookClient");

        final MockHttpSession mockSession = new MockHttpSession();
        mockSession.setAttribute(ThemeChangeInterceptor.DEFAULT_PARAM_NAME, MY_THEME);
        mockSession.setAttribute(LocaleChangeInterceptor.DEFAULT_PARAM_NAME, MY_LOCALE);
        mockSession.setAttribute(CasProtocolConstants.PARAMETER_METHOD, MY_METHOD);
        final Service service = TestUtils.getService(MY_SERVICE);
        mockSession.setAttribute(CasProtocolConstants.PARAMETER_SERVICE, service);
        mockRequest.setSession(mockSession);

        final ServletExternalContext servletExternalContext = mock(ServletExternalContext.class);
        when(servletExternalContext.getNativeRequest()).thenReturn(mockRequest);

        final MockRequestContext mockRequestContext = new MockRequestContext();
        mockRequestContext.setExternalContext(servletExternalContext);

        final FacebookClient facebookClient = new MockFacebookClient();
        final Clients clients = new Clients(MY_LOGIN_URL, facebookClient);

        final TicketGrantingTicket tgt = new TicketGrantingTicketImpl(TGT_ID, mock(Authentication.class), mock(ExpirationPolicy.class));
        final CentralAuthenticationService casImpl = mock(CentralAuthenticationService.class);
        when(casImpl.createTicketGrantingTicket(any(AuthenticationResult.class))).thenReturn(tgt);
        final ClientAction action = new ClientAction();

        final AuthenticationManager authNManager = mock(AuthenticationManager.class);
        when(authNManager.authenticate(any(AuthenticationTransaction.class))).thenReturn(TestUtils.getAuthentication());
        action.getAuthenticationSystemSupport().getAuthenticationTransactionManager()
                .setAuthenticationManager(authNManager);
        action.setCentralAuthenticationService(casImpl);


        action.setClients(clients);
        final Event event = action.execute(mockRequestContext);
        assertEquals("success", event.getId());
        assertEquals(MY_THEME, mockRequest.getAttribute(ThemeChangeInterceptor.DEFAULT_PARAM_NAME));
        assertEquals(MY_LOCALE, mockRequest.getAttribute(LocaleChangeInterceptor.DEFAULT_PARAM_NAME));
        assertEquals(MY_METHOD, mockRequest.getAttribute(CasProtocolConstants.PARAMETER_METHOD));
        assertEquals(MY_SERVICE, mockRequest.getAttribute(CasProtocolConstants.PARAMETER_SERVICE));
        final MutableAttributeMap flowScope = mockRequestContext.getFlowScope();
        final MutableAttributeMap requestScope = mockRequestContext.getRequestScope();
        assertEquals(service, flowScope.get(CasProtocolConstants.PARAMETER_SERVICE));
        assertEquals(TGT_ID, flowScope.get(TGT_NAME));
        assertEquals(TGT_ID, requestScope.get(TGT_NAME));
    }

    @Test
    public void checkUnautorizedProtocol() throws Exception {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter(Clients.DEFAULT_CLIENT_NAME_PARAMETER, "IndirectBasicAuthClient");

        final ServletExternalContext servletExternalContext = mock(ServletExternalContext.class);
        when(servletExternalContext.getNativeRequest()).thenReturn(mockRequest);

        final MockRequestContext mockRequestContext = new MockRequestContext();
        mockRequestContext.setExternalContext(servletExternalContext);

        final IndirectBasicAuthClient basicAuthClient = new IndirectBasicAuthClient();
        final Clients clients = new Clients(MY_LOGIN_URL, basicAuthClient);
        final ClientAction action = new ClientAction();
        action.setCentralAuthenticationService(mock(CentralAuthenticationService.class));
        action.setClients(clients);

        try {
            action.execute(mockRequestContext);
            fail("Should fail as the HTTP protocol is not authorized");
        } catch (final TechnicalException e) {
            assertEquals("Only CAS, OAuth, OpenID and SAML protocols are supported: " + basicAuthClient, e.getMessage());
        }
    }
}
