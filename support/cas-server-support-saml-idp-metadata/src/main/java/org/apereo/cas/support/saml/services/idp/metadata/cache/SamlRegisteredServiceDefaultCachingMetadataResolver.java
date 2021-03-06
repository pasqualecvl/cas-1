package org.apereo.cas.support.saml.services.idp.metadata.cache;

import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.SamlException;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.util.function.FunctionUtils;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import org.opensaml.core.criterion.SatisfyAnyCriterion;
import org.opensaml.saml.metadata.resolver.MetadataResolver;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * An adaptation of metadata resolver which handles the resolution of metadata resources
 * inside a cache. It basically is a fancy wrapper around a cache, and constructs the cache
 * semantics before processing the resolution of metadata for a SAML service.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
public class SamlRegisteredServiceDefaultCachingMetadataResolver implements SamlRegisteredServiceCachingMetadataResolver {

    private static final int MAX_CACHE_SIZE = 10_000;

    private final SamlRegisteredServiceMetadataResolverCacheLoader chainingMetadataResolverCacheLoader;

    private final LoadingCache<SamlRegisteredServiceCacheKey, MetadataResolver> cache;

    @Getter
    private final OpenSamlConfigBean openSamlConfigBean;

    public SamlRegisteredServiceDefaultCachingMetadataResolver(final Duration metadataCacheExpiration,
                                                               final SamlRegisteredServiceMetadataResolverCacheLoader loader,
                                                               final OpenSamlConfigBean openSamlConfigBean) {
        this.openSamlConfigBean = openSamlConfigBean;
        this.chainingMetadataResolverCacheLoader = loader;
        this.cache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .recordStats()
            .expireAfter(new SamlRegisteredServiceMetadataExpirationPolicy(metadataCacheExpiration))
            .build(this.chainingMetadataResolverCacheLoader);
    }

    @Override
    @Synchronized
    public MetadataResolver resolve(final SamlRegisteredService service, final CriteriaSet criteriaSet) {
        LOGGER.debug("Resolving metadata for [{}] at [{}]", service.getName(), service.getMetadataLocation());
        val cacheKey = new SamlRegisteredServiceCacheKey(service, criteriaSet);
        LOGGER.trace("Locating cached metadata resolver using key [{}] for service [{}]", cacheKey.getId(), service.getName());
        return FunctionUtils.doAndRetry(retryContext -> {
            val resolver = locateAndCacheMetadataResolver(service, cacheKey);
            if (!isMetadataResolverAcceptable(resolver, criteriaSet)) {
                invalidate(service, criteriaSet);
                LOGGER.warn("SAML metadata resolver [{}] obtained from the cache is "
                        + "unable to produce/resolve valid metadata for [{}]. Metadata resolver cache entry with key [{}] "
                        + "has been invalidated. Retry attempt: [{}]",
                    resolver.getId(), service.getMetadataLocation(), cacheKey.getId(), retryContext.getRetryCount());
                throw new SamlException("Unable to locate a valid SAML metadata resolver for "
                    + service.getMetadataLocation() + " to locate " + criteriaSet);
            }
            return resolver;
        });
    }

    @Override
    public void invalidate() {
        LOGGER.trace("Invalidating cache, removing all metadata resolvers");
        this.cache.invalidateAll();
    }

    @Override
    public void invalidate(final SamlRegisteredService service, final CriteriaSet criteriaSet) {
        LOGGER.trace("Invalidating cache for [{}].", service.getName());
        val k = new SamlRegisteredServiceCacheKey(service, criteriaSet);
        this.cache.invalidate(k);
    }

    /**
     * Is metadata resolver resolvable.
     *
     * @param metadataResolver the metadata resolver
     * @param criteriaSet      the criteria set
     * @return true/false
     */
    @SneakyThrows
    protected boolean isMetadataResolverAcceptable(final MetadataResolver metadataResolver,
                                                   final CriteriaSet criteriaSet) {
        if (criteriaSet.contains(SatisfyAnyCriterion.class)) {
            return true;
        }
        val md = metadataResolver.resolveSingle(criteriaSet);
        return md != null && md.isValid();
    }

    /**
     * Locate and cache metadata resolver.
     *
     * @param service  the service
     * @param cacheKey the cache key
     * @return the metadata resolver
     */
    protected MetadataResolver locateAndCacheMetadataResolver(final SamlRegisteredService service,
                                                              final SamlRegisteredServiceCacheKey cacheKey) {
        LOGGER.debug("Loading metadata resolver from the cache using [{}]", cacheKey.getCacheKey());
        val resolver = Objects.requireNonNull(cache.get(cacheKey));
        LOGGER.debug("Loaded and cached SAML metadata [{}] from [{}]",
            resolver.getId(), service.getMetadataLocation());
        return resolver;
    }

    /**
     * Resolve if present.
     *
     * @param service     the service
     * @param criteriaSet the criteria set
     * @return the resolver.
     */
    Optional<MetadataResolver> resolveIfPresent(final SamlRegisteredService service, final CriteriaSet criteriaSet) {
        val cacheKey = new SamlRegisteredServiceCacheKey(service, criteriaSet);
        return Optional.ofNullable(this.cache.getIfPresent(cacheKey));
    }

    /**
     * Gets statistics.
     *
     * @return the statistics
     */
    CacheStats getCacheStatistics() {
        return this.cache.stats();
    }
}
