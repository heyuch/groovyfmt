class WrappedStatement {

    private static final String PREFIX = "a" +
            "b"

    void m(a, b, c, flag) {
        def s1 = a +
                b +
                c
        def s2 = "x" +
                a
        def n1 = a *
                b
        def n2 = flag
                ? a
                : b
        if (flag
                && a) {
            println a
        }
        return a +
                b
    }

}
