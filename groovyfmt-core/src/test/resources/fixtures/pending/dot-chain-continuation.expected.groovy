class DotChain {

    def build(timeout, chromeUserDataDir) {
        Options options = Options.builder()
                .headless(true)
                .readTimeout(timeout)
                .arguments(['--no-sandbox'])
                .userDataDir(chromeUserDataDir.toPath())
                .build()
        return options
    }

}
