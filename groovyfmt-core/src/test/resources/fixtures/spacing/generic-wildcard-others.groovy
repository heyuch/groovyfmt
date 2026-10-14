class GenericWildcardOthers {

    def m(a, b, c) {
        def r = a ? b : c
        Class<?> x = null
        Map<String, ?> y = null
        Class<String> z = null
    }

}
