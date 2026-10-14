class WrapAssignmentOverNestedChain {

    void m() {
        AbstractReleaseVersionProvider releaseVerProvider =
                ReleaseVersionUtils.getReleaseVerProvider(RvRefTypeEnum.OUTPUT_HF_PATTERN.code)
    }

}
