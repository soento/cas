package org.apereo.cas.support.saml.authentication.principal;

import org.apereo.cas.authentication.TestUtils;
import org.apereo.cas.authentication.principal.DefaultResponse;
import org.apereo.cas.authentication.principal.Response;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.config.CasCoreAuthenticationConfiguration;
import org.apereo.cas.config.CasCoreConfiguration;
import org.apereo.cas.config.CasCoreServicesConfiguration;
import org.apereo.cas.config.CasCoreTicketsConfiguration;
import org.apereo.cas.config.CasCoreUtilConfiguration;
import org.apereo.cas.config.CasCoreWebConfiguration;
import org.apereo.cas.config.CasPersonDirectoryAttributeRepositoryConfiguration;
import org.apereo.cas.config.CoreSamlConfiguration;
import org.apereo.cas.logout.config.CasCoreLogoutConfiguration;
import org.apereo.cas.services.DefaultRegisteredServiceUsernameProvider;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.AbstractOpenSamlTests;
import org.apereo.cas.support.saml.SamlProtocolConstants;
import org.apereo.cas.support.saml.config.SamlGoogleAppsConfiguration;
import org.apereo.cas.util.ApplicationContextProvider;
import org.apereo.cas.util.CompressionUtils;
import org.apereo.cas.validation.config.CasCoreValidationConfiguration;
import org.apereo.cas.web.config.CasCookieConfiguration;
import org.apereo.cas.web.config.CasProtocolViewsConfiguration;
import org.apereo.cas.web.config.CasValidationConfiguration;
import org.apereo.cas.web.flow.config.CasCoreWebflowConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Scott Battaglia
 * @since 3.1
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SamlGoogleAppsConfiguration.class, CasCoreAuthenticationConfiguration.class,
        CasCoreServicesConfiguration.class,
        CoreSamlConfiguration.class,
        CasCoreWebConfiguration.class,
        CasCoreWebflowConfiguration.class,
        RefreshAutoConfiguration.class,
        AopAutoConfiguration.class,
        CasCookieConfiguration.class,
        CasCoreAuthenticationConfiguration.class,
        CasCoreTicketsConfiguration.class,
        CasCoreLogoutConfiguration.class,
        CasValidationConfiguration.class,
        CasProtocolViewsConfiguration.class,
        CasCoreValidationConfiguration.class,
        CasCoreConfiguration.class,
        CasPersonDirectoryAttributeRepositoryConfiguration.class,
        CasCoreUtilConfiguration.class})
@TestPropertySource(locations = "classpath:/gapps.properties")
public class GoogleAccountsServiceTests extends AbstractOpenSamlTests {

    @Autowired
    @Qualifier("googleAccountsServiceFactory")
    private ServiceFactory factory;

    private GoogleAccountsService googleAccountsService;

    @Autowired
    private ApplicationContextProvider applicationContextProvider;

    @Before
    public void init() {
        this.applicationContextProvider.setApplicationContext(this.applicationContext);
    }

    public GoogleAccountsService getGoogleAccountsService() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        final String samlRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "ID=\"5545454455\" Version=\"2.0\" IssueInstant=\"Value\" "
                + "ProtocolBinding=\"urn:oasis:names.tc:SAML:2.0:bindings:HTTP-Redirect\" "
                + "ProviderName=\"https://localhost:8443/myRutgers\" AssertionConsumerServiceURL=\"https://localhost:8443/myRutgers\"/>";
        request.setParameter(SamlProtocolConstants.PARAMETER_SAML_REQUEST, encodeMessage(samlRequest));
        request.setParameter(SamlProtocolConstants.PARAMETER_SAML_RELAY_STATE, "RelayStateAddedHere");

        final RegisteredService regSvc = mock(RegisteredService.class);
        when(regSvc.getUsernameAttributeProvider()).thenReturn(new DefaultRegisteredServiceUsernameProvider());

        final ServicesManager servicesManager = mock(ServicesManager.class);
        when(servicesManager.findServiceBy(any(Service.class))).thenReturn(regSvc);

        return (GoogleAccountsService) factory.createService(request);
    }

    @Before
    public void setUp() throws Exception {
        this.googleAccountsService = getGoogleAccountsService();
        this.googleAccountsService.setPrincipal(TestUtils.getPrincipal());
    }

    @Test
    public void verifyResponse() {
        final Response resp = this.googleAccountsService.getResponse("ticketId");
        assertEquals(resp.getResponseType(), DefaultResponse.ResponseType.POST);
        final String response = resp.getAttributes().get(SamlProtocolConstants.PARAMETER_SAML_RESPONSE);
        assertNotNull(response);
        assertTrue(response.contains("NotOnOrAfter"));

        final Pattern pattern = Pattern.compile("NotOnOrAfter\\s*=\\s*\"(.+Z)\"");
        final Matcher matcher = pattern.matcher(response);
        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        while (matcher.find()) {
            final String onOrAfter = matcher.group(1);
            final ZonedDateTime dt = ZonedDateTime.parse(onOrAfter);
            assertTrue(dt.isAfter(now));
        }
        assertTrue(resp.getAttributes().containsKey(SamlProtocolConstants.PARAMETER_SAML_RELAY_STATE));

    }

    private static String encodeMessage(final String xmlString) throws IOException {
        return CompressionUtils.deflate(xmlString);
    }
}
