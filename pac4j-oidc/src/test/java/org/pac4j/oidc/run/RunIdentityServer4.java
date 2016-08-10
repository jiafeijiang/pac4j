package org.pac4j.oidc.run;

import com.esotericsoftware.kryo.Kryo;
import com.nimbusds.oauth2.sdk.token.AccessTokenType;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.run.RunClient;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.kryo.AccessTokenTypeSerializer;
import org.pac4j.oidc.profile.DefaultIdTokenProfile;
import org.pac4j.oidc.profile.OidcProfile;

import static org.junit.Assert.*;

/**
 * Run a manual test for the IdentityServer4 (https://github.com/IdentityServer/IdentityServer4/src/Host)
 * with the following configuration:
 *
 * new Client
 * {
 *     ClientId = "test",
 *     ClientSecrets = new List<Secret>
 *     {
 *         new Secret("secret".Sha256())
 *     },
 *     RedirectUris = new List<string>
 *     {
 *         "http://www.pac4j.org/"
 *     },
 *     AllowedGrantTypes = GrantTypes.ImplicitAndClientCredentials,
 *     AllowedScopes = new List<string>
 *     {
 *         "openid", "profile", "email"
 *     }
 * },
 *
 * @author Jerome Leleu
 * @since 1.9.2
 */
public class RunIdentityServer4 extends RunClient {

    private enum Flow { IMPLICIT_FLOW, IMPLICIT_FLOW_CLIENT_SIDE, AUTHORIZATION_CODE, HYBRID_FLOW };

    private final static Flow flow = Flow.HYBRID_FLOW;

    public static void main(final String[] args) throws Exception {
        new RunIdentityServer4().run();
    }

    @Override
    protected String getLogin() {
        return "alice";
    }

    @Override
    protected String getPassword() {
        return "alice";
    }

    @Override
    protected IndirectClient getClient() {
        final OidcConfiguration configuration = new OidcConfiguration();
        configuration.setClientId("test");
        configuration.setSecret("secret");
        configuration.setDiscoveryURI("http://localhost:1941/.well-known/openid-configuration");
        if (flow == Flow.IMPLICIT_FLOW) {
            // AllowedGrantTypes = GrantTypes.ImplicitAndClientCredentials,
            configuration.setResponseType("id_token");
            configuration.setResponseMode("form_post");
            configuration.setUseNonce(true);
            logger.warn("For the implicit flow, copy / paste the form body parameters after a ? as the returned url");
        } else if (flow == Flow.IMPLICIT_FLOW_CLIENT_SIDE) {
            // AllowedGrantTypes = GrantTypes.ImplicitAndClientCredentials,
            configuration.setResponseType("id_token");
            configuration.setUseNonce(true);
        /*} else if (flow == Flow.AUTHORIZATION_CODE) {
            AllowedGrantTypes = GrantTypes.CodeAndClientCredentials,*/
        } else if (flow == Flow.HYBRID_FLOW) {
            // AllowAccessTokensViaBrowser = true, AllowedGrantTypes = GrantTypes.HybridAndClientCredentials,
            configuration.setResponseType("code id_token token");
            configuration.setUseNonce(true);
        } else if (flow != Flow.AUTHORIZATION_CODE) {
            throw new TechnicalException("Unsupported flow for tests");
        }
        final OidcClient client = new OidcClient(configuration);
        client.setCallbackUrl(PAC4J_BASE_URL);
        return client;
    }

    @Override
    protected void registerForKryo(final Kryo kryo) {
        kryo.register(OidcProfile.class);
        kryo.register(AccessTokenType.class, new AccessTokenTypeSerializer());
    }

    @Override
    protected void verifyProfile(final CommonProfile userProfile) {
        final OidcProfile profile = (OidcProfile) userProfile;
        assertEquals("818727", profile.getId());
        final DefaultIdTokenProfile idTokenProfile = (DefaultIdTokenProfile) profile.getIdTokenProfile().get();
        assertEquals("test", idTokenProfile.getAudience().get(0));
        assertNotNull(idTokenProfile.getNbf());
        assertEquals("idsvr", idTokenProfile.getAttribute("idp"));
        assertNotNull(idTokenProfile.getAuthTime());
        assertEquals("http://localhost:1941", idTokenProfile.getIssuer());
        assertEquals("Alice Smith", idTokenProfile.getDisplayName());
        assertNotNull(idTokenProfile.getExpirationDate());
        assertNotNull(idTokenProfile.getIssuedAt());
        assertEquals("50665cc0cf78943f5a5a0f7357175cb3", idTokenProfile.getAttribute("sid"));
        if (flow  == Flow.IMPLICIT_FLOW || flow == Flow.IMPLICIT_FLOW_CLIENT_SIDE) {
            assertEquals(1, profile.getAttributes().size());
            assertEquals(10, idTokenProfile.getAttributes().size());
        } else if (flow == Flow.AUTHORIZATION_CODE) {
            assertNotNull(profile.getAccessToken());
            assertEquals(3, profile.getAttributes().size());
            assertEquals(9, idTokenProfile.getAttributes().size());
        } else if (flow == Flow.HYBRID_FLOW) {
            assertNotNull(profile.getAccessToken());
            assertEquals(3, profile.getAttributes().size());
            assertEquals(10, idTokenProfile.getAttributes().size());
        }
    }
}
