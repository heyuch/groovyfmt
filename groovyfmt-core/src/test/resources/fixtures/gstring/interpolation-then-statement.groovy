class InterpolationThenStatement {

    private static void check(String code, Object target) {
        P.notNull(target, "can't find config of the ${code}")
        checkMore(code, target.value)
    }

    private static void checkMore(String code, Object v) {
        P.notNull(v, "code: ${code} is not usable")
        P.isTrue(v != null, "outputCode: ${code} could not be used")
    }

}
