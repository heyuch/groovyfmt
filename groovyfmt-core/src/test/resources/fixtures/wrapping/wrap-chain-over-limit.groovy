class WrapChainOverLimit {

    void m() {
        def result = optionsBuilder.withHeadlessModeEnabled().withReadTimeoutInMilliseconds(readTimeout).withChromeUserDataDir(userDataDir).build()
    }

}
