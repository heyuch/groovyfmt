class AsCastThenStatement {

    void m(tenantCode) {
        Set<String> allCacheKeySet = contentPOList.collect {
            getContentCacheKey(getTenantCode(), it.getContentCode())
        } as Set<String>
        Set<String> versionKeys = contentPOList.collect {
            getContentVersionCacheKey(getTenantCode(), it.getContentCode())
        } as Set<String>
        getContentCache().removeAll(allCacheKeySet)
        getContentVersionCache().removeAll(versionKeys)
    }

}
