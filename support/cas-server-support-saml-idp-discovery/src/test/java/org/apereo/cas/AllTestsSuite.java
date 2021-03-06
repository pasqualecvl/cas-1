package org.apereo.cas;

import org.apereo.cas.entity.SamlIdentityProviderEntityParserTests;
import org.apereo.cas.web.SamlIdentityProviderDiscoveryFeedControllerTests;
import org.apereo.cas.web.flow.SamlIdentityProviderDiscoveryWebflowConfigurerTests;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * This is {@link AllTestsSuite}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@SelectClasses({
    SamlIdentityProviderEntityParserTests.class,
    SamlIdentityProviderDiscoveryFeedControllerTests.class,
    SamlIdentityProviderDiscoveryWebflowConfigurerTests.class
})
@Suite
public class AllTestsSuite {
}
